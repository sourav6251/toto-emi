package com.emi.model.entity;

import com.emi.utils.enums.CustomerStatus;
import com.emi.utils.enums.Gender;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Borrower profile. A customer may also have a login (userId) for the customer portal.
 */
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customers_phone_number", columnList = "phoneNumber"),
        @Index(name = "idx_customers_assigned_staff", columnList = "assignedStaffId"),
        @Index(name = "idx_customers_full_name", columnList = "fullName")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private String customerId;

    @Column(length = 150)
    private String fullName;

    @Column(length = 150)
    private String guardianName;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(nullable = false, length = 15)
    private String phoneNumber;

    @Column(length = 15)
    private String alternatePhoneNumber;

    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String addressLine;

    @Column(length = 100)
    private String village;

    @Column(length = 100)
    private String postOffice;

    @Column(length = 100)
    private String policeStation;

    @Column(length = 100)
    private String district;

    @Column(length = 100)
    private String state;

    @Column(length = 6)
    private String pinCode;

    @Column(name = "aadhaar_number", length = 12)
    private String aadhaarNumber;

    @Column(length = 10)
    private String panNumber;

    @Column(length = 20)
    private String voterIdNumber;

    @Column(unique = true)
    private String userId;

    private String assignedStaffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public String getAadhaarMasked() {
        if (aadhaarNumber == null || aadhaarNumber.isBlank()) {
            return null;
        }
        String a = aadhaarNumber.trim();
        return a.length() <= 4 ? a : "XXXX XXXX " + a.substring(a.length() - 4);
    }
}
