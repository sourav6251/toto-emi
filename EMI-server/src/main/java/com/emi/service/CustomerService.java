package com.emi.service;

import com.emi.dto.request.CustomerProfileRequest;
import com.emi.dto.request.WalkInCustomerRequest;
import com.emi.dto.response.CustomerDetailResponse;
import com.emi.dto.response.CustomerResponse;
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
import com.emi.utils.enums.CustomerStatus;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.InstallmentStatus;
import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class CustomerService {

    private final CustomerJpaRepository customerJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final FinanceJpaRepository financeJpaRepository;
    private final AssetJpaRepository assetJpaRepository;
    private final EmiInstallmentJpaRepository emiInstallmentJpaRepository;
    private final FinanceSummaryAssembler financeSummaryAssembler;

    @Transactional
    public CustomerResponse createMyProfile(String userId, CustomerProfileRequest request) {
        Users user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found", HttpStatus.NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE || user.getPhoneNumber() == null) {
            throw new DomainException("Verify your phone number before applying for finance");
        }
        if (customerJpaRepository.findByUserId(userId).isPresent()) {
            throw new DomainException("Customer profile already exists", HttpStatus.CONFLICT);
        }

        Customer customer = profileFields(request)
                .phoneNumber(user.getPhoneNumber())
                .userId(userId)
                .status(CustomerStatus.ACTIVE)
                .createdBy(userId)
                .build();

        return toResponse(customerJpaRepository.save(customer));
    }

    @Transactional
    public CustomerResponse createWalkIn(String staffId, boolean assignToStaff, CustomerProfileRequest request) {
        String phoneNumber = request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null;
        String fullName = request.getFullName() != null ? request.getFullName().trim() : null;
        String email = request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail().trim().toLowerCase(Locale.ROOT)
                : null;

        if (customerJpaRepository.existsByPhoneNumber(phoneNumber)) {
            throw new DomainException("A customer profile already exists for this phone number", HttpStatus.CONFLICT);
        }
        
        Users user = userJpaRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (user != null) {
            if (user.getRole() == Role.ADMIN || user.getRole() == Role.CUSTOMER_CARE) {
                throw new DomainException("This phone number belongs to a staff account", HttpStatus.CONFLICT);
            }
            if (customerJpaRepository.findByUserId(user.getUserID()).isPresent()) {
                throw new DomainException("A customer profile already exists for this phone number", HttpStatus.CONFLICT);
            }
            // Leftover user from an unfinished app sign-up (OTP requested, never registered): reuse it
            if (fullName != null) {
                user.setFullName(fullName);
            }
            if (email != null) {
                user.setEmail(email);
            }
            user.setRole(Role.CUSTOMER);
            user.setStatus(UserStatus.ACTIVE);
        } else {
            user = Users.builder()
                    .phoneNumber(phoneNumber)
                    .fullName(fullName)
                    .email(email)
                    .role(Role.CUSTOMER)
                    .status(UserStatus.ACTIVE)
                    .build();
        }
        Users savedUser = userJpaRepository.save(user);

        Customer customer = profileFields(request)
                .phoneNumber(phoneNumber)
                .userId(savedUser.getUserID())
                .assignedStaffId(assignToStaff ? staffId : null)
                .status(CustomerStatus.ACTIVE)
                .createdBy(staffId)
                .build();

        return toResponse(customerJpaRepository.save(customer));
    }

    @Transactional
    public CustomerResponse createOrUpdateWalkIn(String staffId, boolean assignToStaff, CustomerProfileRequest request) {
        String phoneNumber = request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null;
        Customer existing = customerJpaRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (existing != null) {
            applyProfileFields(existing, request);
            return toResponse(customerJpaRepository.save(existing));
        }
        return createWalkIn(staffId, assignToStaff, request);
    }

    private void applyProfileFields(Customer c, CustomerProfileRequest request) {
        if (request.getFullName() != null) c.setFullName(request.getFullName().trim());
        if (request.getGuardianName() != null) c.setGuardianName(request.getGuardianName().trim());
        if (request.getDateOfBirth() != null) c.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) c.setGender(request.getGender());
        if (request.getAlternatePhoneNumber() != null) c.setAlternatePhoneNumber(request.getAlternatePhoneNumber().trim());
        if (request.getEmail() != null) c.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        if (request.getAddressLine() != null) c.setAddressLine(request.getAddressLine().trim());
        if (request.getVillage() != null) c.setVillage(request.getVillage().trim());
        if (request.getPostOffice() != null) c.setPostOffice(request.getPostOffice().trim());
        if (request.getPoliceStation() != null) c.setPoliceStation(request.getPoliceStation().trim());
        if (request.getDistrict() != null) c.setDistrict(request.getDistrict().trim());
        if (request.getState() != null) c.setState(request.getState().trim());
        if (request.getPinCode() != null) c.setPinCode(request.getPinCode().trim());
        if (request.getAadhaarNumber() != null && !request.getAadhaarNumber().isBlank()) {
            c.setAadhaarNumber(request.getAadhaarNumber().trim());
        }
        if (request.getPanNumber() != null) c.setPanNumber(request.getPanNumber().trim());
        if (request.getVoterIdNumber() != null) c.setVoterIdNumber(request.getVoterIdNumber().trim());
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse getCustomerDetail(String id) {
        Customer customer = customerJpaRepository.findByUserId(id)
                .or(() -> customerJpaRepository.findById(id))
                .orElseThrow(() -> new DomainException("Customer not found", HttpStatus.NOT_FOUND));

        return buildCustomerDetail(customer);
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse getCustomerDetail(String callerId, Authentication authentication, String customerId) {
        Customer customer = customerJpaRepository.findById(customerId)
                .or(() -> customerJpaRepository.findByUserId(customerId))
                .orElseThrow(() -> new DomainException("Customer not found", HttpStatus.NOT_FOUND));

        boolean isCare = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER_CARE"));
        boolean isCustomer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));

        if (isCustomer) {
            if (customer.getUserId() == null || !customer.getUserId().equals(callerId)) {
                throw new DomainException("Access denied", HttpStatus.FORBIDDEN);
            }
        } else if (isCare) {
            if (customer.getAssignedStaffId() != null && !customer.getAssignedStaffId().equals(callerId)) {
                throw new DomainException("Customer is not assigned to you", HttpStatus.FORBIDDEN);
            }
        }

        return buildCustomerDetail(customer);
    }

    @Transactional(readOnly = true)
    public List<Users> searchCustomerByMobileNo(String mobileNo) {
        if (mobileNo == null || mobileNo.trim().length() < 3) {
            throw new DomainException("Search query must be at least 3 digits", HttpStatus.BAD_REQUEST);
        }
        String pattern = mobileNo.trim();
        if (pattern.startsWith("+91")) {
            pattern = pattern.substring(3).trim();
        }
        if (pattern.length() < 3) {
            throw new DomainException("Search query must be at least 3 digits", HttpStatus.BAD_REQUEST);
        }

        return userJpaRepository.searchByPhoneOrName(pattern);
    }

    private CustomerDetailResponse buildCustomerDetail(Customer customer) {
        String assignedToName = "Unassigned";
        if (customer.getAssignedStaffId() != null) {
            assignedToName = userJpaRepository.findById(customer.getAssignedStaffId())
                    .map(Users::getFullName)
                    .orElse("Unassigned");
        }

        String aadhaarNumber = customer.getAadhaarNumber();
        String aadhaarMasked = customer.getAadhaarMasked();

        String panNumber = customer.getPanNumber();
        String panMasked = (panNumber != null && !panNumber.isBlank())
                ? (panNumber.trim().length() >= 4 ? "XXXXX" + panNumber.trim().substring(panNumber.trim().length() - 4) : panNumber.trim())
                : null;

        String voterIdNumber = customer.getVoterIdNumber();
        String voterMasked = (voterIdNumber != null && !voterIdNumber.isBlank())
                ? (voterIdNumber.trim().length() >= 4 ? "XXXXXX" + voterIdNumber.trim().substring(voterIdNumber.trim().length() - 4) : voterIdNumber.trim())
                : null;

        List<Finance> financeEntities = financeJpaRepository.findAllByCustomerId(customer.getCustomerId());
        LocalDate today = LocalDate.now();

        List<CustomerDetailResponse.FinanceSummaryDto> finances = new ArrayList<>();
        List<String> productTypes = new ArrayList<>();
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;

        for (Finance f : financeEntities) {
            Asset asset = f.getAssetId() != null
                    ? assetJpaRepository.findById(f.getAssetId()).orElse(null)
                    : null;
            List<EmiInstallment> installments = emiInstallmentJpaRepository
                    .findAllByFinanceIdOrderByInstallmentNo(f.getFinanceId());

            FinanceSummaryAssembler.Summary summary = financeSummaryAssembler.assemble(f, customer, assignedToName, asset, installments, today);
            finances.add(summary.dto());
            if (!productTypes.contains(summary.dto().getProduct().getType())) {
                productTypes.add(summary.dto().getProduct().getType());
            }
            totalOutstanding = totalOutstanding.add(summary.outstanding());
            totalOverdue = totalOverdue.add(summary.overdueAmount());
        }

        CustomerStatus customerStatus = customer.getStatus() != null ? customer.getStatus() : CustomerStatus.ACTIVE;

        CustomerDetailResponse.CustomerInfo customerInfo = new CustomerDetailResponse.CustomerInfo(
                customer.getCustomerId(),
                customer.getFullName(),
                customer.getGuardianName(),
                customer.getDateOfBirth(),
                customer.getGender(),
                customer.getPhoneNumber(),
                customer.getAlternatePhoneNumber(),
                customer.getEmail(),
                customer.getAddressLine(),
                customer.getVillage(),
                customer.getPostOffice(),
                customer.getPoliceStation(),
                customer.getDistrict(),
                customer.getState(),
                customer.getPinCode(),
                customer.getAadhaarNumber(),
                customer.getPanNumber(),
                customer.getVoterIdNumber(),
                customer.getUserId(),
                customer.getAssignedStaffId(),
                customer.getStatus(),
                customer.getCreatedBy(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );

        return new CustomerDetailResponse(
                customer.getCustomerId(),
                customer.getCustomerId(),
                customer.getUserId(),
                customer.getFullName(),
                customer.getFullName(),
                customer.getPhoneNumber(),
                customer.getPhoneNumber(),
                customer.getAlternatePhoneNumber(),
                customer.getAlternatePhoneNumber(),
                customer.getVillage(),
                customer.getDistrict(),
                customer.getState(),
                customer.getAddressLine(),
                customer.getAddressLine(),
                customer.getPostOffice(),
                customer.getPoliceStation(),
                customer.getPinCode(),
                customer.getPinCode(),
                customer.getEmail(),
                customer.getGender(),
                customer.getDateOfBirth(),
                customer.getDateOfBirth(),
                customer.getGuardianName(),
                customer.getGuardianName(),
                "Unknown",
                customer.getAssignedStaffId(),
                customer.getAssignedStaffId(),
                assignedToName,
                customerStatus,
                finances.size(),
                productTypes,
                totalOutstanding,
                totalOverdue,
                aadhaarNumber,
                aadhaarMasked,
                panNumber,
                panMasked,
                voterIdNumber,
                voterMasked,
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                finances,
                List.of(), // followups empty until followups module
                customerInfo,
                null,
                List.of()
        );
    }

    private Customer.CustomerBuilder profileFields(CustomerProfileRequest request) {
        return Customer.builder()
                .fullName(request.getFullName())
                .guardianName(request.getGuardianName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .alternatePhoneNumber(request.getAlternatePhoneNumber())
                .email(request.getEmail())
                .addressLine(request.getAddressLine())
                .village(request.getVillage())
                .postOffice(request.getPostOffice())
                .policeStation(request.getPoliceStation())
                .district(request.getDistrict())
                .state(request.getState())
                .pinCode(request.getPinCode())
                .aadhaarNumber(request.getAadhaarNumber() != null && !request.getAadhaarNumber().isBlank() ? request.getAadhaarNumber().trim() : null)
                .panNumber(request.getPanNumber())
                .voterIdNumber(request.getVoterIdNumber());
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getCustomerId(),
                customer.getFullName(),
                customer.getPhoneNumber(),
                customer.getEmail(),
                customer.getAddressLine(),
                customer.getDistrict(),
                customer.getState(),
                customer.getPinCode(),
                customer.getAadhaarNumber(),
                customer.getAadhaarMasked(),
                customer.getAssignedStaffId(),
                customer.getStatus()
        );
    }
}
