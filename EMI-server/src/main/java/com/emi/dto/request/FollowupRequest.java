package com.emi.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowupRequest {

    @NotBlank(message = "Finance is required")
    private String financeId;

    @Size(max = 30, message = "Call status is too long")
    private String callStatus;

    @NotBlank(message = "Response is required")
    @Size(max = 500, message = "Response is too long")
    private String response;

    @FutureOrPresent(message = "Promise date cannot be in the past")
    private LocalDate promiseDate;

    @NotNull(message = "Next follow-up date is required")
    private LocalDate nextDate;

    @Size(max = 500, message = "Remarks are too long")
    private String remarks;
}
