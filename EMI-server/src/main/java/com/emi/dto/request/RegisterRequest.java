package com.emi.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class RegisterRequest {

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must be at most 150 characters")
        private String fullName;

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64, message = "Password must be 8 to 64 characters")
        private String password;
}
