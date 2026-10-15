package com.emi.model.reposetory;

import com.emi.model.entity.UserOtp;
import com.emi.utils.enums.OtpPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface UserOtpJpaRepository extends JpaRepository<UserOtp, String> {

    Optional<UserOtp> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(String userId, OtpPurpose purpose);

    // Row lock so concurrent verify calls can't both pass the attempt limit or both consume the code
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserOtp> findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(String userId, OtpPurpose purpose);
}
