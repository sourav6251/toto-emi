package com.emi.service;


import com.emi.exception.DomainException;
import com.emi.model.entity.UserOtp;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.model.reposetory.UserOtpJpaRepository;
import com.emi.notification.OtpSender;
import com.emi.utils.enums.OtpPurpose;
import com.emi.utils.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserOtpJpaRepository otpRepository;
    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpSender otpSender;

    /**
     * Generates, stores (hashed) and sends a new OTP. Skips silently if one was sent within the cooldown,
     * so repeated resend calls can't be used to spam a phone number.
     */
    @Transactional
    public void issue(String userId, OtpPurpose purpose) {
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean inCooldown = otpRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .map(last -> last.getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now))
                .orElse(false);
        if (inCooldown) {
            log.info("OTP for userId={} skipped: resend cooldown active", userId);
            return;
        }

        String code = generateCode();
        otpRepository.save(UserOtp.builder()
                .userId(userId)
                .purpose(purpose)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(now.plus(OTP_TTL))
                .build());

        otpSender.send(user.getPhoneNumber(), code);
    }

    /**
     * Checks the latest unused OTP. Must run inside the caller's transaction; a wrong code increments
     * the attempt counter, so the caller must not roll back on the DomainException thrown here.
     */
    @Transactional(noRollbackFor = DomainException.class)
    public void verify(String userId, OtpPurpose purpose, String code) {
        UserOtp otp = otpRepository.findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(userId, purpose)
                .orElseThrow(() -> new DomainException("OTP not found, please request a new one"));

        OffsetDateTime now = OffsetDateTime.now();
        if (otp.getExpiresAt().isBefore(now)) {
            throw new DomainException("OTP has expired, please request a new one");
        }
        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new DomainException("Too many incorrect attempts, please request a new OTP");
        }
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            throw new DomainException("Invalid OTP");
        }

        otp.setUsedAt(now);
    }

    private static String generateCode() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        return String.format("%0" + OTP_LENGTH + "d", RANDOM.nextInt(bound));
    }
}
