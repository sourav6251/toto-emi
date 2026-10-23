package com.emi.service;

import com.emi.dto.response.EmiItem;
import com.emi.exception.DomainException;
import com.emi.model.entity.Asset;
import com.emi.model.entity.Customer;
import com.emi.model.entity.EmiInstallment;
import com.emi.model.entity.Finance;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.AssetJpaRepository;
import com.emi.model.reposetory.EmiInstallmentJpaRepository;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.InstallmentStatus;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class EmiService {

    private static final int MAX_ITEMS = 300;
    private static final int MAX_DAYS = 366;
    private static final Set<String> STATUSES = Set.of("overdue", "due", "partial", "upcoming", "paid");

    private final EmiInstallmentJpaRepository emiInstallmentJpaRepository;
    private final AssetJpaRepository assetJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public record Worklist(List<EmiItem> items, int total, BigDecimal totalAmount) {
    }

    // An EMI with its finance and customer and its derived status, ready to become an EmiItem
    public record Row(EmiInstallment installment, Finance finance, Customer customer, String status, int daysLate) {
    }

    // Collection worklist. Care staff see their own and unassigned customers, Admin sees all.
    // Pending-approval and cancelled finances are never collected, so their EMIs are left out.
    @Transactional(readOnly = true)
    public Worklist worklist(String callerId, boolean isAdmin, String status, String q, Integer days) {
        String wanted = status == null || status.isBlank() ? "overdue" : status.trim().toLowerCase();
        if (!STATUSES.contains(wanted)) {
            throw new DomainException("status must be one of overdue, due, partial, upcoming, paid");
        }
        if (days != null && (days < 1 || days > MAX_DAYS)) {
            throw new DomainException("days must be between 1 and " + MAX_DAYS);
        }

        LocalDate today = LocalDate.now();
        LocalDate from = LocalDate.of(1900, 1, 1);
        LocalDate to = LocalDate.of(9999, 12, 31);
        List<InstallmentStatus> dbStatuses;
        switch (wanted) {
            case "upcoming" -> {
                dbStatuses = List.of(InstallmentStatus.PENDING);
                from = today.plusDays(1);
                to = today.plusDays(days != null ? days : 30);
            }
            case "paid" -> {
                dbStatuses = List.of(InstallmentStatus.PAID);
                from = today.minusDays(days != null ? days : 45);
            }
            case "partial" -> dbStatuses = List.of(InstallmentStatus.PARTIALLY_PAID);
            case "due" -> {
                dbStatuses = List.of(InstallmentStatus.PENDING);
                to = today;
            }
            default -> { // overdue: unpaid or part-paid, and already due
                dbStatuses = List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIALLY_PAID);
                to = today;
            }
        }

        String pattern = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        List<Object[]> rows = emiInstallmentJpaRepository.findWorklist(
                List.of(FinanceStatus.CANCELLED, FinanceStatus.PENDING_APPROVAL),
                dbStatuses, from, to, isAdmin, callerId, pattern);

        // Derive the status of each row; only rows that match the requested one are kept
        List<Row> matches = new ArrayList<>();
        for (Object[] r : rows) {
            EmiInstallment i = (EmiInstallment) r[0];
            Finance f = (Finance) r[1];
            Customer c = (Customer) r[2];
            String derived = derive(i, f, today);
            if (derived.equals(wanted)) {
                matches.add(row(i, f, c, derived, today));
            }
        }

        Comparator<Row> byDue = Comparator.comparing((Row r) -> r.installment().getDueDate());
        matches.sort(switch (wanted) {
            case "overdue" -> Comparator.comparingInt(Row::daysLate).reversed().thenComparing(byDue);
            case "paid" -> byDue.reversed();
            default -> byDue;
        });

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Row r : matches) {
            totalAmount = totalAmount.add("paid".equals(wanted)
                    ? r.installment().getAmountPaid()
                    : unpaid(r.installment()));
        }

        List<Row> page = matches.size() > MAX_ITEMS ? matches.subList(0, MAX_ITEMS) : matches;
        return new Worklist(toItems(page), matches.size(), totalAmount);
    }

    // Builds a row with its days late (0 once paid or before the due date)
    public Row row(EmiInstallment i, Finance f, Customer c, String status, LocalDate today) {
        long late = ChronoUnit.DAYS.between(i.getDueDate(), today);
        return new Row(i, f, c, status, "paid".equals(status) ? 0 : (int) Math.max(late, 0));
    }

    // Joins rows with their product and the staff member responsible for collection
    @Transactional(readOnly = true)
    public List<EmiItem> toItems(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Asset> assets = assetJpaRepository
                .findAllById(rows.stream().map(r -> r.finance().getAssetId()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Asset::getAssetId, Function.identity()));
        Map<String, String> staffNames = new HashMap<>();
        userJpaRepository.findAllById(rows.stream().map(r -> r.customer().getAssignedStaffId())
                        .filter(id -> id != null).collect(Collectors.toSet()))
                .forEach((Users u) -> staffNames.put(u.getUserID(), u.getFullName()));

        return rows.stream().map(r -> {
            EmiInstallment i = r.installment();
            Finance f = r.finance();
            Customer c = r.customer();
            Asset asset = assets.get(f.getAssetId());
            String staffId = c.getAssignedStaffId();
            return new EmiItem(
                    f.getFinanceNumber() + "#" + i.getInstallmentNo(),
                    f.getFinanceNumber(),
                    c.getCustomerId(),
                    c.getFullName(),
                    c.getPhoneNumber(),
                    asset != null && asset.getProductType() != null ? asset.getProductType().name() : "Other",
                    asset != null ? asset.getVehicleNumber() : null,
                    i.getInstallmentNo(),
                    f.getTenureMonths(),
                    i.getDueDate(),
                    i.getEmiAmount(),
                    i.getAmountPaid(),
                    i.getPaidAt(),
                    r.status(),
                    "overdue".equals(r.status()),
                    r.daysLate(),
                    staffId == null ? "Unassigned" : staffNames.getOrDefault(staffId, "Unassigned"));
        }).toList();
    }

    // Upcoming / due / overdue are derived from the due date and the finance's grace days, never stored
    public String derive(EmiInstallment i, Finance f, LocalDate today) {
        if (i.getStatus() == InstallmentStatus.PAID || i.getAmountPaid().compareTo(i.getEmiAmount()) >= 0) {
            return "paid";
        }
        int grace = f.getGraceDays() != null ? f.getGraceDays() : 0;
        long late = ChronoUnit.DAYS.between(i.getDueDate(), today);
        if (late > grace) {
            return "overdue"; // includes part-paid EMIs past grace
        }
        if (i.getAmountPaid().signum() > 0) {
            return "partial";
        }
        return late >= 0 ? "due" : "upcoming";
    }

    public BigDecimal unpaid(EmiInstallment i) {
        return i.getEmiAmount().subtract(i.getAmountPaid()).max(BigDecimal.ZERO);
    }
}
