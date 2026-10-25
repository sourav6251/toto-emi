package com.emi.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * The specific unit being financed (one Toto, one bike...), not a product catalog entry.
 */
@Entity
@Table(name = "assets", indexes = {
        @Index(name = "idx_assets_vehicle_number", columnList = "vehicleNumber")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String assetId;

    @Column(nullable = false, length = 50)
    private String productType;

    @Column(nullable = false, length = 150)
    private String productName;

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String model;

    @Column(length = 30)
    private String vehicleNumber;

    // @Column(length = 50)
    // private String chassisNumber;

    // @Column(length = 50)
    // private String engineNumber;

    @Column(length = 30)
    private String registrationNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Column(length = 150)
    private String dealerName;

    @Column(length = 15)
    private String dealerContact;

    private LocalDate purchaseDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}
