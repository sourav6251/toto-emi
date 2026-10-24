package com.emi.dto.response;

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
public class PaymentReceiptResponse {
    private String id;
    private String receiptNo;
    private String financeId;
    private String customerId;
    private String customerName;
    private BigDecimal amount;
    private LocalDate date;
    private String method;
    private String txnId;
    private String reference;
    private String collectedBy;
    private String collectedByName;
    private String status;
    private String verifiedBy;
    private String remarks;
    private OffsetDateTime createdAt;
    private List<Integer> appliedTo;
    private List<Allocation> allocation;
    private FinanceBrief finance;
    private CustomerBrief customer;
    private CompanyBrief company;

    // no is null for advance credit (money left after the last EMI)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Allocation {
        private Integer no;
        private BigDecimal amount;
        private LocalDate dueDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceBrief {
        private String id;
        private String product;
        private String vehicleNo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerBrief {
        private String name;
        private String mobile;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyBrief {
        private String name;
        private String address;
        private String phone;
        private String email;
        private String gstin;
    }
}
