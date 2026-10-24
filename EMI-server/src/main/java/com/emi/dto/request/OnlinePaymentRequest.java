package com.emi.dto.request;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OnlinePaymentRequest {
    String userID;

    @NotNull(message = "Months is required")
    @Min(value = 1, message = "Months must be at least 1")
    Integer months;
    String financeID;
}
