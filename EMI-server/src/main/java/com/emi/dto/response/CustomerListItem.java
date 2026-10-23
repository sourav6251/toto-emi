package com.emi.dto.response;

import com.emi.utils.enums.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

// Minimal row for the customer list; tenureMonths is from the latest non-cancelled finance
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerListItem {
    private String customerId;
    private String fullName;
    private String phoneNumber;
    private CustomerStatus status;
    private int financeCount;
    private Integer tenureMonths;
    private BigDecimal outstanding;
    private LocalDate nextDueDate;
}
