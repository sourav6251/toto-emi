package com.emi.model.entity;

import com.emi.utils.enums.InstallmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * One month of a finance's EMI schedule.
 * Upcoming / Due / Overdue are derived from dueDate and today, never stored.
 */
@Entity
@Table(name = "emi_installments",
        uniqueConstraints = @UniqueConstraint(name = "uk_emi_installments_finance_no",
                columnNames = {"financeId", "installmentNo"}),
        indexes = @Index(name = "idx_emi_installments_due_status", columnList = "dueDate, status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmiInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String installmentId;

    @Column(nullable = false, updatable = false)
    private String financeId;

    @Column(nullable = false, updatable = false)
    private Integer installmentNo;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal interestAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal emiAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lateFeePaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InstallmentStatus status = InstallmentStatus.PENDING;

    private LocalDate paidAt;

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
