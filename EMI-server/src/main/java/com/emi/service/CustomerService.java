package com.emi.service;

import com.emi.dto.request.CustomerProfileRequest;
import com.emi.dto.request.WalkInCustomerRequest;
import com.emi.dto.response.CustomerDetailResponse;
import com.emi.dto.response.CustomerListItem;
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
import com.emi.utils.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    // One borrower record per login; the phone number comes from the verified login, not the request
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

    // Staff enter a walk-in borrower who has no app login. assignToStaff is true when a Customer Care
    // user creates the record, so they own the collection; Admin-created records are left unassigned.
    @Transactional
    public CustomerResponse createWalkIn(String staffId, boolean assignToStaff, WalkInCustomerRequest request) {
        CustomerProfileRequest profile = request.getProfile() == null ? CustomerProfileRequest.empty() : request.getProfile();

        // Phone numbers aren't unique (families share one), so only block an exact repeat of phone + name
        if (profile.getFullName() != null
                && customerJpaRepository.existsByPhoneNumberAndFullName(request.getPhoneNumber(), profile.getFullName())) {
            throw new DomainException("A customer with this phone number and name already exists", HttpStatus.CONFLICT);
        }

        Customer customer = profileFields(profile)
                .phoneNumber(request.getPhoneNumber())
                .assignedStaffId(assignToStaff ? staffId : null)
                .status(CustomerStatus.ACTIVE)
                .createdBy(staffId)
                .build();

        return toResponse(customerJpaRepository.save(customer));
    }

    // Lightweight paginated list for staff. Care staff see their own and unassigned customers, Admin sees all.
    @Transactional(readOnly = true)
    public Page<CustomerListItem> listCustomers(String callerId, boolean isAdmin, String search, int page, int size) {
        String pattern = "%" + (search == null ? "" : search.trim().toLowerCase()) + "%";
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Customer> customers = customerJpaRepository.search(isAdmin, callerId, pattern, pageable);

        List<String> customerIds = customers.getContent().stream().map(Customer::getCustomerId).toList();
        Map<String, List<Finance>> financesByCustomer = customerIds.isEmpty() ? Map.of()
                : financeJpaRepository.findAllByCustomerIdIn(customerIds).stream()
                        .collect(Collectors.groupingBy(Finance::getCustomerId));

        List<String> financeIds = financesByCustomer.values().stream()
                .flatMap(List::stream).map(Finance::getFinanceId).toList();
        Map<String, List<EmiInstallment>> installmentsByFinance = financeIds.isEmpty() ? Map.of()
                : emiInstallmentJpaRepository.findAllByFinanceIdIn(financeIds).stream()
                        .collect(Collectors.groupingBy(EmiInstallment::getFinanceId));

        return customers.map(c -> {
            List<Finance> finances = financesByCustomer.getOrDefault(c.getCustomerId(), List.of());
            List<Finance> live = finances.stream().filter(f -> f.getStatus() != FinanceStatus.CANCELLED).toList();

            Integer tenure = live.stream()
                    .max(Comparator.comparing(Finance::getCreatedAt))
                    .map(Finance::getTenureMonths)
                    .orElse(null);

            BigDecimal outstanding = BigDecimal.ZERO;
            LocalDate nextDueDate = null;
            for (Finance f : live) {
                List<EmiInstallment> installments = installmentsByFinance.getOrDefault(f.getFinanceId(), List.of());
                BigDecimal paid = installments.stream()
                        .map(i -> i.getAmountPaid() != null ? i.getAmountPaid() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalPayable = f.getTotalPayable() != null ? f.getTotalPayable() : BigDecimal.ZERO;
                outstanding = outstanding.add(totalPayable.subtract(paid).max(BigDecimal.ZERO));

                if (f.getStatus() == FinanceStatus.ACTIVE) {
                    for (EmiInstallment i : installments) {
                        boolean unpaid = i.getStatus() == InstallmentStatus.PENDING
                                || i.getStatus() == InstallmentStatus.PARTIALLY_PAID;
                        if (unpaid && i.getDueDate() != null
                                && (nextDueDate == null || i.getDueDate().isBefore(nextDueDate))) {
                            nextDueDate = i.getDueDate();
                        }
                    }
                }
            }

            return new CustomerListItem(c.getCustomerId(), c.getFullName(), c.getPhoneNumber(), c.getStatus(),
                    finances.size(), tenure, outstanding, nextDueDate);
        });
    }

    // Customer 360° View: Profile, KYC, active & past loan accounts, EMI installment schedules, and collection status
    @Transactional(readOnly = true)
    public CustomerDetailResponse getCustomerDetail(String callerId, Authentication authentication, String customerId) {
        Customer customer = customerJpaRepository.findById(customerId)
                .or(() -> customerJpaRepository.findByUserId(customerId))
                .orElseThrow(() -> new DomainException("Customer not found", HttpStatus.NOT_FOUND));

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isCare = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER_CARE"));
        boolean isCustomer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));

        // Role-based access control
        if (isCustomer) {
            if (customer.getUserId() == null || !customer.getUserId().equals(callerId)) {
                throw new DomainException("Access denied", HttpStatus.FORBIDDEN);
            }
        } else if (isCare) {
            if (customer.getAssignedStaffId() != null && !customer.getAssignedStaffId().equals(callerId)) {
                throw new DomainException("Customer is not assigned to you", HttpStatus.FORBIDDEN);
            }
        }

        // Assigned staff details
        String assignedToName = "Unassigned";
        if (customer.getAssignedStaffId() != null) {
            assignedToName = userJpaRepository.findById(customer.getAssignedStaffId())
                    .map(Users::getFullName)
                    .orElse("Unassigned");
        }

        // User login info (if registered)
        Users loginUser = customer.getUserId() != null
                ? userJpaRepository.findById(customer.getUserId()).orElse(null)
                : null;
        CustomerDetailResponse.LoginInfo loginInfo = loginUser == null ? null : new CustomerDetailResponse.LoginInfo(
                loginUser.getUserID(),
                loginUser.getFullName(),
                loginUser.getPhoneNumber(),
                loginUser.getEmail(),
                loginUser.getRole(),
                loginUser.getStatus(),
                loginUser.getLastLoginAt(),
                loginUser.getCreatedAt(),
                loginUser.getUpdatedAt()
        );

        // Identity masking based on role (Aadhaar masked for all; PAN masked for Admin & Customer self; Voter ID for Admin only)
        String aadhaarMasked = customer.getAadhaarMasked();

        String panMasked = null;
        if (customer.getPanNumber() != null && (isAdmin || isCustomer)) {
            String pan = customer.getPanNumber().trim();
            panMasked = pan.length() >= 4 ? "XXXXX" + pan.substring(pan.length() - 4) : "XXXXX";
        }

        String voterMasked = null;
        if (customer.getVoterIdNumber() != null && isAdmin) {
            String voter = customer.getVoterIdNumber().trim();
            voterMasked = voter.length() >= 4 ? "XXXXXX" + voter.substring(voter.length() - 4) : "XXXXXX";
        }

        // Customer's finances & EMI schedule ledgers
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

            var summary = financeSummaryAssembler.assemble(f, customer, assignedToName, asset, installments, today);
            finances.add(summary.dto());
            if (!productTypes.contains(summary.dto().getProduct().getType())) {
                productTypes.add(summary.dto().getProduct().getType());
            }
            totalOutstanding = totalOutstanding.add(summary.outstanding());
            totalOverdue = totalOverdue.add(summary.overdueAmount());
        }

        CustomerStatus customerStatus = customer.getStatus() != null ? customer.getStatus() : CustomerStatus.ACTIVE;

        var customerInfo = new CustomerDetailResponse.CustomerInfo(
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
                "Father",
                customer.getAssignedStaffId(),
                customer.getAssignedStaffId(),
                assignedToName,
                customerStatus,
                finances.size(),
                productTypes,
                totalOutstanding,
                totalOverdue,
                aadhaarMasked,
                panMasked,
                voterMasked,
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                finances,
                List.of(), // followups empty until followups module
                customerInfo,
                loginInfo,
                List.of()
        );
    }

    // Profile fields shared by self-registration and staff entry; the caller sets phone, login and ownership
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
