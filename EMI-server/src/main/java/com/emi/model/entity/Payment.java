package com.emi.model.entity;

import com.emi.utils.enums.PaymentMethod;
import com.emi.utils.enums.PaymentStatus;
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
 * Money received against a finance. It only counts towards the EMI schedule once VERIFIED.
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_finance", columnList = "financeId"),
        @Index(name = "idx_payments_status", columnList = "status"),
        @Index(name = "idx_payments_payment_date", columnList = "paymentDate"),
        @Index(name = "idx_payments_txnid", columnList = "txnId"),
        @Index(name = "idx_payments_reference", columnList = "reference")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String paymentId;

    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String paymentNumber;

    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String receiptNo;

    @Column(nullable = false, updatable = false)
    private String financeId;

    @Column(nullable = false, updatable = false)
    private String customerId;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PaymentMethod method;

    @Column(length = 100)
    private String txnId;

    @Column(length = 150)
    private String reference;

    @Column(length = 500)
    private String remarks;

    private String collectedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private String verifiedBy;

    private OffsetDateTime verifiedAt;

    @Column(nullable = false, updatable = false)
    private String createdBy;

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
