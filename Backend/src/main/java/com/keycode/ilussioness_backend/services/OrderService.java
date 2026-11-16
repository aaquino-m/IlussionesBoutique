package com.keycode.ilussioness_backend.services;

import com.keycode.ilussioness_backend.audit.domain.AuditEntityType;
import com.keycode.ilussioness_backend.audit.domain.AuditEventType;
import com.keycode.ilussioness_backend.audit.domain.AuditOutcome;
import com.keycode.ilussioness_backend.audit.service.AuditService;
import com.keycode.ilussioness_backend.models.Order;
import com.keycode.ilussioness_backend.models.Product;
import com.keycode.ilussioness_backend.models.Customer;
import com.keycode.ilussioness_backend.repositories.CustomerRepository;
import com.keycode.ilussioness_backend.repositories.OrderRepository;
import com.keycode.ilussioness_backend.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);
    private static final int ORDER_ID_MAX_LENGTH = 64;

    private static final Set<String> STATUSES =
            Set.of("PENDING", "IN_PROGRESS", "READY", "DELIVERED", "CANCELLED");

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;

    public OrderService(
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            AuditService auditService
    ) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomer(UUID customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(String id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public Order createOrder(Order order) {
        try {
            requireValidOrder(order);
            if (orderRepository.existsById(order.getOrderId())) {
                throw new BusinessValidationException("order_already_exists");
            }
            order.setStockReserved(false);
            if (order.getStatus() == null || order.getStatus().isBlank()) {
                order.setStatus("PENDING");
            }
            requireValidStatus(order.getStatus());
            order.setCustomer(resolveCustomer(order.getCustomer()));
            order.setProducts(resolveProducts(order.getProducts()));
            reserveStock(order.getProducts());
            order.setStockReserved(true);
        } catch (BusinessValidationException ex) {
            auditFailure(
                    AuditEventType.ORDER_CREATE_FAILED,
                    order == null ? null : order.getOrderId(),
                    AuditOutcome.REJECTED,
                    BusinessValidationException.auditReason(ex.getCode())
            );
            throw ex;
        }

        Order saved;
        try {
            saved = orderRepository.saveAndFlush(order);
        } catch (RuntimeException ex) {
            auditFailure(AuditEventType.ORDER_CREATE_FAILED, null, AuditOutcome.FAILURE, "PERSISTENCE_ERROR");
            throw ex;
        }

        auditService.recordBusinessSuccess(
                AuditEventType.ORDER_CREATED,
                AuditEntityType.ORDER,
                saved.getOrderId(),
                buildMetadata(saved)
        );
        return saved;
    }

    /**
     * Applies the non-null fields of {@code updates} to the stored order.
     *
     * @return the updated order, or empty when it did not exist.
     */
    @Transactional
    public Optional<Order> updateOrder(String id, Order updates) {
        if (id == null || id.isBlank()) {
            throw new BusinessValidationException("id_required");
        }
        Optional<Order> existing = orderRepository.findById(id);
        if (existing.isEmpty()) {
            auditFailure(AuditEventType.ORDER_UPDATE_REJECTED, id, AuditOutcome.REJECTED, "NOT_FOUND");
            return Optional.empty();
        }

        Order order = existing.get();
        if (updates != null) {
            if (updates.getEventType() != null) {
                order.setEventType(updates.getEventType());
            }
            if (updates.getDeliveryDate() != null) {
                order.setDeliveryDate(updates.getDeliveryDate());
            }
            if (updates.getStatus() != null) {
                order.setStatus(updates.getStatus());
            }
        }

        try {
            requireValidOrder(order);
            if (updates != null) {
                if (order.getStatus() != null) {
                    requireValidStatus(order.getStatus());
                }
                if (updates.getCustomer() != null) {
                    order.setCustomer(resolveCustomer(updates.getCustomer()));
                }
                if (updates.getProducts() != null) {
                    requireValidProducts(updates.getProducts());
                    if (order.isStockReserved()) {
                        releaseStock(order.getProducts());
                    }
                    List<Product> products = resolveProducts(updates.getProducts());
                    reserveStock(products);
                    order.setProducts(products);
                    order.setStockReserved(true);
                }
            }
        } catch (BusinessValidationException ex) {
            auditFailure(
                    AuditEventType.ORDER_UPDATE_REJECTED,
                    id,
                    AuditOutcome.REJECTED,
                    BusinessValidationException.auditReason(ex.getCode())
            );
            throw ex;
        }

        Order saved;
        try {
            saved = orderRepository.saveAndFlush(order);
        } catch (RuntimeException ex) {
            auditFailure(AuditEventType.ORDER_UPDATE_FAILED, id, AuditOutcome.FAILURE, "PERSISTENCE_ERROR");
            throw ex;
        }

        auditService.recordBusinessSuccess(
                AuditEventType.ORDER_UPDATED,
                AuditEntityType.ORDER,
                id,
                buildMetadata(saved)
        );
        return Optional.of(saved);
    }

    /**
     * @return true when deleted, false when the order did not exist.
     */
    @Transactional
    public boolean deleteOrder(String id) {
        Optional<Order> existing = orderRepository.findById(id);
        if (existing.isEmpty()) {
            auditFailure(AuditEventType.ORDER_DELETE_REJECTED, id, AuditOutcome.REJECTED, "NOT_FOUND");
            return false;
        }

        try {
            if (existing.get().isStockReserved() && !"DELIVERED".equals(existing.get().getStatus())) {
                releaseStock(existing.get().getProducts());
            }
            orderRepository.deleteById(id);
            orderRepository.flush();
        } catch (RuntimeException ex) {
            auditFailure(AuditEventType.ORDER_DELETE_FAILED, id, AuditOutcome.FAILURE, "PERSISTENCE_ERROR");
            throw ex;
        }

        auditService.recordBusinessSuccess(
                AuditEventType.ORDER_DELETED,
                AuditEntityType.ORDER,
                id,
                Map.of()
        );
        return true;
    }

    private static Map<String, Object> buildMetadata(Order order) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        int productCount = order.getProducts() == null ? 0 : order.getProducts().size();
        metadata.put("PRODUCT_COUNT", Math.min(productCount, 10_000));
        metadata.put("HAS_DELIVERY_DATE", order.getDeliveryDate() != null);
        return metadata;
    }

    private static void requireValidStatus(String status) {
        if (!STATUSES.contains(status)) {
            throw new BusinessValidationException("invalid_status");
        }
    }

    private Customer resolveCustomer(Customer reference) {
        if (reference == null || reference.getId() == null) {
            return null;
        }
        return customerRepository.findById(reference.getId())
                .orElseThrow(() -> new BusinessValidationException("customer_not_found"));
    }

    /**
     * Replaces product references that carry an id with the managed inventory
     * rows, so stock can be updated.
     */
    private List<Product> resolveProducts(List<Product> products) {
        if (products == null) {
            return null;
        }
        List<Product> resolved = new ArrayList<>(products.size());
        for (Product product : products) {
            if (product.getProductId() == null) {
                resolved.add(product);
            } else {
                resolved.add(productRepository.findByIdForUpdate(product.getProductId())
                        .orElseThrow(() -> new BusinessValidationException("product_not_found")));
            }
        }
        return resolved;
    }

    private static void reserveStock(List<Product> products) {
        if (products == null) {
            return;
        }
        for (Product product : products) {
            if (product.getProductId() != null) {
                try {
                    product.adjustStock(-1);
                } catch (IllegalStateException ex) {
                    throw new BusinessValidationException("insufficient_stock");
                }
            }
        }
    }

    private static void releaseStock(List<Product> products) {
        if (products == null) {
            return;
        }
        for (Product product : products) {
            if (product.getProductId() != null) {
                product.adjustStock(1);
            }
        }
    }

    private static void requireValidOrder(Order order) {
        if (order == null) {
            throw new BusinessValidationException("payload_required");
        }
        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            throw new BusinessValidationException("order_id_required");
        }
        if (order.getOrderId().length() > ORDER_ID_MAX_LENGTH) {
            throw new BusinessValidationException("order_id_too_long");
        }
        if (order.getEventType() == null || order.getEventType().isBlank()) {
            throw new BusinessValidationException("event_type_required");
        }
        requireValidProducts(order.getProducts());
    }

    private static void requireValidProducts(List<Product> products) {
        if (products != null) {
            for (Product product : products) {
                if (product == null) {
                    throw new BusinessValidationException("invalid_product");
                }
                BigDecimal price = product.getPrice();
                if (price != null && price.signum() < 0) {
                    throw new BusinessValidationException("negative_price");
                }
            }
        }
    }

    private void auditFailure(
            AuditEventType eventType,
            String entityId,
            AuditOutcome outcome,
            String reason
    ) {
        try {
            auditService.recordBusinessFailure(
                    eventType,
                    AuditEntityType.ORDER,
                    entityId,
                    outcome,
                    Map.of("REASON", reason)
            );
        } catch (RuntimeException auditException) {
            LOG.warn("Could not persist order audit failure {} ({})",
                    eventType, auditException.getClass().getSimpleName());
        }
    }
}
