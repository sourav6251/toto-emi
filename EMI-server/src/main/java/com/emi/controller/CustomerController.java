package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.annotation.CurrentUserRole;
import com.emi.dto.request.CustomerProfileRequest;
import com.emi.dto.request.WalkInCustomerRequest;
import com.emi.dto.response.ApiResponse;
import com.emi.dto.response.CustomerDetailResponse;
import com.emi.dto.response.CustomerResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Users;
import com.emi.service.CustomerService;
import com.emi.utils.enums.Role;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/customers")
@AllArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> createWalkInCustomer(@Parameter(hidden = true) @CurrentUserId String staffId,
                                                  @CurrentUserRole Role role,
                                                  @Valid @RequestBody CustomerProfileRequest request) {
        boolean isCustomerCare = role.name().equals("CUSTOMER_CARE");
        CustomerResponse customer = customerService.createWalkIn(staffId, isCustomerCare, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", customer));
    }

    // Search customers by partial/full mobile number (minimum 3 digits) or name
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> searchCustomerByMobileNo(
            @RequestParam(value = "mobileNo", required = true) String mobileNo) {
        List<Users> customers = customerService.searchCustomerByMobileNo(mobileNo);
        return ResponseEntity.ok(ApiResponse.success().add("data", customers));
    }

    // Retrieve customer's All detail (profile, loans, EMI schedule, collection status)
    @GetMapping("/{id}")
    public ResponseEntity<?> getCustomerDetail(@CurrentUserId String userId, @CurrentUserRole Role role, @PathVariable("id") String requestUserID) {
        if (role == Role.CUSTOMER) {
            if (!requestUserID.equals(userId)) {
                throw new DomainException("You don't have permission to access this user data", HttpStatus.FORBIDDEN);
            }
        }
        CustomerDetailResponse detail = customerService.getCustomerDetail(requestUserID);
        return ResponseEntity.ok(ApiResponse.success().add("data", detail));
    }

}
