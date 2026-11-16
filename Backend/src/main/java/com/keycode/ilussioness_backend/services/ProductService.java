package com.keycode.ilussioness_backend.services;

import com.keycode.ilussioness_backend.audit.domain.AuditEntityType;
import com.keycode.ilussioness_backend.audit.domain.AuditEventType;
import com.keycode.ilussioness_backend.audit.domain.AuditOutcome;
import com.keycode.ilussioness_backend.audit.service.AuditService;
import com.keycode.ilussioness_backend.models.Product;
import com.keycode.ilussioness_backend.models.ProductStatus;
import com.keycode.ilussioness_backend.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Inventory management over the {@code product} table.
 */
@Service
public class ProductService {

    private static final Logger LOG = LoggerFactory.getLogger(ProductService.class);
    private static final int NAME_MAX_LENGTH = 120;
    private static final int MAX_STOCK = 1_000_000;

    private final ProductRepository productRepository;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepository, AuditService auditService) {
        this.productRepository = productRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductById(UUID id) {
        return productRepository.findById(id);
    }

    @Transactional
    public Product createProduct(Product product) {
        if (product != null) {
            product.setProductId(null);
            if (product.getStock() == null) {
                product.setStock(0);
            }
            if (product.getStatus() == null) {
                product.setStatus(product.getStock() > 0 ? ProductStatus.AVAILABLE : ProductStatus.OUT_OF_STOCK);
            }
        }
        validate(product, null);
        Product saved = productRepository.saveAndFlush(product);
        auditService.recordBusinessSuccess(
                AuditEventType.PRODUCT_CREATED, AuditEntityType.PRODUCT, saved.getProductId().toString(), Map.of());
        return saved;
    }

    /**
     * Applies the non-null fields of {@code updates}.
     *
     * @return the updated product, or empty when it does not exist.
     */
    @Transactional
    public Optional<Product> updateProduct(UUID id, Product updates) {
        Optional<Product> existing = productRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Product product = existing.get();
        if (updates != null) {
            if (updates.getName() != null) {
                product.setName(updates.getName());
            }
            if (updates.getType() != null) {
                product.setType(updates.getType());
            }
            if (updates.getPrice() != null) {
                product.setPrice(updates.getPrice());
            }
            if (updates.getPhoto() != null) {
                product.setPhoto(updates.getPhoto().isBlank() ? null : updates.getPhoto());
            }
            if (updates.getMaterials() != null) {
                product.setMaterials(updates.getMaterials().isBlank() ? null : updates.getMaterials());
            }
            if (updates.getStock() != null) {
                product.setStock(updates.getStock());
            }
            if (updates.getStatus() != null) {
                product.setStatus(updates.getStatus());
            }
        }
        validate(product, id);
        Product saved = productRepository.saveAndFlush(product);
        auditService.recordBusinessSuccess(
                AuditEventType.PRODUCT_UPDATED, AuditEntityType.PRODUCT, id.toString(), Map.of());
        return Optional.of(saved);
    }

    /**
     * Adds (positive) or removes (negative) units from the stock.
     *
     * @return the updated product, or empty when it does not exist.
     */
    @Transactional
    public Optional<Product> adjustStock(UUID id, int delta) {
        Optional<Product> existing = productRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Product product = existing.get();
        try {
            product.adjustStock(delta);
        } catch (IllegalStateException ex) {
            reject(id, "insufficient_stock");
        }
        validate(product, id);
        Product saved = productRepository.saveAndFlush(product);
        auditService.recordBusinessSuccess(
                AuditEventType.PRODUCT_UPDATED, AuditEntityType.PRODUCT, id.toString(), Map.of());
        return Optional.of(saved);
    }

    @Transactional
    public boolean deleteProduct(UUID id) {
        if (!productRepository.existsById(id)) {
            return false;
        }
        productRepository.deleteById(id);
        productRepository.flush();
        auditService.recordBusinessSuccess(
                AuditEventType.PRODUCT_DELETED, AuditEntityType.PRODUCT, id.toString(), Map.of());
        return true;
    }

    private void validate(Product product, UUID id) {
        if (product == null) {
            reject(id, "payload_required");
        }
        if (product.getName() == null || product.getName().isBlank()) {
            reject(id, "name_required");
        }
        product.setName(product.getName().trim());
        if (product.getName().length() > NAME_MAX_LENGTH) {
            reject(id, "name_too_long");
        }
        if (product.getType() == null) {
            reject(id, "type_required");
        }
        BigDecimal price = product.getPrice();
        if (price == null) {
            reject(id, "price_required");
        }
        if (price.signum() < 0) {
            reject(id, "negative_price");
        }
        Integer stock = product.getStock();
        if (stock == null || stock < 0) {
            reject(id, "negative_stock");
        }
        if (stock > MAX_STOCK) {
            reject(id, "stock_too_large");
        }
    }

    private void reject(UUID id, String code) {
        try {
            auditService.recordBusinessFailure(
                    AuditEventType.PRODUCT_OPERATION_REJECTED,
                    AuditEntityType.PRODUCT,
                    id == null ? null : id.toString(),
                    AuditOutcome.REJECTED,
                    Map.of("REASON", BusinessValidationException.auditReason(code))
            );
        } catch (RuntimeException auditException) {
            LOG.warn("Could not persist product audit failure ({})", auditException.getClass().getSimpleName());
        }
        throw new BusinessValidationException(code);
    }
}
