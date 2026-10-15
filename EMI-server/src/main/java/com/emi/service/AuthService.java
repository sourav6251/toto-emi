package com.emi.service;


import com.emi.config.JWTService;
import com.emi.dto.request.LoginRequest;
import com.emi.dto.request.RegisterRequest;
import com.emi.dto.request.ResendOtpRequest;
import com.emi.dto.request.VerifyOtpRequest;
import com.emi.dto.response.AuthResponse;
import com.emi.dto.response.MessageResponse;
import com.emi.dto.response.RegisterResponse;
import com.emi.event.user.OtpRequestedEvent;
import com.emi.event.user.UserRegisteredEvent;
import com.emi.exception.DomainException;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.utils.enums.OtpPurpose;
import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String OTP_SENT_MESSAGE = "If the number is registered and pending verification, an OTP has been sent";

    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final OtpService otpService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Users login(LoginRequest request) {
        String phoneNumber = request.getPhoneNumber().trim();
        Users user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new DomainException("Invalid phone number or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new DomainException("Invalid phone number or password", HttpStatus.UNAUTHORIZED);
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new DomainException("Account is suspended. Please contact support.", HttpStatus.FORBIDDEN);
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new DomainException("Account verification pending. Please verify OTP first.", HttpStatus.FORBIDDEN);
        }

        return user;
    }

    // Public self-registration always creates a CUSTOMER; staff accounts are created by an Admin
    @Transactional
    public RegisterResponse register(String id, RegisterRequest request) {
        Users existUser = userRepository.findById(id)
                .orElseThrow(() -> new DomainException("User not found", HttpStatus.NOT_FOUND));

        // Check if this user is already active and registered
        if (existUser.getStatus() == UserStatus.ACTIVE && existUser.getPassword() != null) {
            throw new DomainException("User is already registered", HttpStatus.CONFLICT);
        }

        String email = request.getEmail() == null || request.getEmail().isBlank()
                ? null
                : request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (email != null && (existUser.getEmail() == null || !email.equalsIgnoreCase(existUser.getEmail()))
                && userRepository.existsByEmail(email)) {
            throw new DomainException("Email is already registered", HttpStatus.CONFLICT);
        }

        existUser.setFullName(request.getFullName().trim());
        existUser.setEmail(email);
        existUser.setPassword(passwordEncoder.encode(request.getPassword()));
        existUser.setRole(Role.CUSTOMER);
        existUser.setStatus(UserStatus.ACTIVE);

        try {
            existUser = userRepository.saveAndFlush(existUser);
        } catch (DataIntegrityViolationException e) {
            throw new DomainException("Phone number or email is already registered", HttpStatus.CONFLICT);
        }

        // Delivered to listeners only after this transaction commits
        eventPublisher.publishEvent(UserRegisteredEvent.of(existUser.getUserID(), existUser.getRole()));

        return new RegisterResponse(
                existUser.getUserID(),
                existUser.getPhoneNumber(),
                existUser.getRole().toString(),
                existUser.getStatus(),
                "Registration successful"
        );
    }

    // A wrong OTP must still persist the incremented attempt counter, so don't roll back on DomainException
    @Transactional(noRollbackFor = DomainException.class)
    public String verifyOtp(VerifyOtpRequest request) {
        Users user = userRepository.findByPhoneNumber(request.phoneNumber().trim())
                .orElseThrow(() -> new DomainException("User not found", HttpStatus.NOT_FOUND));

        String dbOTP = user.getOtp();
        LocalDateTime otpExpire = user.getOtpExpire();

        if (dbOTP == null || otpExpire == null) {
            throw new DomainException("No OTP found. Please request a new OTP", HttpStatus.BAD_REQUEST);
        }

        if (LocalDateTime.now().isAfter(otpExpire)) {
            throw new DomainException("Otp Expired", HttpStatus.BAD_REQUEST);
        }

        if (!dbOTP.equals(request.otp().trim())) {
            int attempts = user.getOtpVerificationFailedAttempted() != null ? user.getOtpVerificationFailedAttempted() + 1 : 1;
            user.setOtpVerificationFailedAttempted(attempts);
            user.setLastFailedAttempted(LocalDateTime.now());
            userRepository.save(user);
            throw new DomainException("Invalid otp", HttpStatus.BAD_REQUEST);
        }

        user.setOtp(null);
        user.setOtpExpire(null);
        user.setOtpVerificationFailedAttempted(0);
        Users updatedUser = userRepository.save(user);
        return updatedUser.getUserID();
    }

    // Same response whether or not the number exists, so this can't be used to discover registered numbers
    @Transactional
    public MessageResponse resendOtp(ResendOtpRequest request) {
        String otp = generateOTP();
        Users user = userRepository.findByPhoneNumber(request.phoneNumber().trim())
                .orElseGet(() -> userRepository.save(Users.builder().phoneNumber(request.phoneNumber().trim()).build()));
        user.setOtp(otp);
        user.setOtpExpire(LocalDateTime.now().plusMinutes(5));
        user.setOtpVerificationFailedAttempted(0);
        userRepository.save(user);
        return new MessageResponse(otp);
    }

    private String generateOTP() {
        SecureRandom random = new SecureRandom();
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
