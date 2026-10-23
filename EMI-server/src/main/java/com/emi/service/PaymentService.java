package com.emi.service;

import com.emi.config.properties.CompanyProperties;
import com.emi.dto.request.PaymentRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    private static final int MAX_ITEMS = 200;

    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentAllocationJpaRepository paymentAllocationJpaRepository;
    private final FinanceJpaRepository financeJpaRepository;
    private final CustomerJpaRepository customerJpaRepository;
    private final AssetJpaRepository assetJpaRepository;
    private final EmiInstallmentJpaRepository emiInstallmentJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final CompanyProperties companyProperties;
    private final AuditService auditService;

    public record PaymentList(List<PaymentResponse> items, int total, BigDecimal totalAmount, long pendingCount) {
    }

    // Records money received. Admin payments are verified straight away and applied to the schedule;
    // care payments stay pending until an admin verifies them. A repeated Idempotency-Key returns the original.
    @Transactional
    public PaymentResponse record(String staffId, boolean isAdmin, String idempotencyKey, PaymentRequest request) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null) {
            var existing = paymentJpaRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return responseFor(existing.get());
            }
        }

        Finance finance = findFinance(request.getFinanceId());
        Customer customer = customerJpaRepository.findById(finance.getCustomerId())
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));
        if (!isAdmin && !inStaffScope(customer, staffId)) {
            throw new DomainException("Finance not found", HttpStatus.NOT_FOUND);
        }
        if (Set.of(FinanceStatus.CANCELLED, FinanceStatus.CLOSED, FinanceStatus.FORECLOSED).contains(finance.getStatus())) {
            throw new DomainException("Payments cannot be recorded on a "
                    + finance.getStatus().name().toLowerCase() + " finance");
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
        // Care staff always collect; for an admin, only cash and cheques are physically collected
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
                .idempotencyKey(key)
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

    // Admin approves or rejects a pending payment. Only an approved payment is applied to the EMI schedule.
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

    // Payment history for staff, newest first. Care staff see their own and unassigned customers, Admin sees all.
    @Transactional(readOnly = true)
    public PaymentList list(String callerId, boolean isAdmin, String status, boolean mine, LocalDate from, LocalDate to) {
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

        List<Object[]> rows = paymentJpaRepository.search(isAdmin, callerId, wantedStatus == null,
                wantedStatus == null ? PaymentStatus.PENDING : wantedStatus, mine,
                from != null ? from : LocalDate.of(1900, 1, 1), to != null ? to : LocalDate.of(9999, 12, 31));

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Object[] r : rows) {
            Payment p = (Payment) r[0];
            if (p.getStatus() != PaymentStatus.REJECTED) {
                totalAmount = totalAmount.add(p.getAmount());
            }
        }

        List<Object[]> page = rows.size() > MAX_ITEMS ? rows.subList(0, MAX_ITEMS) : rows;
        List<Payment> payments = page.stream().map(r -> (Payment) r[0]).toList();
        Map<String, Finance> finances = page.stream().map(r -> (Finance) r[1])
                .collect(Collectors.toMap(Finance::getFinanceId, Function.identity(), (a, b) -> a));
        Map<String, Customer> customers = page.stream().map(r -> (Customer) r[2])
                .collect(Collectors.toMap(Customer::getCustomerId, Function.identity(), (a, b) -> a));

        return new PaymentList(toResponses(payments, finances, customers), rows.size(), totalAmount,
                paymentJpaRepository.countPending(isAdmin, callerId));
    }

    // Receipt view. Customers can open only their own, and never a rejected one.
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
            // sees everything
        } else if (isCare) {
            if (!inStaffScope(c, callerId)) {
                throw new DomainException("Payment not found", HttpStatus.NOT_FOUND);
            }
        } else if (c.getUserId() == null || !c.getUserId().equals(callerId) || p.getStatus() == PaymentStatus.REJECTED) {
            throw new DomainException("Payment not found", HttpStatus.NOT_FOUND);
        }

        PaymentResponse base = toResponses(List.of(p), Map.of(f.getFinanceId(), f), Map.of(c.getCustomerId(), c)).get(0);

        // The allocation is empty until the payment is verified, because nothing has been applied yet
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

    // Payments of one finance, newest first, for the loan account view. Customers don't see rejected ones.
    @Transactional(readOnly = true)
    public List<PaymentResponse> forFinance(Finance finance, Customer customer, boolean hideRejected) {
        List<Payment> payments = paymentJpaRepository
                .findAllByFinanceIdOrderByPaymentDateDescCreatedAtDesc(finance.getFinanceId()).stream()
                .filter(p -> !hideRejected || p.getStatus() != PaymentStatus.REJECTED)
                .toList();
        return toResponses(payments, Map.of(finance.getFinanceId(), finance),
                Map.of(customer.getCustomerId(), customer));
    }

    // Oldest unpaid EMI first, filling each before moving on; waived and cancelled EMIs are skipped.
    // Money left after the last EMI is recorded as advance credit (no installment). Closes the finance once
    // every EMI is paid.
    private void apply(Payment payment, Finance finance) {
        List<EmiInstallment> installments = emiInstallmentJpaRepository
                .findAllByFinanceIdOrderByInstallmentNo(finance.getFinanceId());
        BigDecimal remaining = payment.getAmount();
        List<PaymentAllocation> allocations = new ArrayList<>();

        for (EmiInstallment i : installments) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (i.getStatus() == InstallmentStatus.WAIVED || i.getStatus() == InstallmentStatus.CANCELLED
                    || i.getStatus() == InstallmentStatus.PAID) {
                continue;
            }
            BigDecimal owed = i.getEmiAmount().subtract(i.getAmountPaid());
            if (owed.signum() <= 0) {
                continue;
            }
            BigDecimal applied = remaining.min(owed);
            i.setAmountPaid(i.getAmountPaid().add(applied));
            if (i.getAmountPaid().compareTo(i.getEmiAmount()) >= 0) {
                i.setStatus(InstallmentStatus.PAID);
                i.setPaidAt(payment.getPaymentDate());
            } else {
                i.setStatus(InstallmentStatus.PARTIALLY_PAID);
            }
            allocations.add(PaymentAllocation.builder().paymentId(payment.getPaymentId())
                    .installmentId(i.getInstallmentId()).installmentNo(i.getInstallmentNo()).amount(applied).build());
            remaining = remaining.subtract(applied);
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
            return new PaymentResponse(
                    p.getPaymentNumber(),
                    p.getReceiptNo(),
                    f != null ? f.getFinanceNumber() : p.getFinanceId(),
                    p.getCustomerId(),
                    c != null ? c.getFullName() : null,
                    p.getAmount(),
                    p.getPaymentDate(),
                    p.getMethod().label(),
                    p.getTxnId() == null ? "" : p.getTxnId(),
                    p.getReference() == null ? "" : p.getReference(),
                    p.getCollectedBy(),
                    collectorName,
                    p.getStatus().name().toLowerCase(),
                    p.getVerifiedBy(),
                    p.getRemarks() == null ? "" : p.getRemarks(),
                    p.getCreatedAt(),
                    applied.getOrDefault(p.getPaymentId(), List.of()));
        }).toList();
    }

    // id is the FIN-YYYY-NNNNNN number or the internal id
    private Finance findFinance(String id) {
        return financeJpaRepository.findByFinanceNumber(id.trim())
                .or(() -> financeJpaRepository.findById(id.trim()))
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));
    }

    // id is the PAY-NNNNNN number or the internal id
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
