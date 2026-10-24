package com.emi.dto.response;

import com.emi.utils.enums.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {
    private String customerId;
    private String fullName;
    private String phoneNumber;
    private String email;
    private String addressLine;
    private String district;
    private String state;
    private String pinCode;
    private String aadhaarNumber;
    private String aadhaarMasked;
    private String assignedStaffId;
    private CustomerStatus status;
}
