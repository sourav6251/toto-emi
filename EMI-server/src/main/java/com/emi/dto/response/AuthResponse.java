package com.emi.dto.response;

import com.emi.utils.enums.Role;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String fullName,
        String phoneNumber,
        String email,
        Role role,
        String accessToken
) {
}
