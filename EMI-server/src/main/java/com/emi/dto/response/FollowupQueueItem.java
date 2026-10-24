package com.emi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowupQueueItem {
    private String financeId;
    private String customerId;
    private String customerName;
    private String mobile;
    private BigDecimal overdueAmount;
    private int dpd;
    private BigDecimal nextDue;
    private LocalDate lastDate;
    private String callStatus;
    private String response;
    private LocalDate promiseDate;
    private String promise;
    private LocalDate nextDate;
    private String staffName;
}
