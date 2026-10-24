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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One logged call or visit about an overdue finance. Calls are not recorded automatically; staff log the outcome.
 */
@Entity
@Table(name = "followups", indexes = {
        @Index(name = "idx_followups_finance", columnList = "financeId"),
        @Index(name = "idx_followups_customer", columnList = "customerId"),
        @Index(name = "idx_followups_next_date", columnList = "nextDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Followup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String followupId;

    @Column(nullable = false, updatable = false)
    private String financeId;

    @Column(nullable = false, updatable = false)
    private String customerId;

    @Column(nullable = false, updatable = false)
    private String staffId;

    @Column(nullable = false, updatable = false, length = 30)
    private String callStatus;

    @Column(nullable = false, updatable = false, length = 500)
    private String response;

    @Column(updatable = false)
    private LocalDate promiseDate;

    @Column(nullable = false, updatable = false)
    private LocalDate nextDate;

    @Column(updatable = false, length = 500)
    private String remarks;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
