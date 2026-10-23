package com.emi.model.entity;

import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.InterestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
 * One finance agreement: one customer + one asset, with its terms frozen at creation.
 */
@Entity
@Table(name = "finances", indexes = {
        @Index(name = "idx_finances_customer", columnList = "customerId"),
        @Index(name = "idx_finances_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Finance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String financeId;

    // Human-readable id shown to users, e.g. FIN-2026-000125
    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String financeNumber;

    @Column(nullable = false, updatable = false)
    private String customerId;

    @Column(nullable = false, unique = true, updatable = false)
    private String assetId;

    @Column(nullable = false, unique = true, length = 50)
    private String doNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal productPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal downPayment;

    // productPrice - downPayment
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal financeAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterestType interestType;

    // Annual rate in percent, e.g. 18.000
    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private Integer tenureMonths;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal emiAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalInterest;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal processingFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal otherCharges = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPayable;

    @Column(nullable = false)
    private LocalDate firstEmiDate;

    // 1-28 so every month has this day
    @Column(nullable = false)
    private Integer emiDueDay;

    @Column(nullable = false)
    @Builder.Default
    private Integer graceDays = 0;

    // Fixed fee charged once per installment overdue past the grace period
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lateFeeAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FinanceStatus status = FinanceStatus.PENDING_APPROVAL;

    @Column(length = 500)
    private String remarks;

    @Column(nullable = false, updatable = false)
    private String createdBy;

    private String approvedBy;

    private OffsetDateTime approvedAt;

    private OffsetDateTime closedAt;

    // Optimistic lock: two staff recording payments at once can't overwrite each other
    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}
