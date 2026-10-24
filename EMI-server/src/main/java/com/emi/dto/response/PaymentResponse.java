package com.emi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

// status is pending | verified | rejected. appliedTo lists the EMI numbers the payment was applied to
// (empty until verified); advance credit is not an EMI so it isn't listed.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private String id;
    private String receiptNo;
    private String financeId;
    private String customerId;
    private String customerName;
    private String customerPhone;
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
}
