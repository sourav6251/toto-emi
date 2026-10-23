package com.emi.model.entity;


import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Login account for Admin, Customer Care / Collection Staff and (optionally) Customer.
 * Borrower profile data (address, KYC, documents) lives in the customers table, not here.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_role", columnList = "role"),
        @Index(name = "idx_users_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String userID;

    @Column(length = 150)
    private String fullName;

    // Primary login identifier and contact number for calls/reminders
    @Column( unique = true, length = 15)
    private String phoneNumber;

    // Used for forgot-password / notifications; optional for field staff
    @Column( length = 255)
    private String email;

    // @Jsoni 
    private String password;

    @Enumerated(EnumType.STRING)
    @Column( length = 30)
    private Role role;

    // Accounts are deactivated, never hard-deleted, so audit logs and payment records keep a valid reference
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;
    private String otp;
    private LocalDateTime otpExpire;
    @Builder.Default
    private Integer otpVerificationFailedAttempted=0;
    private LocalDateTime lastFailedAttempted;
    private OffsetDateTime lastLoginAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}
