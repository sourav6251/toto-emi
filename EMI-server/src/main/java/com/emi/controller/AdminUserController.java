package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.request.CreateStaffRequest;
import com.emi.dto.response.ApiResponse;
import com.emi.dto.response.CustomerDetailResponse;
import com.emi.dto.response.ProfileResponse;
import com.emi.service.AdminUserService;
import com.emi.utils.enums.Role;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
//@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
@AllArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    public ResponseEntity<?> createCustomerCare(@Valid @RequestBody CreateStaffRequest request) {
        ProfileResponse user = adminUserService.createCustomerCare(request.getPhoneNumber(), request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", user));
    }

    @GetMapping("/customers/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getCustomerDetails(@CurrentUserId String adminId,
                                                @PathVariable String customerId) {
        CustomerDetailResponse details = adminUserService.getCustomerDetails(adminId, customerId);
        return ResponseEntity.ok(ApiResponse.success().add("data", details));
    }

    @GetMapping
    public ResponseEntity<?> getUsers(@RequestParam(required = false) Role role) {
        List<ProfileResponse> users = adminUserService.getUsers(role);
        return ResponseEntity.ok(ApiResponse.success().add("data", users));
    }

    // @PatchMapping("/{userId}/role")
    // public ResponseEntity<?> updateRole(@CurrentUserId String adminId,
    //                                     @PathVariable String userId,
    //                                     @RequestParam Role role) {
    //     ProfileResponse user = adminUserService.updateRole(adminId, userId, role);
    //     return ResponseEntity.ok(ApiResponse.success().add("data", user));
    // }
}
