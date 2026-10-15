package com.emi.dto.response;

import com.emi.utils.enums.UserStatus;

import java.util.UUID;

public record RegisterResponse(
        String userId,
        String phoneNumber,
        String role,
        UserStatus status,
        String message
) {
}
