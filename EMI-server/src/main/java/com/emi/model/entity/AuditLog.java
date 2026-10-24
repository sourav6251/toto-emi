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

import java.time.OffsetDateTime;

/**
 * Append-only audit entry. Every column is non-updatable and the repository exposes no delete path.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_user", columnList = "userId"),
        @Index(name = "idx_audit_logs_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String auditId;

    @Column(nullable = false, updatable = false)
    private String userId;

    @Column(nullable = false, updatable = false, length = 50)
    private String action;

    @Column(updatable = false, length = 500)
    private String detail;

    @Column(updatable = false)
    private String ref;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
