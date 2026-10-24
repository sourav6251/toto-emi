package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.annotation.CurrentUserRole;
import com.emi.dto.request.AttachDocumentRequest;
import com.emi.dto.request.FinanceCreateRequest;
import com.emi.dto.request.FinanceListRequest;
import com.emi.service.DocumentService;
import com.emi.dto.response.ApiResponse;
import com.emi.service.FinanceService;
import com.emi.utils.enums.Role;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/finances")
@AllArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final DocumentService documentService;

    // Loan list, newest first, unfiltered.
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> listFinances(
                                          @RequestBody FinanceListRequest request) {
//        boolean isAdmin = role.name().equals("ROLE_ADMIN");
        var result = financeService.listFinances(
                request.getStatus(), request.getStartDate(), request.getEndDate());
        return ResponseEntity.ok(ApiResponse.success()
                .add("items", result.items())
                .add("total", result.total()));
    }
    // Same list, filtered. status: active, overdue, closed or cancelled; startDate/endDate is an inclusive
    // range on when the loan account was created. All fields in the body are optional.
//    @PostMapping("/search")
//    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
//    public ResponseEntity<?> searchFinances(@CurrentUserId String staffId,
//                                            @RequestBody FinanceListRequest request) {
////        boolean isAdmin = role.name().equals("ROLE_ADMIN");
//        var result = financeService.listFinances(
//                request.getStatus(), request.getStartDate(), request.getEndDate());
//        return ResponseEntity.ok(ApiResponse.success()
//                .add("items", result.items())
//                .add("total", result.total()));
//    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
    public ResponseEntity<?> getFinanceDetail(@CurrentUserId String callerId,
                                              Authentication authentication,
                                              @PathVariable("id") String id) {
        var detail = financeService.getFinanceDetail(callerId, authentication, id);
        return ResponseEntity.ok(ApiResponse.success().add("data", detail));
    }

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
