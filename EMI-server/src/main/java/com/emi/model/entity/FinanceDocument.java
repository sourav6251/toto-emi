package com.emi.model.entity;

import com.emi.utils.enums.DocumentVerification;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * A document attached to a finance. A finance holds at most one per document type; replacing the photo updates the row.
 */
@Entity
@Table(name = "finance_documents",
        uniqueConstraints = @UniqueConstraint(name = "uk_finance_documents_finance_key", columnNames = {"financeId", "docKey"}),
        indexes = @Index(name = "idx_finance_documents_finance", columnList = "financeId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinanceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String documentId;

    @Column(nullable = false, updatable = false)
    private String financeId;

    @Column(nullable = false, updatable = false)
    private String customerId;

    @Column(nullable = false, updatable = false, length = 30)
    private String docKey;

    @Column(nullable = false)
    private String fileId;

    @Column(length = 50)
    private String docNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentVerification verification;

    @Column(nullable = false)
    private String uploadedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}
