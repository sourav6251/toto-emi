package com.emi.service;

import com.emi.dto.response.CustomerDetailResponse;
import com.emi.dto.response.CustomerDetailResponse.AssetInfo;
import com.emi.dto.response.CustomerDetailResponse.CustomerInfo;
import com.emi.dto.response.CustomerDetailResponse.FinanceInfo;
import com.emi.dto.response.CustomerDetailResponse.InstallmentInfo;
import com.emi.dto.response.CustomerDetailResponse.LoginInfo;
import com.emi.dto.response.ProfileResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Asset;
import com.emi.model.entity.Customer;
import com.emi.model.entity.EmiInstallment;
import com.emi.model.entity.Finance;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.AssetJpaRepository;
import com.emi.model.reposetory.CustomerJpaRepository;
import com.emi.model.reposetory.EmiInstallmentJpaRepository;
import com.emi.model.reposetory.FinanceJpaRepository;
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
    private final CustomerJpaRepository customerJpaRepository;
    private final FinanceJpaRepository financeJpaRepository;
    private final AssetJpaRepository assetJpaRepository;
    private final EmiInstallmentJpaRepository emiInstallmentJpaRepository;

    @Transactional(readOnly = true)
    public CustomerDetailResponse getCustomerDetails(String adminId, String customerId) {
        Customer customer = customerJpaRepository.findById(customerId)
                .orElseThrow(() -> new DomainException("Customer not found", HttpStatus.NOT_FOUND));

        Users login = customer.getUserId() == null
                ? null
                : userJpaRepository.findById(customer.getUserId()).orElse(null);

        List<FinanceInfo> finances = financeJpaRepository.findAllByCustomerId(customerId).stream()
                .map(this::toFinanceInfo)
                .toList();

        log.info("Admin {} viewed full details of customer {}", adminId, customerId);

        return new CustomerDetailResponse(
                toCustomerInfo(customer),
                login == null ? null : toLoginInfo(login),
                finances
        );
    }

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

    private CustomerInfo toCustomerInfo(Customer c) {
        return new CustomerInfo(
                c.getCustomerId(), c.getFullName(), c.getGuardianName(), c.getDateOfBirth(), c.getGender(),
                c.getPhoneNumber(), c.getAlternatePhoneNumber(), c.getEmail(), c.getAddressLine(),
                c.getVillage(), c.getPostOffice(), c.getPoliceStation(), c.getDistrict(), c.getState(),
                c.getPinCode(), c.getAadhaarNumber(), c.getPanNumber(), c.getVoterIdNumber(),
                c.getUserId(), c.getAssignedStaffId(), c.getStatus(), c.getCreatedBy(),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    private LoginInfo toLoginInfo(Users u) {
        return new LoginInfo(
                u.getUserID(), u.getFullName(), u.getPhoneNumber(), u.getEmail(), u.getRole(), u.getStatus(),
                u.getLastLoginAt(), u.getCreatedAt(), u.getUpdatedAt()
        );
    }

    private FinanceInfo toFinanceInfo(Finance f) {
        AssetInfo asset = assetJpaRepository.findById(f.getAssetId()).map(this::toAssetInfo).orElse(null);
        List<InstallmentInfo> installments = emiInstallmentJpaRepository
                .findAllByFinanceIdOrderByInstallmentNo(f.getFinanceId()).stream()
                .map(this::toInstallmentInfo)
                .toList();
        return new FinanceInfo(
                f.getFinanceId(), f.getFinanceNumber(), f.getDoNumber(), f.getProductPrice(), f.getDownPayment(),
                f.getFinanceAmount(), f.getInterestType(), f.getInterestRate(), f.getTenureMonths(), f.getEmiAmount(),
                f.getTotalInterest(), f.getProcessingFee(), f.getOtherCharges(), f.getTotalPayable(),
                f.getFirstEmiDate(), f.getEmiDueDay(), f.getGraceDays(), f.getLateFeeAmount(), f.getStatus(),
                f.getRemarks(), f.getCreatedBy(), f.getApprovedBy(), f.getApprovedAt(), f.getClosedAt(),
                f.getCreatedAt(), f.getUpdatedAt(), asset, installments
        );
    }

    private AssetInfo toAssetInfo(Asset a) {
        return new AssetInfo(
                a.getAssetId(), a.getProductType(), a.getProductName(), a.getBrand(), a.getModel(),
                a.getVehicleNumber(), a.getRegistrationNumber(),
                a.getPurchasePrice(), a.getDealerName(), a.getDealerContact(), a.getPurchaseDate(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }

    private InstallmentInfo toInstallmentInfo(EmiInstallment e) {
        return new InstallmentInfo(
                e.getInstallmentId(), e.getInstallmentNo(), e.getDueDate(), e.getPrincipalAmount(),
                e.getInterestAmount(), e.getEmiAmount(), e.getAmountPaid(), e.getLateFee(), e.getLateFeePaid(),
                e.getStatus(), e.getPaidAt(), e.getCreatedAt(), e.getUpdatedAt()
        );
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
