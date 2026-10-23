package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.request.PaymentRequest;
import com.emi.dto.response.ApiResponse;
import com.emi.service.PaymentService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/payments")
@AllArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // Records money received. Admin payments are verified immediately; care payments wait for an admin.
    // Send an Idempotency-Key header so a double tap on "Record" can't create two receipts.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> recordPayment(@CurrentUserId String staffId,
                                           Authentication authentication,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                           @Valid @RequestBody PaymentRequest request) {
        boolean isAdmin = isAdmin(authentication);
        var payment = paymentService.record(staffId, isAdmin, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", payment));
    }

    // Payment history, newest first (capped at 200). status: pending, verified or rejected; mine=true limits to
    // payments the caller collected; from/to are an inclusive payment-date range (yyyy-MM-dd).
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> listPayments(@CurrentUserId String staffId,
                                          Authentication authentication,
                                          @RequestParam(value = "status", required = false) String status,
                                          @RequestParam(value = "mine", defaultValue = "false") boolean mine,
                                          @RequestParam(value = "from", required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(value = "to", required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var result = paymentService.list(staffId, isAdmin(authentication), status, mine, from, to);
        return ResponseEntity.ok(ApiResponse.success()
                .add("items", result.items())
                .add("total", result.total())
                .add("totalAmount", result.totalAmount())
                .add("pendingCount", result.pendingCount()));
    }

    // Receipt view: the payment, how it was applied to the EMIs, and the finance, customer and company
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
    public ResponseEntity<?> getPayment(@CurrentUserId String callerId,
                                        Authentication authentication,
                                        @PathVariable("id") String id) {
        var receipt = paymentService.getReceipt(callerId, authentication, id);
        return ResponseEntity.ok(ApiResponse.success().add("data", receipt));
    }

    // Admin approves (default) or rejects a pending payment: POST /payments/{id}/verify?approve=false
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> verifyPayment(@CurrentUserId String adminId,
                                           @PathVariable("id") String id,
                                           @RequestParam(value = "approve", defaultValue = "true") boolean approve) {
        var payment = paymentService.verify(adminId, id, approve);
        return ResponseEntity.ok(ApiResponse.success().add("data", payment));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
