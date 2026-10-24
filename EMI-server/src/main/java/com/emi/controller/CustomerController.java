package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.request.CustomerProfileRequest;
import com.emi.dto.request.WalkInCustomerRequest;
import com.emi.dto.response.ApiResponse;
import com.emi.dto.response.CustomerDetailResponse;
import com.emi.service.CustomerService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@AllArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> createWalkInCustomer(@Parameter(hidden = true) @CurrentUserId String staffId,
                                                  Authentication authentication,
                                                  @Valid @RequestBody CustomerProfileRequest request) {
        boolean isCustomerCare = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER_CARE"));
        var customer = customerService.createWalkIn(staffId, isCustomerCare, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", customer));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> listCustomers(@CurrentUserId String staffId,
                                           Authentication authentication,
                                           @RequestParam(value = "search", required = false) String search,
                                           @RequestParam(value = "page", defaultValue = "0") int page,
                                           @RequestParam(value = "size", defaultValue = "20") int size) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        var result = customerService.listCustomers(staffId, isAdmin, search, page, size);
        return ResponseEntity.ok(ApiResponse.success()
                .add("customers", result.getContent())
                .add("page", result.getNumber())
                .add("size", result.getSize())
                .add("totalElements", result.getTotalElements())
                .add("totalPages", result.getTotalPages()));
    }

    // Retrieve customer's All detail (profile, loans, EMI schedule, collection status)
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
    public ResponseEntity<?> getCustomerDetail(@CurrentUserId String currentUserId,
                                               Authentication authentication,
                                               @PathVariable("id") String customerId) {
        CustomerDetailResponse detail = customerService.getCustomerDetail(currentUserId, authentication, customerId);
        return ResponseEntity.ok(ApiResponse.success().add("data", detail));
    }

    // Called by a logged-in CUSTOMER before applying for finance
    // @PostMapping("/me")
    // @PreAuthorize("hasRole('CUSTOMER')")
    // public ResponseEntity<?> createMyProfile(@CurrentUserId String userId,
    //                                          @Valid @RequestBody CustomerProfileRequest request) {
    //     var customer = customerService.createMyProfile(userId, request);
    //     return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", customer));
    // }
}
