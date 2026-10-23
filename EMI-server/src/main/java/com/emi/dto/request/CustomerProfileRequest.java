package com.emi.dto.request;

import com.emi.utils.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
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
public class CustomerProfileRequest {

    @Size(max = 150)
    private String fullName;

    @Size(max = 150)
    private String guardianName;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private Gender gender;

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

    // Full 12-digit Aadhaar number
    @Pattern(regexp = "^\\d{12}$", message = "Aadhaar must be exactly 12 digits")
    private String aadhaarNumber;

    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "PAN must look like ABCDE1234F")
    private String panNumber;

    @Size(max = 20)
    private String voterIdNumber;

    // Used when a borrower record is created at registration, before any profile details are entered
    public static CustomerProfileRequest empty() {
        return CustomerProfileRequest.builder().build();
    }
}
