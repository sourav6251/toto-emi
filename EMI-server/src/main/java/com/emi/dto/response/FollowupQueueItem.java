package com.emi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

// One finance in the calling queue. The last-call fields (lastDate .. staffName) are null in the uncalled list.
// promise is kept | broken | open, or null when no promise date was given.
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
