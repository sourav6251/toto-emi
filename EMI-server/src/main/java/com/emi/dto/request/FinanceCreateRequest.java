package com.emi.dto.request;

import com.emi.utils.enums.InterestType;
import com.emi.utils.enums.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class FinanceCreateRequest {

    @NotBlank(message = "Customer is required")
    private String customerId;

    @NotNull(message = "Asset details are required")
    @Valid
    private AssetRequest asset;

    @NotNull(message = "Product price is required")
    @DecimalMin(value = "0.01", message = "Product price must be greater than zero")
    private BigDecimal productPrice;

    @NotNull(message = "Down payment is required")
    @DecimalMin(value = "0.00", message = "Down payment cannot be negative")
    private BigDecimal downPayment;

    // @NotNull(message = "Interest type is required")
    // private InterestType interestType;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.00", message = "Interest rate cannot be negative")
    @DecimalMax(value = "100.00", message = "Interest rate cannot exceed 100%")
    private BigDecimal interestRate;

    @NotNull(message = "Tenure is required")
    @Min(value = 3, message = "Tenure must be at least 3 months")
    @Max(value = 60, message = "Tenure cannot exceed 60 months")
    private Integer tenureMonths;

    @NotBlank(message = "DO number is required")
    @Size(max = 50, message = "DO number is too long")
    private String doNumber;

    @NotNull(message = "First EMI date is required")
    private LocalDate firstEmiDate;

    @DecimalMin(value = "0.00", message = "Processing fee cannot be negative")
    private BigDecimal processingFee;

    @DecimalMin(value = "0.00", message = "Other charges cannot be negative")
    private BigDecimal otherCharges;

    @Min(value = 0, message = "Grace days cannot be negative")
    private Integer graceDays;

    @DecimalMin(value = "0.00", message = "Late fee cannot be negative")
    private BigDecimal lateFeeAmount;

    @DecimalMin(value = "0.00", message = "Overdue percentage cannot be negative")
    @DecimalMax(value = "100.00", message = "Overdue percentage cannot exceed 100%")
    private BigDecimal overduePercentage;

    @Size(max = 500, message = "Remarks are too long")
    private String remarks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetRequest {
        // @NotNull(message = "Product type is required")
        // private ProductType productType;

        @NotBlank(message = "Product name is required")
        @Size(max = 150)
        private String productName;

        @Size(max = 100)
        private String brand;
        @Size(max = 100)
        private String model;
        @Size(max = 30)
        private String vehicleNumber;
        @Size(max = 50)
        private String chassisNumber;
        @Size(max = 50)
        private String engineNumber;
        @Size(max = 30)
        private String registrationNumber;
        @Size(max = 150)
        private String dealerName;
        @Size(max = 15)
        private String dealerContact;
        private LocalDate purchaseDate;
    }
}
