package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.annotation.CurrentUserRole;
import com.emi.dto.request.CustomerDocumentBatchRequest;
import com.emi.dto.request.CustomerProfileRequest;
import com.emi.dto.request.FinanceCreateRequest;
import com.emi.dto.request.FinanceListRequest;
import com.emi.dto.request.UpdateDocumentFileRequest;
import com.emi.dto.response.ApiResponse;
import com.emi.dto.response.CustomerDetailResponse.FinanceInfo;
import com.emi.dto.response.CustomerDocumentResponse;
import com.emi.dto.response.CustomerResponse;
import com.emi.dto.response.FinanceDetailResponse;
import com.emi.service.CustomerService;
import com.emi.service.DocumentService;
import com.emi.service.FinanceService;
import com.emi.utils.enums.Role;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/finances")
@AllArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final DocumentService documentService;
    private final CustomerService customerService;

    // Loan list, newest first, unfiltered.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> listFinances(@RequestBody FinanceListRequest request) {
    FinanceService.FinanceList result = financeService.listFinances(
                request.getStatus(), request.getStartDate(), request.getEndDate());
        return ResponseEntity.ok(ApiResponse.success()
                .add("items", result.items())
                .add("total", result.total()));
    }

    // Logged-in customer's own loans (summary only, no EMI schedule), newest first.
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> listMyFinances(@CurrentUserId String userId) {
        FinanceService.FinanceList result = financeService.listCustomerFinances(userId);
        return ResponseEntity.ok(ApiResponse.success()
                .add("items", result.items())
                .add("total", result.total()));
    }

    /**
     *
     * @param callerId
     * @param authentication
     * @param id finance id
     * @return
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
    public ResponseEntity<?> getFinanceDetail(@CurrentUserId String callerId,
                                              Authentication authentication,
                                              @PathVariable("id") String id) {
        FinanceDetailResponse detail = financeService.getFinanceDetail(callerId, authentication, id);
        return ResponseEntity.ok(ApiResponse.success().add("data", detail));
    }

    // --- 3-STAGE FINANCE ONBOARDING WORKFLOW ---

    // Stage 1: Accept full customer profile details, create/update users & customers tables, return customerId
    @PostMapping("/stage1-profile")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> stage1Profile(@CurrentUserId String staffId,
                                           @CurrentUserRole Role role,
                                           @Valid @RequestBody CustomerProfileRequest request) {
        boolean isCustomerCare = role == Role.CUSTOMER_CARE;
        CustomerResponse customer = customerService.createOrUpdateWalkIn(staffId, isCustomerCare, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", customer));
    }

    // Stage 2: Upload and attach raw document files for this customer. Uploads run in the background
    // (can be several large files); poll stage2GetDocuments below to see them land.
    @PostMapping(value = "/stage2-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> stage2AttachDocuments(@CurrentUserId String staffId,
                                                   @CurrentUserRole Role role,
                                                   @Valid @ModelAttribute CustomerDocumentBatchRequest request) {
        boolean isAdmin = role == Role.ADMIN;
        DocumentService.PreparedDocumentBatch batch = documentService.prepareBatchForCustomer(request);
        documentService.attachBatchToCustomerAsync(staffId, isAdmin, batch.customerId(), batch.documents());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success().add("message", "Document upload started"));
    }

    // Stage 2 (Lookup): View all uploaded documents staged for this customer
    @GetMapping("/{customerId}/stage2-documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> stage2GetDocuments(@PathVariable("customerId") String customerId) {
        List<CustomerDocumentResponse> docs = documentService.getCustomerDocuments(customerId);
        return ResponseEntity.ok(ApiResponse.success().add("data", docs));
    }

    // Stage 3: Accept asset and loan amounts, calculate EMI schedule, save finance and full EMI installment list
    @PostMapping("/stage3-amounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> stage3Amounts(@CurrentUserId String staffId,
                                           @CurrentUserRole Role role,
                                           @Valid @RequestBody FinanceCreateRequest request) {

        FinanceInfo finance = financeService.createFinance(staffId, role == Role.ADMIN, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", finance));
    }

    // Edit a finance that is still waiting for admin approval: same payload as stage 3, EMI schedule rebuilt
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> updateFinance(@CurrentUserId String staffId,
                                           @PathVariable("id") String id,
                                           @Valid @RequestBody FinanceCreateRequest request) {
        FinanceInfo finance = financeService.updateFinance(staffId, id, request);
        return ResponseEntity.ok(ApiResponse.success().add("data", finance));
    }

    // Admin approves a pending finance and adds its DO number; the finance becomes active.
    // DO numbers may contain "/", so it is a query param, not a path variable.
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveFinance(@CurrentUserId String adminId,
                                            @PathVariable("id") String id,
                                            @RequestParam("doNumber") String doNumber) {
        FinanceInfo finance = financeService.approveFinance(adminId, id, doNumber);
        return ResponseEntity.ok(ApiResponse.success().add("data", finance));
    }

    // Update/replace an already uploaded document:
    // Works either with customerId (before finance is created) or financeId (after finance is created / settled)
    // Accepts raw file directly via multipart/form-data
    @PutMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> updateDocument(@CurrentUserId String staffId,
                                            @CurrentUserRole Role role,
                                            @PathVariable("id") String id,
                                            @Valid @ModelAttribute UpdateDocumentFileRequest request) {
        boolean isAdmin = role == Role.ADMIN;
        Object result = documentService.updateDocumentWithFile(staffId, isAdmin, id,
                request.getFile(), request.getKey(), request.getNumber());
        return ResponseEntity.ok(ApiResponse.success().add("data", result));
    }
}
