package com.emi.dto.response;

import com.emi.utils.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private UUID userId;
    private String fullName;
    private String phoneNumber;
    private String email;
    private Role role;
    private String accessToken;
}
