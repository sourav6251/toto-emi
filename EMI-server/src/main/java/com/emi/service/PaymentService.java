package com.emi.service;

import com.cashfree.pg.ApiResponse;
import com.cashfree.pg.model.OrderEntity;
import com.cashfree.pg.model.SubscriptionEntity;
import com.emi.config.properties.CompanyProperties;
import com.emi.dto.request.OnlinePaymentRequest;
import com.emi.dto.request.PaymentRequest;
import com.emi.dto.response.AutoPaySetupResponse;
import com.emi.dto.response.PaymentOrderResponse;
import com.emi.dto.response.PaymentReceiptResponse;
import com.emi.dto.response.PaymentResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Asset;
import com.emi.model.entity.Customer;
import com.emi.model.entity.EmiInstallment;
import com.emi.model.entity.Finance;
import com.emi.model.entity.Payment;
import com.emi.model.entity.PaymentAllocation;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.AssetJpaRepository;
import com.emi.model.reposetory.CustomerJpaRepository;
import com.emi.model.reposetory.EmiInstallmentJpaRepository;
import com.emi.model.reposetory.FinanceJpaRepository;
import com.emi.model.reposetory.PaymentAllocationJpaRepository;
import com.emi.model.reposetory.PaymentJpaRepository;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.InstallmentStatus;
import com.emi.utils.enums.PaymentMethod;
import com.emi.utils.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PaymentService {

    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentAllocationJpaRepository paymentAllocationJpaRepository;
    private final FinanceJpaRepository financeJpaRepository;
    private final CustomerJpaRepository customerJpaRepository;
    private final AssetJpaRepository assetJpaRepository;
    private final EmiInstallmentJpaRepository emiInstallmentJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final CompanyProperties companyProperties;
    private final AuditService auditService;
    private final CashfreePaymentService cashfreePaymentService;

    public record PaymentList(List<PaymentResponse> items, int total, BigDecimal totalAmount, long pendingCount) {
    }

    @Transactional
    public PaymentResponse record(String staffId, boolean isAdmin, PaymentRequest request) {
        Finance finance = findFinance(request.getFinanceId());
        Customer customer = customerJpaRepository.findById(finance.getCustomerId())
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));
        if (!isAdmin && !inStaffScope(customer, staffId)) {
            throw new DomainException("Finance not found", HttpStatus.NOT_FOUND);
        }
        if (finance.getStatus() == FinanceStatus.PENDING_APPROVAL) {
            throw new DomainException("Payments cannot be recorded until an admin approves this finance");
        }
        if (Set.of(FinanceStatus.CANCELLED, FinanceStatus.CLOSED, FinanceStatus.FORECLOSED).contains(finance.getStatus())) {
            throw new DomainException("Payments cannot be recorded on a "
                    + finance.getStatus().name().toLowerCase() + " finance");
        }

        var existing = paymentJpaRepository.findFirstByFinanceIdAndPaymentDateAndAmountAndStatusNot(
                finance.getFinanceId(), request.getDate(), request.getAmount(), PaymentStatus.REJECTED);
        if (existing.isPresent()) {
            return responseFor(existing.get());
        }

        PaymentMethod method = PaymentMethod.parse(request.getMethod());
        if (method == null) {
            throw new DomainException("Payment method must be one of Cash, UPI, Bank Transfer, Payment Gateway, Cheque");
        }
        String txnId = request.getTxnId() == null || request.getTxnId().isBlank() ? null : request.getTxnId().trim();
        if (method.requiresTxnId() && txnId == null) {
            throw new DomainException(method == PaymentMethod.CHEQUE
                    ? "Cheque number is required" : "Transaction reference is required for " + method.label());
        }
        if (txnId != null && paymentJpaRepository.existsByMethodAndTxnIdAndStatusNot(method, txnId, PaymentStatus.REJECTED)) {
            throw new DomainException("A " + method.label() + " payment with this reference is already recorded",
                    HttpStatus.CONFLICT);
        }

        long sequence = paymentJpaRepository.nextPaymentNumber();
        boolean collected = !isAdmin || method == PaymentMethod.CASH || method == PaymentMethod.CHEQUE;

        Payment payment = Payment.builder()
                .paymentNumber(String.format("PAY-%06d", sequence))
                .receiptNo(String.format("RCT-%d-%06d", LocalDate.now().getYear(), sequence))
                .financeId(finance.getFinanceId())
                .customerId(customer.getCustomerId())
                .amount(request.getAmount())
                .paymentDate(request.getDate())
                .method(method)
                .txnId(txnId)
                .reference(blankToNull(request.getReference()))
                .remarks(blankToNull(request.getRemarks()))
                .collectedBy(collected ? staffId : null)
                .status(isAdmin ? PaymentStatus.VERIFIED : PaymentStatus.PENDING)
                .verifiedBy(isAdmin ? staffId : null)
                .verifiedAt(isAdmin ? OffsetDateTime.now() : null)
                .createdBy(staffId)
                .build();
        payment = paymentJpaRepository.save(payment);

        if (isAdmin) {
            apply(payment, finance);
        }
        auditService.log(staffId, "Payment recorded", payment.getReceiptNo() + " — ₹"
                + payment.getAmount().stripTrailingZeros().toPlainString() + " by " + method.label()
                + " on " + finance.getFinanceNumber(), finance.getFinanceNumber());
        return responseFor(payment);
    }

    @Transactional
    public PaymentResponse verify(String adminId, String id, boolean approve) {
        Payment payment = findPayment(id);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new DomainException("This payment is already " + payment.getStatus().name().toLowerCase() + ".",
                    HttpStatus.CONFLICT);
        }
        payment.setStatus(approve ? PaymentStatus.VERIFIED : PaymentStatus.REJECTED);
        payment.setVerifiedBy(adminId);
        payment.setVerifiedAt(OffsetDateTime.now());
        payment = paymentJpaRepository.save(payment);

        Finance finance = financeJpaRepository.findById(payment.getFinanceId())
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));
        if (approve) {
            apply(payment, finance);
        }
        auditService.log(adminId, approve ? "Payment verified" : "Payment rejected", payment.getReceiptNo() + " — ₹"
                + payment.getAmount().stripTrailingZeros().toPlainString() + " on " + finance.getFinanceNumber(),
                finance.getFinanceNumber());
        return responseFor(payment);
    }

    @Transactional
    public PaymentResponse recordGatewayPayment(String financeId, BigDecimal amount, LocalDate date, String txnId,
                                                String reference, String orderId, String actorId) {
        Finance finance = financeJpaRepository.findById(financeId)
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));

        if (txnId != null && !txnId.isBlank()) {
            var existing = paymentJpaRepository.findFirstByTxnId(txnId.trim());
            if (existing.isPresent()) {
                return responseFor(existing.get());
            }
        }
        if (orderId != null && !orderId.isBlank()) {
            var existing = paymentJpaRepository.findFirstByReference(orderId.trim());
            if (existing.isPresent()) {
                return responseFor(existing.get());
            }
        }

        long sequence = paymentJpaRepository.nextPaymentNumber();
        Payment payment = Payment.builder()
                .paymentNumber(String.format("PAY-%06d", sequence))
                .receiptNo(String.format("RCT-%d-%06d", LocalDate.now().getYear(), sequence))
                .financeId(finance.getFinanceId())
                .customerId(finance.getCustomerId())
                .amount(amount)
                .paymentDate(date)
                .method(PaymentMethod.PAYMENT_GATEWAY)
                .txnId(txnId)
                .reference(blankToNull(reference))
                .remarks("Paid online, Cashfree order " + orderId)
                .status(PaymentStatus.VERIFIED)
                .verifiedAt(OffsetDateTime.now())
                .createdBy(actorId)
                .build();
        payment = paymentJpaRepository.save(payment);

        apply(payment, finance);
        auditService.log(actorId, "Online payment received", payment.getReceiptNo() + " — ₹"
                + payment.getAmount().stripTrailingZeros().toPlainString() + " via Cashfree on "
                + finance.getFinanceNumber(), finance.getFinanceNumber());
        return responseFor(payment);
    }

    @Transactional(readOnly = true)
    public PaymentList list(String status, LocalDate from, LocalDate to) {
        PaymentStatus wantedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                wantedStatus = PaymentStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new DomainException("status must be one of pending, verified, rejected");
            }
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainException("from must not be after to");
        }

        List<Object[]> rows = paymentJpaRepository.search(wantedStatus == null,
                wantedStatus == null ? PaymentStatus.PENDING : wantedStatus,
                from != null ? from : LocalDate.of(2000, 1, 1), to != null ? to : LocalDate.of(9999, 12, 31));

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Object[] r : rows) {
            Payment p = (Payment) r[0];
            if (p.getStatus() != PaymentStatus.REJECTED) {
                totalAmount = totalAmount.add(p.getAmount());
            }
        }

        List<Payment> payments = rows.stream().map(r -> (Payment) r[0]).toList();
        Map<String, Finance> finances = rows.stream().map(r -> (Finance) r[1])
                .collect(Collectors.toMap(Finance::getFinanceId, Function.identity(), (a, b) -> a));
        Map<String, Customer> customers = rows.stream().map(r -> (Customer) r[2])
                .collect(Collectors.toMap(Customer::getCustomerId, Function.identity(), (a, b) -> a));

        return new PaymentList(toResponses(payments, finances, customers), rows.size(), totalAmount,
                paymentJpaRepository.countByStatus(PaymentStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public PaymentReceiptResponse getReceipt(String callerId, Authentication authentication, String id) {
        Payment p = findPayment(id);
        Finance f = financeJpaRepository.findById(p.getFinanceId())
                .orElseThrow(() -> new DomainException("Payment not found", HttpStatus.NOT_FOUND));
        Customer c = customerJpaRepository.findById(p.getCustomerId())
                .orElseThrow(() -> new DomainException("Payment not found", HttpStatus.NOT_FOUND));

        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        boolean isCare = hasRole(authentication, "ROLE_CUSTOMER_CARE");
        if (isAdmin) {
        } else if (isCare) {
            if (!inStaffScope(c, callerId)) {
                throw new DomainException("Payment not found", HttpStatus.NOT_FOUND);
            }
        } else if (c.getUserId() == null || !c.getUserId().equals(callerId) || p.getStatus() == PaymentStatus.REJECTED) {
            throw new DomainException("Payment not found", HttpStatus.NOT_FOUND);
        }

        PaymentResponse base = toResponses(List.of(p), Map.of(f.getFinanceId(), f), Map.of(c.getCustomerId(), c)).get(0);

        List<PaymentReceiptResponse.Allocation> allocation = new ArrayList<>();
        if (p.getStatus() == PaymentStatus.VERIFIED) {
            Map<String, EmiInstallment> installments = emiInstallmentJpaRepository
                    .findAllByFinanceIdOrderByInstallmentNo(f.getFinanceId()).stream()
                    .collect(Collectors.toMap(EmiInstallment::getInstallmentId, Function.identity()));
            paymentAllocationJpaRepository.findAllByPaymentIdIn(List.of(p.getPaymentId())).stream()
                    .sorted(Comparator.comparing(PaymentAllocation::getInstallmentNo,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(a -> {
                        EmiInstallment i = a.getInstallmentId() == null ? null : installments.get(a.getInstallmentId());
                        allocation.add(new PaymentReceiptResponse.Allocation(
                                a.getInstallmentNo(), a.getAmount(), i != null ? i.getDueDate() : null));
                    });
        }

        Asset asset = assetJpaRepository.findById(f.getAssetId()).orElse(null);
        return PaymentReceiptResponse.builder()
                .id(base.getId())
                .receiptNo(base.getReceiptNo())
                .financeId(base.getFinanceId())
                .customerId(base.getCustomerId())
                .customerName(base.getCustomerName())
                .amount(base.getAmount())
                .date(base.getDate())
                .method(base.getMethod())
                .txnId(base.getTxnId())
                .reference(base.getReference())
                .collectedBy(base.getCollectedBy())
                .collectedByName(base.getCollectedByName())
                .status(base.getStatus())
                .verifiedBy(base.getVerifiedBy())
                .remarks(base.getRemarks())
                .createdAt(base.getCreatedAt())
                .appliedTo(base.getAppliedTo())
                .allocation(allocation)
                .finance(new PaymentReceiptResponse.FinanceBrief(f.getFinanceNumber(),
                        asset != null ? asset.getProductName() : null,
                        asset != null ? asset.getVehicleNumber() : null))
                .customer(new PaymentReceiptResponse.CustomerBrief(c.getFullName(), c.getPhoneNumber()))
                .company(new PaymentReceiptResponse.CompanyBrief(companyProperties.getName(),
                        companyProperties.getAddress(), companyProperties.getPhone(),
                        companyProperties.getEmail(), companyProperties.getGstin()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> forFinance(Finance finance, Customer customer, boolean hideRejected) {
        List<Payment> payments = paymentJpaRepository
                .findAllByFinanceIdOrderByPaymentDateDescCreatedAtDesc(finance.getFinanceId()).stream()
                .filter(p -> !hideRejected || p.getStatus() != PaymentStatus.REJECTED)
                .toList();
        return toResponses(payments, Map.of(finance.getFinanceId(), finance),
                Map.of(customer.getCustomerId(), customer));
    }

    public BigDecimal calculateOverdueCharge(EmiInstallment installment, Finance finance, LocalDate asOfDate) {
        if (installment.getStatus() == InstallmentStatus.PAID
                || installment.getStatus() == InstallmentStatus.WAIVED
                || installment.getStatus() == InstallmentStatus.CANCELLED) {
            return BigDecimal.ZERO;
        }
        int graceDays = finance.getGraceDays() != null ? finance.getGraceDays() : 0;
        LocalDate cutoffDate = installment.getDueDate().plusDays(graceDays);
        if (!asOfDate.isAfter(cutoffDate)) {
            return BigDecimal.ZERO;
        }

        BigDecimal percentage = finance.getOverduePercentage();
        if (percentage != null && percentage.compareTo(BigDecimal.ZERO) > 0) {
            return installment.getEmiAmount()
                    .multiply(percentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (finance.getLateFeeAmount() != null && finance.getLateFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            return finance.getLateFeeAmount();
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal calculateInstallmentTotalDue(EmiInstallment installment, Finance finance, LocalDate asOfDate) {
        BigDecimal emiPaid = installment.getAmountPaid() != null ? installment.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal emiBalance = installment.getEmiAmount().subtract(emiPaid);
        if (emiBalance.compareTo(BigDecimal.ZERO) < 0) {
            emiBalance = BigDecimal.ZERO;
        }

        BigDecimal overdueCharge = calculateOverdueCharge(installment, finance, asOfDate);
        BigDecimal lateFeePaid = installment.getLateFeePaid() != null ? installment.getLateFeePaid() : BigDecimal.ZERO;
        BigDecimal lateFeeBalance = overdueCharge.subtract(lateFeePaid);
        if (lateFeeBalance.compareTo(BigDecimal.ZERO) < 0) {
            lateFeeBalance = BigDecimal.ZERO;
        }

        return emiBalance.add(lateFeeBalance);
    }

    @Transactional(readOnly = true)
    public List<EmiInstallment> getPendingEmis(String financeId) {
        Finance finance = financeJpaRepository.findById(financeId).orElse(null);
        LocalDate today = LocalDate.now();

        return emiInstallmentJpaRepository
                .findAllByFinanceIdOrderByInstallmentNo(financeId).stream()
                .filter(i -> i.getStatus() != InstallmentStatus.PAID
                        && i.getStatus() != InstallmentStatus.WAIVED
                        && i.getStatus() != InstallmentStatus.CANCELLED)
                .peek(i -> {
                    if (finance != null) {
                        BigDecimal overdue = calculateOverdueCharge(i, finance, today);
                        i.setLateFee(overdue);
                    }
                })
                .filter(i -> {
                    BigDecimal totalDue = finance != null
                            ? calculateInstallmentTotalDue(i, finance, today)
                            : i.getEmiAmount().subtract(i.getAmountPaid() != null ? i.getAmountPaid() : BigDecimal.ZERO);
                    return totalDue.compareTo(BigDecimal.ZERO) > 0;
                })
                .sorted(Comparator.comparing(EmiInstallment::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public PaymentOrderResponse payEmi(OnlinePaymentRequest paymentRequest) {
        if (paymentRequest.getFinanceID() == null || paymentRequest.getFinanceID().isBlank()) {
            throw new DomainException("Choose correct finance");
        }
        Finance finance = financeJpaRepository.findByFinanceNumber(paymentRequest.getFinanceID().trim())
                .or(() -> financeJpaRepository.findById(paymentRequest.getFinanceID().trim()))
                .orElseThrow(() -> new DomainException("Choose correct finance"));
        if (!financeJpaRepository.isUsersFinance(paymentRequest.getUserID(), finance.getFinanceId())) {
            throw new DomainException("Choose correct finance");
        }
        if (finance.getStatus() == FinanceStatus.PENDING_APPROVAL) {
            throw new DomainException("Payments cannot be made until this finance is approved");
        }
        if (Set.of(FinanceStatus.CANCELLED, FinanceStatus.CLOSED, FinanceStatus.FORECLOSED).contains(finance.getStatus())) {
            throw new DomainException("Payments cannot be made on a "
                    + finance.getStatus().name().toLowerCase() + " finance");
        }

        List<EmiInstallment> emiInstallments = this.getPendingEmis(finance.getFinanceId());
        if (emiInstallments.isEmpty()) {
            throw new DomainException("No pending EMIs found for this finance");
        }
        if (paymentRequest.getMonths() > emiInstallments.size()) {
            throw new DomainException("You only have " + emiInstallments.size() + " month(s) due, but requested " + paymentRequest.getMonths() + " to pay");
        }

        List<EmiInstallment> selectedEmis = emiInstallments.stream()
                .limit(paymentRequest.getMonths())
                .toList();

        LocalDate today = LocalDate.now();
        BigDecimal totalAmount = selectedEmis.stream()
                .map(i -> calculateInstallmentTotalDue(i, finance, today))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Customer customer = customerJpaRepository.findByUserId(paymentRequest.getUserID())
                .orElseThrow(() -> new DomainException("Customer not found", HttpStatus.NOT_FOUND));

        return cashfreePaymentService.generateCashfreeOrder(finance, customer, totalAmount, paymentRequest);
    }

    @Transactional
    public PaymentResponse verifyEmi(String userId, String orderId) {
        OrderEntity order = cashfreePaymentService.getOrderDetails(orderId);
        String createdBy = order.getOrderTags() != null ? order.getOrderTags().get(CashfreePaymentService.CREATED_BY_TAG) : null;
        LocalDate paymentDate = order.getCreatedAt() != null
        ? order.getCreatedAt().toLocalDate()
        : LocalDate.now();

        if (createdBy == null || !createdBy.equals(userId)) {
            throw new DomainException("Order not found", HttpStatus.NOT_FOUND);
        }
        if (!"PAID".equalsIgnoreCase(order.getOrderStatus())) {
            throw new DomainException("Payment is not complete yet, order is "
                    + (order.getOrderStatus() == null ? "unknown" : order.getOrderStatus().toLowerCase()));
        }
        String financeId = order.getOrderTags().get(CashfreePaymentService.FINANCE_TAG);
        if (financeId == null) {
            throw new DomainException("Order is not linked to a finance");
        }

        return recordGatewayPayment(financeId, order.getOrderAmount(), paymentDate, order.getCfOrderId(),
                order.getOrderId(), order.getOrderId(), userId);
    }

    public AutoPaySetupResponse setupAutoPay(String userId, String financeId, String returnUrl) {
        if (financeId == null || financeId.isBlank()) {
            throw new DomainException("Choose correct finance");
        }
        Finance finance = financeJpaRepository.findByFinanceNumber(financeId.trim())
                .or(() -> financeJpaRepository.findById(financeId.trim()))
                .orElseThrow(() -> new DomainException("Choose correct finance"));

        if (!financeJpaRepository.isUsersFinance(userId, finance.getFinanceId())) {
            throw new DomainException("Choose correct finance");
        }
        if (finance.getStatus() == FinanceStatus.PENDING_APPROVAL) {
            throw new DomainException("AutoPay cannot be set up until this finance is approved");
        }
        if (Set.of(FinanceStatus.CANCELLED, FinanceStatus.CLOSED, FinanceStatus.FORECLOSED).contains(finance.getStatus())) {
            throw new DomainException("AutoPay cannot be configured for a "
                    + finance.getStatus().name().toLowerCase() + " finance");
        }

        List<EmiInstallment> pending = getPendingEmis(finance.getFinanceId());
        if (pending.isEmpty()) {
            throw new DomainException("No pending EMIs found for this finance");
        }

        LocalDate firstDueDate = pending.get(0).getDueDate();
        OffsetDateTime firstChargeTime = firstDueDate.atTime(8, 0)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toOffsetDateTime();
        String firstChargeTimeString = firstChargeTime.truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Customer customer = customerJpaRepository.findByUserId(userId)
                .orElseThrow(() -> new DomainException("Customer not found", HttpStatus.NOT_FOUND));

        SubscriptionEntity sub = cashfreePaymentService.createAutoPaySubscription(
                finance, customer, firstChargeTimeString, returnUrl);

        finance.setSubscriptionId(sub.getSubscriptionId());
        finance.setAutoPayStatus(sub.getSubscriptionStatus());
        financeJpaRepository.save(finance);

        BigDecimal maxAmount = finance.getEmiAmount().multiply(BigDecimal.valueOf(2));

        return AutoPaySetupResponse.builder()
                .subscriptionId(sub.getSubscriptionId())
                .subscriptionSessionId(sub.getSubscriptionSessionId())
                .cfSubscriptionId(sub.getCfSubscriptionId())
                .status(sub.getSubscriptionStatus())
                .emiAmount(finance.getEmiAmount())
                .maxAmount(maxAmount)
                .firstChargeTime(firstChargeTimeString)
                .build();
    }

    @Transactional
    public void cancelAutoPay(String userId, String financeId) {
        if (financeId == null || financeId.isBlank()) {
            throw new DomainException("Choose correct finance");
        }
        Finance finance = financeJpaRepository.findByFinanceNumber(financeId.trim())
                .or(() -> financeJpaRepository.findById(financeId.trim()))
                .orElseThrow(() -> new DomainException("Choose correct finance"));

        if (!financeJpaRepository.isUsersFinance(userId, finance.getFinanceId())) {
            throw new DomainException("Choose correct finance");
        }
        if (finance.getSubscriptionId() == null || finance.getSubscriptionId().isBlank()) {
            throw new DomainException("No active AutoPay found for this finance");
        }
        if ("CANCELLED".equalsIgnoreCase(finance.getAutoPayStatus())) {
            throw new DomainException("AutoPay is already cancelled");
        }

        cashfreePaymentService.cancelSubscription(finance.getSubscriptionId());
        finance.setAutoPayStatus("CANCELLED");
        financeJpaRepository.save(finance);

        auditService.log(userId, "AutoPay cancelled",
                "Customer cancelled AutoPay on " + finance.getFinanceNumber(),
                finance.getFinanceNumber());
    }

    @Transactional
    public int processOverdueAutoPayCharges(LocalDate asOfDate) {
        List<Object[]> rows = emiInstallmentJpaRepository.findOverdueInstallmentsWithSubscription(asOfDate);
        if (rows.isEmpty()) {
            return 0;
        }

        Map<Finance, List<EmiInstallment>> financeMap = new HashMap<>();
        for (Object[] row : rows) {
            EmiInstallment installment = (EmiInstallment) row[0];
            Finance finance = (Finance) row[1];
            financeMap.computeIfAbsent(finance, k -> new ArrayList<>()).add(installment);
        }

        int processed = 0;
        for (Map.Entry<Finance, List<EmiInstallment>> entry : financeMap.entrySet()) {
            Finance finance = entry.getKey();
            List<EmiInstallment> overdueEmis = entry.getValue();

            BigDecimal totalDue = overdueEmis.stream()
                    .map(i -> calculateInstallmentTotalDue(i, finance, asOfDate))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalDue.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    cashfreePaymentService.chargeSubscription(
                            finance.getSubscriptionId(),
                            totalDue,
                            "AutoPay overdue charge for " + finance.getFinanceNumber()
                    );
                    auditService.log("SYSTEM", "AutoPay overdue charge requested",
                            finance.getFinanceNumber() + " — ₹" + totalDue.stripTrailingZeros().toPlainString()
                                    + " on subscription " + finance.getSubscriptionId(),
                            finance.getFinanceNumber());
                    processed++;
                } catch (Exception e) {
                    auditService.log("SYSTEM", "AutoPay overdue charge failed",
                            finance.getFinanceNumber() + " — " + e.getMessage(),
                            finance.getFinanceNumber());
                }
            }
        }
        return processed;
    }

    private void apply(Payment payment, Finance finance) {
        List<EmiInstallment> installments = emiInstallmentJpaRepository
                .findAllByFinanceIdOrderByInstallmentNo(finance.getFinanceId());
        BigDecimal remaining = payment.getAmount();
        List<PaymentAllocation> allocations = new ArrayList<>();
        LocalDate paymentDate = payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now();

        for (EmiInstallment i : installments) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (i.getStatus() == InstallmentStatus.WAIVED || i.getStatus() == InstallmentStatus.CANCELLED
                    || i.getStatus() == InstallmentStatus.PAID) {
                continue;
            }

            BigDecimal overdueCharge = calculateOverdueCharge(i, finance, paymentDate);
            i.setLateFee(overdueCharge);

            BigDecimal emiPaid = i.getAmountPaid() != null ? i.getAmountPaid() : BigDecimal.ZERO;
            BigDecimal emiOwed = i.getEmiAmount().subtract(emiPaid);
            BigDecimal totalAppliedToInstallment = BigDecimal.ZERO;

            if (emiOwed.signum() > 0) {
                BigDecimal appliedEmi = remaining.min(emiOwed);
                i.setAmountPaid(emiPaid.add(appliedEmi));
                remaining = remaining.subtract(appliedEmi);
                totalAppliedToInstallment = totalAppliedToInstallment.add(appliedEmi);
            }

            BigDecimal lateFeePaid = i.getLateFeePaid() != null ? i.getLateFeePaid() : BigDecimal.ZERO;
            BigDecimal lateFeeOwed = i.getLateFee().subtract(lateFeePaid);
            if (remaining.signum() > 0 && lateFeeOwed.signum() > 0) {
                BigDecimal appliedLateFee = remaining.min(lateFeeOwed);
                i.setLateFeePaid(lateFeePaid.add(appliedLateFee));
                remaining = remaining.subtract(appliedLateFee);
                totalAppliedToInstallment = totalAppliedToInstallment.add(appliedLateFee);
            }

            boolean emiFullyPaid = i.getAmountPaid().compareTo(i.getEmiAmount()) >= 0;
            boolean lateFeeFullyPaid = i.getLateFeePaid().compareTo(i.getLateFee()) >= 0;

            if (emiFullyPaid && lateFeeFullyPaid) {
                i.setStatus(InstallmentStatus.PAID);
                i.setPaidAt(payment.getPaymentDate());
            } else if (totalAppliedToInstallment.signum() > 0) {
                i.setStatus(InstallmentStatus.PARTIALLY_PAID);
            }

            if (totalAppliedToInstallment.signum() > 0) {
                allocations.add(PaymentAllocation.builder()
                        .paymentId(payment.getPaymentId())
                        .installmentId(i.getInstallmentId())
                        .installmentNo(i.getInstallmentNo())
                        .amount(totalAppliedToInstallment)
                        .build());
            }
        }
        if (remaining.signum() > 0) {
            allocations.add(PaymentAllocation.builder().paymentId(payment.getPaymentId()).amount(remaining).build());
        }
        emiInstallmentJpaRepository.saveAll(installments);
        paymentAllocationJpaRepository.saveAll(allocations);

        boolean settled = installments.stream().allMatch(i -> i.getStatus() == InstallmentStatus.PAID
                || i.getStatus() == InstallmentStatus.WAIVED || i.getStatus() == InstallmentStatus.CANCELLED);
        if (settled && finance.getStatus() != FinanceStatus.CLOSED) {
            finance.setStatus(FinanceStatus.CLOSED);
            finance.setClosedAt(OffsetDateTime.now());

            if (finance.getSubscriptionId() != null && !finance.getSubscriptionId().isBlank()) {
                cashfreePaymentService.cancelSubscription(finance.getSubscriptionId());
                finance.setAutoPayStatus("CANCELLED");
            }

            financeJpaRepository.save(finance);
        }
    }

    private PaymentResponse responseFor(Payment payment) {
        Finance finance = financeJpaRepository.findById(payment.getFinanceId()).orElseThrow();
        Customer customer = customerJpaRepository.findById(payment.getCustomerId()).orElseThrow();
        return toResponses(List.of(payment), Map.of(finance.getFinanceId(), finance),
                Map.of(customer.getCustomerId(), customer)).get(0);
    }

    private List<PaymentResponse> toResponses(List<Payment> payments, Map<String, Finance> finances,
                                              Map<String, Customer> customers) {
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<String, String> staffNames = new HashMap<>();
        userJpaRepository.findAllById(payments.stream().map(Payment::getCollectedBy)
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach((Users u) -> staffNames.put(u.getUserID(), u.getFullName()));

        Map<String, List<Integer>> applied = new HashMap<>();
        paymentAllocationJpaRepository.findAllByPaymentIdIn(payments.stream().map(Payment::getPaymentId).toList())
                .stream().filter(a -> a.getInstallmentNo() != null)
                .sorted(Comparator.comparing(PaymentAllocation::getInstallmentNo))
                .forEach(a -> applied.computeIfAbsent(a.getPaymentId(), k -> new ArrayList<>()).add(a.getInstallmentNo()));

        return payments.stream().map(p -> {
            Finance f = finances.get(p.getFinanceId());
            Customer c = customers.get(p.getCustomerId());
            String collectorName = p.getCollectedBy() == null ? "Online"
                    : staffNames.getOrDefault(p.getCollectedBy(), "Unknown");
            return PaymentResponse.builder()
                    .id(p.getPaymentNumber())
                    .receiptNo(p.getReceiptNo())
                    .financeId(f != null ? f.getFinanceNumber() : p.getFinanceId())
                    .customerId(p.getCustomerId())
                    .customerName(c != null ? c.getFullName() : null)
                    .customerPhone(c != null ? c.getPhoneNumber() : null)
                    .amount(p.getAmount())
                    .date(p.getPaymentDate())
                    .method(p.getMethod().label())
                    .txnId(p.getTxnId() == null ? "" : p.getTxnId())
                    .reference(p.getReference() == null ? "" : p.getReference())
                    .collectedBy(p.getCollectedBy())
                    .collectedByName(collectorName)
                    .status(p.getStatus().name().toLowerCase())
                    .verifiedBy(p.getVerifiedBy())
                    .remarks(p.getRemarks() == null ? "" : p.getRemarks())
                    .createdAt(p.getCreatedAt())
                    .appliedTo(applied.getOrDefault(p.getPaymentId(), List.of()))
                    .build();
        }).toList();
    }

    private Finance findFinance(String id) {
        return financeJpaRepository.findByFinanceNumber(id.trim())
                .or(() -> financeJpaRepository.findById(id.trim()))
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));
    }

    private Payment findPayment(String id) {
        return paymentJpaRepository.findByPaymentNumber(id.trim())
                .or(() -> paymentJpaRepository.findById(id.trim()))
                .orElseThrow(() -> new DomainException("Payment not found", HttpStatus.NOT_FOUND));
    }

    private boolean inStaffScope(Customer customer, String staffId) {
        return customer.getAssignedStaffId() == null || customer.getAssignedStaffId().equals(staffId);
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
