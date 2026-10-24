package com.emi.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// Filters for the staff finance list: status is active, overdue, closed or cancelled; startDate/endDate
// (inclusive) bound when the loan account was created. All fields are optional.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceListRequest {
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
}
