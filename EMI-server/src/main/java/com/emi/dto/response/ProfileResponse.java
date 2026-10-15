package com.emi.dto.response;

import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;

public record ProfileResponse(
        String userId,
        String fullName,
        String phoneNumber,
        String email,
        Role role,
        UserStatus status
) {
}
