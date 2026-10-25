package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.request.OnlinePaymentRequest;
import com.emi.dto.request.PaymentFilterRequest;
import com.emi.dto.request.PaymentRequest;
import com.emi.dto.response.ApiResponse;
import com.emi.dto.response.PaymentOrderResponse;
import com.emi.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Payment Management", description = "APIs for loan payments, manual collections, online Cashfree checkout, AutoPay mandates, and verification")
@RestController
@RequestMapping("/payments")
@AllArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Record manual / offline payment",
            description = "Allows ADMIN or CUSTOMER_CARE staff to record money received (Cash, Bank Transfer, Cheque, UPI). Admin payments are verified and applied to EMI installments immediately; Customer Care collections wait for Admin verification."
    )
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> recordPayment(@Parameter(hidden = true) @CurrentUserId String staffId,
                                           @Parameter(hidden = true) Authentication authentication,
                                           @Valid @RequestBody PaymentRequest request) {
        boolean isAdmin = isAdmin(authentication);
        var payment = paymentService.record(staffId, isAdmin, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", payment));
    }

    @Operation(
            summary = "List and search payments",
            description = "Allows ADMIN or CUSTOMER_CARE staff to search and retrieve payments filtered by status (PENDING, VERIFIED, REJECTED) and payment date range (from/to). Returns items, total matching count, total amount collected, and pending verification count."
    )
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> listPayments(@RequestBody(required = false) PaymentFilterRequest request) {
        String status = request != null ? request.getStatus() : null;
        LocalDate from = request != null ? request.getFrom() : null;
        LocalDate to = request != null ? request.getTo() : null;
        var result = paymentService.list(status, from, to);
        return ResponseEntity.ok(ApiResponse.success()
                .add("items", result.items())
                .add("total", result.total())
                .add("totalAmount", result.totalAmount())
                .add("pendingCount", result.pendingCount()));
    }

    @Operation(
            summary = "Initiate online EMI payment (Cashfree PG)",
            description = "Allows a CUSTOMER to pay one or more pending EMI months online. Calculates total due including any overdue late fees and generates a Cashfree payment order session for mobile/web checkout (UPI QR, NetBanking, Cards)."
    )
    @PostMapping("/pay")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> payEmi(@Parameter(hidden = true) @CurrentUserId String userID,
                                    @RequestBody OnlinePaymentRequest paymentRequest){

        paymentRequest.setUserID(userID);
        PaymentOrderResponse paymentOrderResponse=paymentService.payEmi(paymentRequest);

        return ResponseEntity.ok(ApiResponse.success().add("data",paymentOrderResponse));
    }

    @Operation(
            summary = "Verify online payment after Cashfree checkout",
            description = "Called by the CUSTOMER after completing payment in the Cashfree PG sheet. Verifies payment status directly with Cashfree server-to-server. If PAID, creates verified payment record and credits the installments immediately."
    )
    @PutMapping("/verify/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> verifyOnlinePayment(@Parameter(hidden = true) @CurrentUserId String userID,
                                                 @Parameter(description = "Merchant order ID generated during /payments/pay")
                                                 @PathVariable("orderId") String orderId){
        var payment = paymentService.verifyEmi(userID, orderId);
        return ResponseEntity.ok(ApiResponse.success().add("data", payment));
    }

    @Operation(
            summary = "Set up AutoPay mandate (UPI AutoPay / eNACH)",
            description = "Allows a CUSTOMER to initialize a recurring AutoPay mandate with Cashfree. Schedules monthly debit on the customer's EMI due date with a 2x cap to cover future late fees. Returns a subscription session ID for the app to open the bank authentication sheet."
    )
    @PostMapping("/autopay/setup/{financeId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> setupAutoPay(@Parameter(hidden = true) @CurrentUserId String userId,
                                          @Parameter(description = "Finance ID or Loan Number (e.g. FIN-2026-000045)")
                                          @PathVariable("financeId") String financeId,
                                          @Parameter(description = "Optional redirect URL after completing mandate authorization in browser")
                                          @RequestParam(value = "returnUrl", required = false) String returnUrl) {
        var response = paymentService.setupAutoPay(userId, financeId, returnUrl);
        return ResponseEntity.ok(ApiResponse.success().add("data", response));
    }

    @Operation(
            summary = "Cancel AutoPay mandate manually",
            description = "Allows a CUSTOMER to cancel their recurring AutoPay mandate. Immediately revokes the standing debit instruction with Cashfree and the customer's bank (via NPCI), preventing all future automatic debits."
    )
    @PostMapping("/autopay/cancel/{financeId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> cancelAutoPay(@Parameter(hidden = true) @CurrentUserId String userId,
                                           @Parameter(description = "Finance ID or Loan Number (e.g. FIN-2026-000045)")
                                           @PathVariable("financeId") String financeId) {
        paymentService.cancelAutoPay(userId, financeId);
        return ResponseEntity.ok(ApiResponse.success().add("message", "AutoPay cancelled successfully"));
    }

    @Operation(
            summary = "Get payment receipt and installment breakdown",
            description = "Retrieves full receipt details for a payment ID, including which EMI installment numbers the payment was applied to, advance credit balance, customer details, and loan summary."
    )
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')")
    public ResponseEntity<?> getPayment(@Parameter(hidden = true) @CurrentUserId String callerId,
                                        @Parameter(hidden = true) Authentication authentication,
                                        @Parameter(description = "Payment ID or Payment Number (e.g. PAY-2026-000123)")
                                        @PathVariable("id") String id) {
        var receipt = paymentService.getReceipt(callerId, authentication, id);
        return ResponseEntity.ok(ApiResponse.success().add("data", receipt));
    }

    @Operation(
            summary = "Approve or reject a pending offline payment",
            description = "Allows an ADMIN to verify (approve=true) or reject (approve=false) a pending payment recorded by Customer Care staff. Approving marks the payment as VERIFIED and credits the loan EMIs; rejecting leaves the EMIs unpaid."
    )
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> verifyPayment(@Parameter(hidden = true) @CurrentUserId String adminId,
                                           @Parameter(description = "Payment ID or Payment Number (e.g. PAY-2026-000123)")
                                           @PathVariable("id") String id,
                                           @Parameter(description = "Set true to approve and credit EMIs, false to reject")
                                           @RequestParam(value = "approve", defaultValue = "true") boolean approve) {
        var payment = paymentService.verify(adminId, id, approve);
        return ResponseEntity.ok(ApiResponse.success().add("data", payment));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
