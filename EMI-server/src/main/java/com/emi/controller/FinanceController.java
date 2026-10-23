package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.request.AttachDocumentRequest;
import com.emi.dto.request.FinanceCreateRequest;
import com.emi.service.DocumentService;
import com.emi.dto.response.ApiResponse;
import com.emi.service.FinanceService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/finances")
@AllArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final DocumentService documentService;

    // Loan list, newest first. status: active, overdue, closed or cancelled; productType: TOTO, BIKE, SOLAR or OTHER;
    // q matches finance number, customer name, mobile, DO number or vehicle number
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> listFinances(@CurrentUserId String staffId,
                                          Authentication authentication,
                                          @RequestParam(value = "q", required = false) String q,
                                          @RequestParam(value = "status", required = false) String status,
                                          @RequestParam(value = "productType", required = false) String productType) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        var result = financeService.listFinances(staffId, isAdmin, q, status, productType);
        return ResponseEntity.ok(ApiResponse.success()
                .add("items", result.items())
                .add("total", result.total()));
    }

    // Full loan account view: terms, EMI schedule, documents and payments. id is the FIN-YYYY-NNNNNN number.
    // Customers can only open their own; Admin-only fields are null for everyone else.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
    public ResponseEntity<?> getFinanceDetail(@CurrentUserId String callerId,
                                              Authentication authentication,
                                              @PathVariable("id") String id) {
        var detail = financeService.getFinanceDetail(callerId, authentication, id);
        return ResponseEntity.ok(ApiResponse.success().add("data", detail));
    }

    // Adds a document to a finance, or replaces its photo, using a file already uploaded with POST /documents.
    // Admin uploads are Verified, staff uploads Pending. Returns the updated loan account view.
    @PostMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> attachDocument(@CurrentUserId String staffId,
                                            Authentication authentication,
                                            @PathVariable("id") String id,
                                            @Valid @RequestBody AttachDocumentRequest request) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        documentService.attach(staffId, isAdmin, id, request);
        var detail = financeService.getFinanceDetail(staffId, authentication, id);
        return ResponseEntity.ok(ApiResponse.success().add("data", detail));
    }

    // Creates the finance (status PENDING_APPROVAL) with its asset and the full EMI schedule
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> createFinance(@CurrentUserId String staffId,
                                           Authentication authentication,
                                           @Valid @RequestBody FinanceCreateRequest request) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        var finance = financeService.createFinance(staffId, isAdmin, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", finance));
    }
}
