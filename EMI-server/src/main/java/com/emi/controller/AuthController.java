package com.emi.controller;


import com.emi.annotation.CurrentUserId;
import com.emi.annotation.TokenSubject;
import com.emi.config.JWTService;
import com.emi.dto.request.*;
import com.emi.dto.response.ApiResponse;
import com.emi.dto.response.AuthResponse;
import com.emi.dto.response.MessageResponse;
import com.emi.dto.response.RegisterResponse;
import com.emi.model.entity.Users;
import com.emi.service.AuthService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JWTService jwtService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginRequest request) {
        Users user = authService.login(request);
        String token = jwtService.generateToken(user.getUserID(), user.getRole().name());
        ResponseCookie cookie = ResponseCookie.from("AccessToken", token)
                .httpOnly(true)
                .secure(true)
                .maxAge(3600)
                .sameSite("Strict")
                .path("/")
                .build();

        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getUserID());
        userData.put("fullName", user.getFullName());
        userData.put("phoneNumber", user.getPhoneNumber());
        userData.put("email", user.getEmail());
        userData.put("role", user.getRole());
        userData.put("status", user.getStatus());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success()
                        .add("data", userData)
                        .add("accessToken", token));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Parameter(hidden = true) @TokenSubject("registerToken") String id, @Valid @RequestBody RegisterRequest request) {
        RegisterResponse response=authService.register(id,request);
        String token=jwtService.generateToken(response.getUserId(), response.getRole());
        ResponseCookie cookie = ResponseCookie.from("AccessToken", token)
                .httpOnly(true)
                .secure(true)
                .maxAge(3600)
                .sameSite("Strict")
                .path("/")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data",response).add("accessToken",token));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        String response = authService.verifyOtp(request);
        String token = jwtService.generateToken(response, 3600000);
        ResponseCookie cookie = ResponseCookie.from("registerToken", token)
                .httpOnly(true)
                .secure(true)
                .maxAge(3600)
                .sameSite("Strict")
                .path("/")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success()
                        .add("message", "OTP verified successfully")
                        .add("registerToken", token));
    }

    @PostMapping("/otp/send")
    public ResponseEntity<MessageResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return ResponseEntity.ok(authService.resendOtp(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@Parameter(hidden = true) @TokenSubject(value = "registerToken",message = "Something went wrong, Try again") String subject, @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(subject,request);
        return ResponseEntity.ok(ApiResponse.success().add("message", "Password update successfully"));
    }

//    @PatchMapping("/otp/verify")
//    public ResponseEntity<?> verifyOTP(){
//
//    }
}
