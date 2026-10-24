package com.emi.model.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Product stock catalog entry available for sale or financing.
 */
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_product_name", columnList = "productName"),
        @Index(name = "idx_products_product_type", columnList = "productType")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String productId;

    @Column(nullable = false, length = 150)
    private String productName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", nullable = false, length = 500)
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "product_type", length = 50)
    private String productType;

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String model;

    @Column(nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean inStock = true;

    @Column(nullable = false, updatable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}

