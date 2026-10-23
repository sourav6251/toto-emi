package com.emi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// A logged call or visit. promiseDate is "" when the customer promised nothing.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowupResponse {
    private String id;
    private String customerId;
    private String financeId;
    private LocalDate date;
    private String callStatus;
    private String response;
    private String promiseDate;
    private LocalDate nextDate;
    private String remarks;
    private String staffId;
    private String staffName;
}
