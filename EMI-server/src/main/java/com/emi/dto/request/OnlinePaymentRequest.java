package com.emi.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlinePaymentRequest {
    private String userID;

    @NotNull(message = "Months is required")
    @Min(value = 1, message = "Months must be at least 1")
    private Integer months;

    @NotBlank(message = "financeId is required")
    @JsonAlias({"financeId", "finance_id", "financeID"})
    private String financeID;
}

