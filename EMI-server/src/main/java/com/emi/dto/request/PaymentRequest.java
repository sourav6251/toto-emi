package com.emi.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

// Money received against a finance. txnId is required for every method except Cash (cheque number for cheques).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    // The FIN-YYYY-NNNNNN number or the internal id
    @NotBlank(message = "Finance is required")
    private String financeId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Payment date is required")
    @PastOrPresent(message = "Payment date cannot be in the future")
    private LocalDate date;

    @NotBlank(message = "Payment method is required")
    private String method;

    @Size(max = 100, message = "Transaction reference is too long")
    private String txnId;

    @Size(max = 150, message = "Reference is too long")
    private String reference;

    @Size(max = 500, message = "Remarks are too long")
    private String remarks;
}
