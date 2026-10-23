package com.emi.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Staff-entered borrower with no app login. Phone is required because the customer has no login to take it from.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalkInCustomerRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Phone number must be a valid 10-digit Indian mobile number")
    private String phoneNumber;

    @Valid
    private CustomerProfileRequest profile;
}
