package com.emi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

// One EMI joined with its finance and customer, for the collection worklist.
// status is derived: due | partial | overdue | upcoming | paid
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmiItem {
    private String key;
    private String financeId;
    private String customerId;
    private String customerName;
    private String mobile;
    private String productType;
    private String vehicleNo;
    private Integer no;
    private Integer tenure;
    private LocalDate dueDate;
    private BigDecimal amount;
    private BigDecimal paid;
    private LocalDate paidOn;
    private String status;
    private boolean overdue;
    private int daysLate;
    private String assignedToName;
}
