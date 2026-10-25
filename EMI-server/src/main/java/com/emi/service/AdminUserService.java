package com.emi.service;

import com.emi.dto.response.ProfileResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class AdminUserService {

    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ProfileResponse createCustomerCare(String phoneNumber, String password) {
        if (userJpaRepository.existsByPhoneNumber(phoneNumber)) {
            throw new DomainException("Phone number is already registered", HttpStatus.CONFLICT);
        }

        Users user = Users.builder()
                .phoneNumber(phoneNumber)
                .password(passwordEncoder.encode(password))
                .role(Role.CUSTOMER_CARE)
                .status(UserStatus.ACTIVE)
                .build();

        try {
            user = userJpaRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new DomainException("Phone number is already registered", HttpStatus.CONFLICT);
        }
        log.info("Created CUSTOMER_CARE user {}", user.getUserID());

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> getUsers(Role role) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt");
        List<Users> users = role == null
                ? userJpaRepository.findAll(newestFirst)
                : userJpaRepository.findAllByRole(role, newestFirst);
        return users.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProfileResponse updateRole(String adminId, String userId, Role newRole) {
        if (newRole == Role.ADMIN) {
            throw new DomainException("ADMIN role cannot be assigned through this API", HttpStatus.FORBIDDEN);
        }
        if (adminId.equals(userId)) {
            throw new DomainException("You cannot change your own role");
        }

        Users user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found", HttpStatus.NOT_FOUND));

        if (user.getRole() == Role.ADMIN) {
            throw new DomainException("Cannot change the role of another admin", HttpStatus.FORBIDDEN);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DomainException("Only ACTIVE users can be assigned a role");
        }
        if (user.getRole() == newRole) {
            throw new DomainException("User already has role " + newRole);
        }

        Role oldRole = user.getRole();
        user.setRole(newRole);
        userJpaRepository.save(user);
        log.info("Admin {} changed role of user {} from {} to {}", adminId, userId, oldRole, newRole);

        return toResponse(user);
    }

    private ProfileResponse toResponse(Users user) {
        return new ProfileResponse(
                user.getUserID(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getRole(),
                user.getStatus()
        );
    }
}
