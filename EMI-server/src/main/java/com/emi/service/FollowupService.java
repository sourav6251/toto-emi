package com.emi.service;

import com.emi.dto.request.FollowupRequest;
import com.emi.dto.response.FollowupQueueItem;
import com.emi.dto.response.FollowupResponse;
import com.emi.exception.DomainException;
import com.emi.model.entity.Customer;
import com.emi.model.entity.EmiInstallment;
import com.emi.model.entity.Finance;
import com.emi.model.entity.Followup;
import com.emi.model.entity.Payment;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.CustomerJpaRepository;
import com.emi.model.reposetory.EmiInstallmentJpaRepository;
import com.emi.model.reposetory.FinanceJpaRepository;
import com.emi.model.reposetory.FollowupJpaRepository;
import com.emi.model.reposetory.PaymentJpaRepository;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class FollowupService {

    private static final String DEFAULT_CALL_STATUS = "Connected";
    // Finances with nothing to collect: finished ones, and ones not yet approved by an admin
    private static final List<FinanceStatus> FINISHED = List.of(
            FinanceStatus.CANCELLED, FinanceStatus.CLOSED, FinanceStatus.FORECLOSED, FinanceStatus.PENDING_APPROVAL);

    private final FollowupJpaRepository followupJpaRepository;
    private final FinanceJpaRepository financeJpaRepository;
    private final CustomerJpaRepository customerJpaRepository;
    private final EmiInstallmentJpaRepository emiInstallmentJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final FinanceSummaryAssembler financeSummaryAssembler;
    private final AuditService auditService;

    public record Queue(List<FollowupQueueItem> due, List<FollowupQueueItem> missed,
                        List<FollowupQueueItem> upcoming, List<FollowupQueueItem> uncalled) {
    }

    @Transactional
    public FollowupResponse record(String staffId, boolean isAdmin, FollowupRequest request) {
        Finance finance = financeJpaRepository.findByFinanceNumber(request.getFinanceId().trim())
                .or(() -> financeJpaRepository.findById(request.getFinanceId().trim()))
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));
        Customer customer = customerJpaRepository.findById(finance.getCustomerId())
                .orElseThrow(() -> new DomainException("Finance not found", HttpStatus.NOT_FOUND));
        if (!isAdmin && customer.getAssignedStaffId() != null && !customer.getAssignedStaffId().equals(staffId)) {
            throw new DomainException("Finance not found", HttpStatus.NOT_FOUND);
        }

        String callStatus = request.getCallStatus() == null || request.getCallStatus().isBlank()
                ? DEFAULT_CALL_STATUS : request.getCallStatus().trim();
        String remarks = request.getRemarks() == null || request.getRemarks().isBlank() ? null : request.getRemarks().trim();

        Followup saved = followupJpaRepository.save(Followup.builder()
                .financeId(finance.getFinanceId())
                .customerId(customer.getCustomerId())
                .staffId(staffId)
                .callStatus(callStatus)
                .response(request.getResponse().trim())
                .promiseDate(request.getPromiseDate())
                .nextDate(request.getNextDate())
                .remarks(remarks)
                .build());

        String staffName = userJpaRepository.findById(staffId).map(Users::getFullName).orElse("Unknown");
        auditService.log(staffId, "Follow-up added", callStatus + " — "
                + (customer.getFullName() != null ? customer.getFullName() : customer.getPhoneNumber())
                + " on " + finance.getFinanceNumber(), finance.getFinanceNumber());

        return new FollowupResponse(saved.getFollowupId(), customer.getCustomerId(), finance.getFinanceNumber(),
                LocalDate.now(), callStatus, saved.getResponse(),
                saved.getPromiseDate() == null ? "" : saved.getPromiseDate().toString(),
                saved.getNextDate(), remarks == null ? "" : remarks, staffId, staffName);
    }

    @Transactional(readOnly = true)
    public Queue queue(String callerId, boolean isAdmin) {
        return queue(callerId, isAdmin, true);
    }

    // Each unfinished finance is placed by its LATEST follow-up: due (next call today), missed (before today),
    // upcoming (after today). A finance with an overdue EMI and no follow-up yet goes to uncalled.
    @Transactional(readOnly = true)
    public Queue queue(String callerId, boolean allCustomers, boolean includeUnassigned) {
        List<Object[]> rows = financeJpaRepository.findInScope(FINISHED, allCustomers, callerId, includeUnassigned);
        if (rows.isEmpty()) {
            return new Queue(List.of(), List.of(), List.of(), List.of());
        }
        List<String> financeIds = rows.stream().map(r -> ((Finance) r[0]).getFinanceId()).toList();

        Map<String, List<EmiInstallment>> installments = emiInstallmentJpaRepository.findAllByFinanceIdIn(financeIds)
                .stream().sorted(Comparator.comparing(EmiInstallment::getInstallmentNo))
                .collect(Collectors.groupingBy(EmiInstallment::getFinanceId));

        Map<String, Followup> latest = new HashMap<>();
        for (Followup f : followupJpaRepository.findAllByFinanceIdIn(financeIds)) {
            latest.merge(f.getFinanceId(), f, (a, b) -> b.getCreatedAt().isAfter(a.getCreatedAt()) ? b : a);
        }

        Map<String, List<Payment>> verifiedPayments = paymentJpaRepository
                .findAllByFinanceIdInAndStatus(financeIds, PaymentStatus.VERIFIED).stream()
                .collect(Collectors.groupingBy(Payment::getFinanceId));

        Map<String, String> staffNames = new HashMap<>();
        userJpaRepository.findAllById(latest.values().stream().map(Followup::getStaffId).collect(Collectors.toSet()))
                .forEach((Users u) -> staffNames.put(u.getUserID(), u.getFullName()));

        LocalDate today = LocalDate.now();
        List<FollowupQueueItem> due = new ArrayList<>();
        List<FollowupQueueItem> missed = new ArrayList<>();
        List<FollowupQueueItem> upcoming = new ArrayList<>();
        List<FollowupQueueItem> uncalled = new ArrayList<>();

        for (Object[] r : rows) {
            Finance f = (Finance) r[0];
            Customer c = (Customer) r[1];
            var summary = financeSummaryAssembler.assemble(f, c, "", null,
                    installments.getOrDefault(f.getFinanceId(), List.of()), today).dto();
            Followup last = latest.get(f.getFinanceId());

            if (last == null) {
                if (summary.getOverdueCount() > 0) {
                    uncalled.add(new FollowupQueueItem(f.getFinanceNumber(), c.getCustomerId(), c.getFullName(),
                            c.getPhoneNumber(), summary.getOverdueAmount(), summary.getDpd(), summary.getNextDue(),
                            null, null, null, null, null, null, null));
                }
                continue;
            }

            LocalDate lastDate = last.getCreatedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
            FollowupQueueItem item = new FollowupQueueItem(f.getFinanceNumber(), c.getCustomerId(), c.getFullName(),
                    c.getPhoneNumber(), summary.getOverdueAmount(), summary.getDpd(), summary.getNextDue(), lastDate,
                    last.getCallStatus(), last.getResponse(), last.getPromiseDate(),
                    promiseOutcome(last, lastDate, verifiedPayments.getOrDefault(f.getFinanceId(), List.of()), today),
                    last.getNextDate(), staffNames.getOrDefault(last.getStaffId(), "Unknown"));

            if (last.getNextDate().isEqual(today)) {
                due.add(item);
            } else if (last.getNextDate().isBefore(today)) {
                missed.add(item);
            } else {
                upcoming.add(item);
            }
        }

        due.sort(Comparator.comparingInt(FollowupQueueItem::getDpd).reversed());
        missed.sort(Comparator.comparing(FollowupQueueItem::getNextDate));
        upcoming.sort(Comparator.comparing(FollowupQueueItem::getNextDate));
        uncalled.sort(Comparator.comparing(FollowupQueueItem::getOverdueAmount).reversed());
        return new Queue(due, missed, upcoming, uncalled);
    }

    private String promiseOutcome(Followup last, LocalDate lastDate, List<Payment> payments, LocalDate today) {
        LocalDate promise = last.getPromiseDate();
        if (promise == null) {
            return null;
        }
        boolean kept = payments.stream().anyMatch(p -> !p.getPaymentDate().isBefore(lastDate)
                && !p.getPaymentDate().isAfter(promise));
        if (kept) {
            return "kept";
        }
        return promise.isBefore(today) ? "broken" : "open";
    }
}
