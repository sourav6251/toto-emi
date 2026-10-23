package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.request.WalkInCustomerRequest;
import com.emi.dto.response.ApiResponse;
import com.emi.service.CustomerService;
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

    // Staff enter a walk-in borrower who has no app login. Care staff take ownership of the record; Admin doesn't.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> createWalkInCustomer(@CurrentUserId String staffId,
                                                  Authentication authentication,
                                                  @Valid @RequestBody WalkInCustomerRequest request) {
        boolean isCustomerCare = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER_CARE"));
        var customer = customerService.createWalkIn(staffId, isCustomerCare, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", customer));
    }

    // Minimal paginated customer list (id, name, contact, loan tenure, outstanding, next due); search matches name or phone
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

    // Retrieve customer's 360° detail (profile, loans, EMI schedule, collection status)
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
    public ResponseEntity<?> getCustomerDetail(@CurrentUserId String currentUserId,
                                               Authentication authentication,
                                               @PathVariable("id") String customerId) {
        var detail = customerService.getCustomerDetail(currentUserId, authentication, customerId);
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
