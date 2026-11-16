package com.keycode.ilussioness_backend.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Inventory item stored in the {@code product} table.
 *
 * <p>The subtype classes ({@link Dress}, {@link Card}, ...) are plain domain
 * classes; the {@code type} column identifies the kind of product.</p>
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID productId;

    @NotNull(message = "type is required")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false)
    protected ProductType type;

    @NotBlank(message = "name is required")
    @Size(max = 120, message = "name must be at most 120 characters")
    @Column(name = "name", nullable = false, length = 120)
    protected String name;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.00", message = "price must not be negative")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    protected BigDecimal price;

    @Column(name = "photo_url")
    protected String photo;

    @Column(name = "materials")
    protected String materials;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    protected ProductStatus status;

    @Min(value = 0, message = "stock must not be negative")
    @Column(name = "stock", nullable = false)
    protected Integer stock;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public Product() {
    }

    public Product(
            UUID productId,
            BigDecimal price,
            String photo,
            String materials,
            ProductStatus status) {
        this.productId = productId;
        this.price = price;
        this.photo = photo;
        this.materials = materials;
        this.status = status;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public ProductType getType() {
        return type;
    }

    public void setType(ProductType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public String getMaterials() {
        return materials;
    }

    public void setMaterials(String materials) {
        this.materials = materials;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    /**
     * Adds {@code delta} units to the stock and keeps {@code status} coherent:
     * an available product with no units becomes {@code OUT_OF_STOCK} and vice versa.
     *
     * @throws IllegalStateException when the resulting stock would be negative.
     */
    public void adjustStock(int delta) {
        int current = stock == null ? 0 : stock;
        int next = current + delta;
        if (next < 0) {
            throw new IllegalStateException("insufficient_stock");
        }
        stock = next;
        if (next == 0 && (status == null || status == ProductStatus.AVAILABLE)) {
            status = ProductStatus.OUT_OF_STOCK;
        } else if (next > 0 && status == ProductStatus.OUT_OF_STOCK) {
            status = ProductStatus.AVAILABLE;
        }
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
