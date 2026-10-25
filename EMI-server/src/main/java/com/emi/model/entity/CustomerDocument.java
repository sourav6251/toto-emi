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

@Entity
@Table(name = "customer_documents",
        uniqueConstraints = @UniqueConstraint(name = "uk_customer_documents_key", columnNames = {"customerId", "docKey"}),
        indexes = @Index(name = "idx_customer_documents_customer", columnList = "customerId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, updatable = false)
    private String customerId;

    @Column(nullable = false, updatable = false, length = 30)
    private String docKey;

    @Column(nullable = false)
    private String fileId;

    @Column(length = 50)
    private String docNumber;

    @Column(length = 255)
    private String fileName;

    @Column(length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DocumentVerification verification = DocumentVerification.PENDING;

    @Column(nullable = false, updatable = false)
    private String uploadedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}

