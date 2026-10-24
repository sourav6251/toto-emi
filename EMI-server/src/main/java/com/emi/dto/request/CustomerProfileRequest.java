package com.emi.dto.request;

import com.emi.utils.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfileRequest {

    @Size(max = 150)
    private String fullName;

    @Size(max = 150)
    private String guardianName;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private Gender gender;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Phone number must be a valid 10-digit Indian mobile number")
    private String phoneNumber;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Alternate phone must be a valid 10-digit Indian mobile number")
    private String alternatePhoneNumber;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String addressLine;

    @Size(max = 100)
    private String village;

    @Size(max = 100)
    private String postOffice;

    @Size(max = 100)
    private String policeStation;

    @Size(max = 100)
    private String district;

    @Size(max = 100)
    private String state;

    @Pattern(regexp = "^\\d{6}$", message = "PIN code must be 6 digits")
    private String pinCode;

    @Pattern(regexp = "^\\d{12}$", message = "Aadhaar must be exactly 12 digits")
    private String aadhaarNumber;

    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "PAN must look like ABCDE1234F")
    private String panNumber;

    @Size(max = 20)
    private String voterIdNumber;

    public static CustomerProfileRequest empty() {
        return CustomerProfileRequest.builder().build();
    }
}
