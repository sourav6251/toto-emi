package com.emi.dto.response;

import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private String userId;
    private String fullName;
    private String phoneNumber;
    private String email;
    private Role role;
    private UserStatus status;
}
