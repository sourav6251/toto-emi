package com.emi.dto.response;

import com.emi.utils.enums.InterestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

// Full loan account view: everything in the finance list item plus asset, terms, schedule, documents and payments.
// Admin-only fields (rate, interestType, totalInterest, processingFee, otherCharges, schedule principal/interest)
// are null for everyone else.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceDetailResponse {
    private String id;
    private String financeId;
    private String financeNumber;
    private String customerId;
    private String customerName;
    private String mobile;
    private String assignedToName;
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
    private ProductDetail product;
    private BigDecimal price;
    private BigDecimal downPayment;
    private BigDecimal totalPayable;
    private BigDecimal rate;
    private InterestType interestType;
    private BigDecimal totalInterest;
    private BigDecimal processingFee;
    private BigDecimal otherCharges;
    private LocalDate firstEmiDate;
    private Integer dueDay;
    private Integer graceDays;
    private BigDecimal lateFee;
    private BigDecimal lateFees;
    private BigDecimal overduePercentage;
    private String cancelReason;
    private String createdByName;
    private CustomerBrief customer;
    private List<ScheduleRow> schedule;
    private List<PaymentResponse> payments;
    private List<DocumentResponse> documents;
    private boolean canUploadDocs;
    private List<Object> followups;
    private String upi;
    private String companyName;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductDetail {
        private String type;
        private String name;
        private String brand;
        private String model;
        private String vehicleNo;
        private String regNo;
        private BigDecimal price;
        private String dealer;
        private String dealerContact;
        private LocalDate purchaseDate;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerBrief {
        private String id;
        private String name;
        private String mobile;
        private String altMobile;
        private String village;
        private String district;
        private String aadhaarMasked;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleRow {
        private Integer no;
        private LocalDate dueDate;
        private BigDecimal amount;
        private BigDecimal principal;
        private BigDecimal interest;
        private BigDecimal paid;
        private LocalDate paidOn;
        private String status;
        private boolean overdue;
        private int daysLate;
        private boolean paidLate;
    }
}
