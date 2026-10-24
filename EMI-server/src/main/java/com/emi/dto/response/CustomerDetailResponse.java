package com.emi.dto.response;

import com.emi.utils.enums.CustomerStatus;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.Gender;
import com.emi.utils.enums.InstallmentStatus;
import com.emi.utils.enums.InterestType;
import com.emi.utils.enums.ProductType;
import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailResponse {
    private String id;
    private String customerId;
    private String name;
    private String fullName;
    private String mobile;
    private String phoneNumber;
    private String altMobile;
    private String alternatePhoneNumber;
    private String village;
    private String district;
    private String state;
    private String address;
    private String addressLine;
    private String postOffice;
    private String policeStation;
    private String pin;
    private String pinCode;
    private String email;
    private Gender gender;
    private LocalDate dob;
    private LocalDate dateOfBirth;
    private String guardian;
    private String guardianName;
    private String guardianRelation;
    private String assignedTo;
    private String assignedStaffId;
    private String assignedToName;
    private CustomerStatus status;
    private int financeCount;
    private List<String> productTypes;
    private BigDecimal outstanding;
    private BigDecimal overdueAmount;
    private String aadhaarMasked;
    private String panMasked;
    private String voterMasked;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<FinanceSummaryDto> finances;
    private List<FollowupDto> followups;
    private CustomerInfo customer;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LoginInfo login;
    private List<FinanceInfo> rawFinances;

    // Overloaded constructor for legacy 3-argument calls (e.g. from AdminUserService)
    public CustomerDetailResponse(CustomerInfo customer, LoginInfo login, List<FinanceInfo> rawFinances) {
        this.id = customer != null ? customer.getCustomerId() : null;
        this.customerId = customer != null ? customer.getCustomerId() : null;
        this.name = customer != null ? customer.getFullName() : null;
        this.fullName = customer != null ? customer.getFullName() : null;
        this.mobile = customer != null ? customer.getPhoneNumber() : null;
        this.phoneNumber = customer != null ? customer.getPhoneNumber() : null;
        this.altMobile = customer != null ? customer.getAlternatePhoneNumber() : null;
        this.alternatePhoneNumber = customer != null ? customer.getAlternatePhoneNumber() : null;
        this.village = customer != null ? customer.getVillage() : null;
        this.district = customer != null ? customer.getDistrict() : null;
        this.state = customer != null ? customer.getState() : null;
        this.address = customer != null ? customer.getAddressLine() : null;
        this.addressLine = customer != null ? customer.getAddressLine() : null;
        this.postOffice = customer != null ? customer.getPostOffice() : null;
        this.policeStation = customer != null ? customer.getPoliceStation() : null;
        this.pin = customer != null ? customer.getPinCode() : null;
        this.pinCode = customer != null ? customer.getPinCode() : null;
        this.email = customer != null ? customer.getEmail() : null;
        this.gender = customer != null ? customer.getGender() : null;
        this.dob = customer != null ? customer.getDateOfBirth() : null;
        this.dateOfBirth = customer != null ? customer.getDateOfBirth() : null;
        this.guardian = customer != null ? customer.getGuardianName() : null;
        this.guardianName = customer != null ? customer.getGuardianName() : null;
        this.guardianRelation = "Father";
        this.assignedTo = customer != null ? customer.getAssignedStaffId() : null;
        this.assignedStaffId = customer != null ? customer.getAssignedStaffId() : null;
        this.assignedToName = "Unassigned";
        this.status = customer != null ? customer.getStatus() : null;
        this.financeCount = rawFinances != null ? rawFinances.size() : 0;
        this.productTypes = List.of();
        this.outstanding = BigDecimal.ZERO;
        this.overdueAmount = BigDecimal.ZERO;
        this.aadhaarMasked = (customer != null && customer.getAadhaarNumber() != null && !customer.getAadhaarNumber().isBlank())
                ? (customer.getAadhaarNumber().length() <= 4 ? customer.getAadhaarNumber() : "XXXX XXXX " + customer.getAadhaarNumber().substring(customer.getAadhaarNumber().length() - 4))
                : null;
        this.panMasked = null;
        this.voterMasked = null;
        this.createdAt = customer != null ? customer.getCreatedAt() : null;
        this.updatedAt = customer != null ? customer.getUpdatedAt() : null;
        this.finances = List.of();
        this.followups = List.of();
        this.customer = customer;
        this.login = login;
        this.rawFinances = rawFinances;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSummaryDto {
        private String type;
        private String brand;
        private String model;
        private String vehicleNo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceSummaryDto {
        private String id;
        private String financeId;
        private String financeNumber;
        private String customerId;
        private String customerName;
        private String mobile;
        private String assignedToName;
        private ProductSummaryDto product;
        private String doNumber;
        private BigDecimal principal;
        private BigDecimal emiAmount;
        private Integer tenure;
        private OffsetDateTime createdAt;
        private String status;
        private String worst;
        private BigDecimal paidTotal;
        private Integer paidCount;
        private BigDecimal outstanding;
        private BigDecimal overdueAmount;
        private Integer overdueCount;
        private Integer dpd;
        private BigDecimal nextDue;
        private LocalDate nextDueDate;
        private List<String> ribbon;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowupDto {
        private String id;
        private String customerId;
        private String financeId;
        private LocalDate date;
        private String callStatus;
        private String response;
        private LocalDate promiseDate;
        private LocalDate nextDate;
        private String remarks;
        private String staffId;
        private String staffName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        private String customerId;
        private String fullName;
        private String guardianName;
        private LocalDate dateOfBirth;
        private Gender gender;
        private String phoneNumber;
        private String alternatePhoneNumber;
        private String email;
        private String addressLine;
        private String village;
        private String postOffice;
        private String policeStation;
        private String district;
        private String state;
        private String pinCode;
        private String aadhaarNumber;
        private String panNumber;
        private String voterIdNumber;
        private String userId;
        private String assignedStaffId;
        private CustomerStatus status;
        private String createdBy;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginInfo {
        private String userId;
        private String fullName;
        private String phoneNumber;
        private String email;
        private Role role;
        private UserStatus status;
        private OffsetDateTime lastLoginAt;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceInfo {
        private String financeId;
        private String financeNumber;
        private String doNumber;
        private BigDecimal productPrice;
        private BigDecimal downPayment;
        private BigDecimal financeAmount;
        private InterestType interestType;
        private BigDecimal interestRate;
        private Integer tenureMonths;
        private BigDecimal emiAmount;
        private BigDecimal totalInterest;
        private BigDecimal processingFee;
        private BigDecimal otherCharges;
        private BigDecimal totalPayable;
        private LocalDate firstEmiDate;
        private Integer emiDueDay;
        private Integer graceDays;
        private BigDecimal lateFeeAmount;
        private FinanceStatus status;
        private String remarks;
        private String createdBy;
        private String approvedBy;
        private OffsetDateTime approvedAt;
        private OffsetDateTime closedAt;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private AssetInfo asset;
        private List<InstallmentInfo> installments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetInfo {
        private String assetId;
        private ProductType productType;
        private String productName;
        private String brand;
        private String model;
        private String vehicleNumber;
        private String registrationNumber;
        private BigDecimal purchasePrice;
        private String dealerName;
        private String dealerContact;
        private LocalDate purchaseDate;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstallmentInfo {
        private String installmentId;
        private Integer installmentNo;
        private LocalDate dueDate;
        private BigDecimal principalAmount;
        private BigDecimal interestAmount;
        private BigDecimal emiAmount;
        private BigDecimal amountPaid;
        private BigDecimal lateFee;
        private BigDecimal lateFeePaid;
        private InstallmentStatus status;
        private LocalDate paidAt;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
    }
}
