package com.emi.model.entity;


import com.emi.utils.enums.OtpPurpose;
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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One-time code sent to a user's phone. Only the hash is stored.
 */
//@Entity
//@Table(name = "user_otps", indexes = {
//        @Index(name = "idx_user_otps_user_purpose", columnList = "userId, purpose")
//})
//@Getter
//@Setter
//@NoArgsConstructor
//@AllArgsConstructor
//@Builder
public class UserOtp {

//    @Id
//    @GeneratedValue(strategy = GenerationType.UUID)
//    @Column(nullable = false, updatable = false)
//    private String otpId;
//
//    @Column(nullable = false, updatable = false)
//    private String userId;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false, length = 30, updatable = false)
//    private OtpPurpose purpose;
//
//    @Column(nullable = false, updatable = false)
//    private String codeHash;
//
//    @Column(nullable = false, updatable = false)
//    private OffsetDateTime expiresAt;
//
//    @Column(nullable = false)
//    @Builder.Default
//    private int attempts = 0;
//
//    private OffsetDateTime usedAt;
//
//    @CreationTimestamp
//    @Column(nullable = false, updatable = false)
//    private OffsetDateTime createdAt;
}
