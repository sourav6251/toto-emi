package com.emi.service;

import com.emi.dto.response.CustomerDetailResponse.FinanceSummaryDto;
import com.emi.model.entity.Asset;
import com.emi.model.entity.Customer;
import com.emi.model.entity.EmiInstallment;
import com.emi.model.entity.Finance;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.AssetJpaRepository;
import com.emi.model.reposetory.CustomerJpaRepository;
import com.emi.model.reposetory.EmiInstallmentJpaRepository;
import com.emi.model.reposetory.FinanceJpaRepository;
import com.emi.model.reposetory.PaymentJpaRepository;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.InstallmentStatus;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Figures are worked out from the finances and EMIs in the caller's scope. Cancelled finances never count;
// waived and cancelled EMIs are ignored. The volume is small, so everything is computed in memory.
@Service
@AllArgsConstructor
public class DashboardService {

    private static final List<FinanceStatus> EXCLUDED = List.of(FinanceStatus.CANCELLED, FinanceStatus.PENDING_APPROVAL);
    private static final int TOP_OVERDUE = 8;
    private static final int UPCOMING_DAYS = 7;

    private final CustomerJpaRepository customerJpaRepository;
    private final FinanceJpaRepository financeJpaRepository;
    private final EmiInstallmentJpaRepository emiInstallmentJpaRepository;
    private final AssetJpaRepository assetJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final FinanceSummaryAssembler financeSummaryAssembler;
    private final EmiService emiService;
    private final FollowupService followupService;

    private record Loaded(List<Finance> finances, Map<String, Customer> customers,
                          Map<String, List<EmiInstallment>> installments) {

        List<EmiInstallment> installmentsOf(Finance f) {
            return installments.getOrDefault(f.getFinanceId(), List.of());
        }
    }

    private Loaded load(List<Object[]> rows) {
        List<Finance> finances = new ArrayList<>();
        Map<String, Customer> customers = new HashMap<>();
        for (Object[] r : rows) {
            finances.add((Finance) r[0]);
            Customer c = (Customer) r[1];
            customers.put(c.getCustomerId(), c);
        }
        Map<String, List<EmiInstallment>> installments = finances.isEmpty() ? Map.of()
                : emiInstallmentJpaRepository.findAllByFinanceIdIn(finances.stream().map(Finance::getFinanceId).toList())
                        .stream()
                        .sorted(Comparator.comparing(EmiInstallment::getInstallmentNo))
                        .collect(Collectors.groupingBy(EmiInstallment::getFinanceId));
        return new Loaded(finances, customers, installments);
    }

    private boolean live(EmiInstallment i) {
        return i.getStatus() != InstallmentStatus.WAIVED && i.getStatus() != InstallmentStatus.CANCELLED;
    }

    private boolean isPaid(EmiInstallment i) {
        return i.getStatus() == InstallmentStatus.PAID || i.getAmountPaid().compareTo(i.getEmiAmount()) >= 0;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> admin() {
        LocalDate today = LocalDate.now();
        Loaded data = load(financeJpaRepository.findInScope(EXCLUDED, true, "", true));

        Map<String, Asset> assets = data.finances().isEmpty() ? Map.of()
                : assetJpaRepository.findAllById(data.finances().stream().map(Finance::getAssetId).toList()).stream()
                        .collect(Collectors.toMap(Asset::getAssetId, Function.identity()));
        Map<String, String> staffNames = staffNames(data.customers().values());

        BigDecimal totalFinanced = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        int activeFinance = 0;
        int overdueAccounts = 0;
        int overdueEmis = 0;
        Map<String, BigDecimal> productAmount = new LinkedHashMap<>();
        Map<String, Integer> productCount = new LinkedHashMap<>();
        List<FinanceSummaryDto> overdueFinances = new ArrayList<>();

        BigDecimal[] ageAmount = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        int[] ageCount = new int[4];

        YearMonth thisMonth = YearMonth.from(today);
        List<YearMonth> months = new ArrayList<>();
        for (int k = 5; k >= 0; k--) {
            months.add(thisMonth.minusMonths(k));
        }
        Map<YearMonth, BigDecimal> monthReceived = new HashMap<>();
        Map<YearMonth, BigDecimal> monthPending = new HashMap<>();
        BigDecimal monthExpected = BigDecimal.ZERO;
        BigDecimal monthReceivedNow = BigDecimal.ZERO;
        int upcoming7 = 0;

        for (Finance f : data.finances()) {
            Customer c = data.customers().get(f.getCustomerId());
            Asset asset = assets.get(f.getAssetId());
            List<EmiInstallment> installments = data.installmentsOf(f);
            String assignedTo = c.getAssignedStaffId() == null ? "Unassigned"
                    : staffNames.getOrDefault(c.getAssignedStaffId(), "Unassigned");
            var s = financeSummaryAssembler.assemble(f, c, assignedTo, asset, installments, today);

            totalFinanced = totalFinanced.add(f.getFinanceAmount());
            outstanding = outstanding.add(s.outstanding());
            overdue = overdue.add(s.overdueAmount());
            if ("active".equals(s.dto().getStatus())) {
                activeFinance++;
            }
            if (s.dto().getOverdueCount() > 0) {
                overdueAccounts++;
                overdueEmis += s.dto().getOverdueCount();
                overdueFinances.add(s.dto());
            }
            String type = s.dto().getProduct().getType();
            productAmount.merge(type, f.getFinanceAmount(), BigDecimal::add);
            productCount.merge(type, 1, Integer::sum);

            for (EmiInstallment i : installments) {
                if (!live(i)) {
                    continue;
                }
                YearMonth due = YearMonth.from(i.getDueDate());
                BigDecimal paid = i.getAmountPaid().min(i.getEmiAmount());
                if (months.contains(due)) {
                    monthReceived.merge(due, paid, BigDecimal::add);
                    monthPending.merge(due, i.getEmiAmount().subtract(paid), BigDecimal::add);
                }
                if (due.equals(thisMonth)) {
                    monthExpected = monthExpected.add(i.getEmiAmount());
                    monthReceivedNow = monthReceivedNow.add(paid);
                }
                if (!isPaid(i) && i.getDueDate().isAfter(today) && !i.getDueDate().isAfter(today.plusDays(UPCOMING_DAYS))) {
                    upcoming7++;
                }
                if ("overdue".equals(emiService.derive(i, f, today))) {
                    int bucket = bucketOf(ChronoUnit.DAYS.between(i.getDueDate(), today));
                    ageAmount[bucket] = ageAmount[bucket].add(emiService.unpaid(i));
                    ageCount[bucket]++;
                }
            }
        }

        BigDecimal totalCollected = paymentJpaRepository.sumVerified(LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 31), true, "");
        BigDecimal todayCollection = paymentJpaRepository.sumVerified(today, today, true, "");
        BigDecimal monthCollection = paymentJpaRepository.sumVerified(thisMonth.atDay(1), thisMonth.atEndOfMonth(), true, "");

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalCustomers", customerJpaRepository.count());
        kpis.put("activeFinance", activeFinance);
        kpis.put("totalFinanced", totalFinanced);
        kpis.put("totalCollected", totalCollected);
        kpis.put("outstanding", outstanding);
        kpis.put("overdue", overdue);
        kpis.put("overdueAccounts", overdueAccounts);
        kpis.put("todayCollection", todayCollection);
        kpis.put("monthCollection", monthCollection);
        kpis.put("monthExpected", monthExpected);
        kpis.put("monthReceived", monthReceivedNow);
        kpis.put("upcoming7", upcoming7);
        kpis.put("overdueEmis", overdueEmis);
        kpis.put("pendingVerification", paymentJpaRepository.countPending(true, ""));

        List<Map<String, Object>> monthly = new ArrayList<>();
        for (YearMonth m : months) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", m.toString());
            row.put("label", m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            row.put("received", monthReceived.getOrDefault(m, BigDecimal.ZERO));
            row.put("pending", monthPending.getOrDefault(m, BigDecimal.ZERO));
            monthly.add(row);
        }

        String[] ageLabels = {"1–30 days", "31–60 days", "61–90 days", "90+ days"};
        List<Map<String, Object>> ageing = new ArrayList<>();
        for (int b = 0; b < 4; b++) {
            ageing.add(labelled(ageLabels[b], ageAmount[b], ageCount[b]));
        }

        List<Map<String, Object>> byProduct = new ArrayList<>();
        productAmount.forEach((label, amount) -> byProduct.add(labelled(label, amount, productCount.get(label))));

        List<FinanceSummaryDto> topOverdue = overdueFinances.stream()
                .sorted(Comparator.comparing(FinanceSummaryDto::getOverdueAmount).reversed())
                .limit(TOP_OVERDUE).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", today);
        result.put("kpis", kpis);
        result.put("monthly", monthly);
        result.put("ageing", ageing);
        result.put("byProduct", byProduct);
        result.put("topOverdue", topOverdue);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> care(String staffId) {
        LocalDate today = LocalDate.now();
        Loaded data = load(financeJpaRepository.findInScope(EXCLUDED, false, staffId, false));

        List<EmiService.Row> todayRows = new ArrayList<>();
        List<EmiService.Row> pendingRows = new ArrayList<>();
        List<EmiService.Row> overdueRows = new ArrayList<>();
        List<EmiService.Row> upcomingRows = new ArrayList<>();
        Set<String> overdueCustomers = new HashSet<>();

        for (Finance f : data.finances()) {
            Customer c = data.customers().get(f.getCustomerId());
            EmiService.Row oldestOverdue = null;
            for (EmiInstallment i : data.installmentsOf(f)) {
                if (!live(i) || isPaid(i)) {
                    continue;
                }
                String status = emiService.derive(i, f, today);
                boolean notYetOverdue = "due".equals(status) || "partial".equals(status);
                if (notYetOverdue && i.getDueDate().isEqual(today)) {
                    todayRows.add(emiService.row(i, f, c, status, today));
                }
                if (notYetOverdue && !i.getDueDate().isAfter(today)) {
                    pendingRows.add(emiService.row(i, f, c, status, today));
                }
                if ("overdue".equals(status) && oldestOverdue == null) {
                    oldestOverdue = emiService.row(i, f, c, status, today);
                }
                if (i.getDueDate().isAfter(today) && !i.getDueDate().isAfter(today.plusDays(UPCOMING_DAYS))) {
                    upcomingRows.add(emiService.row(i, f, c, status, today));
                }
            }
            if (oldestOverdue != null) {
                overdueRows.add(oldestOverdue);
                overdueCustomers.add(c.getCustomerId());
            }
        }

        Comparator<EmiService.Row> byDue = Comparator.comparing(r -> r.installment().getDueDate());
        todayRows.sort(byDue);
        pendingRows.sort(byDue);
        upcomingRows.sort(byDue);
        overdueRows.sort(Comparator.comparingInt(EmiService.Row::daysLate).reversed());

        var queue = followupService.queue(staffId, false, false);
        BigDecimal paidTodayAmount = paymentJpaRepository.sumVerified(today, today, false, staffId);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("todayDue", todayRows.size());
        kpis.put("upcoming7", upcomingRows.size());
        kpis.put("paidToday", paymentJpaRepository.countVerified(today, today, false, staffId));
        kpis.put("paidTodayAmount", paidTodayAmount);
        kpis.put("pending", pendingRows.size());
        kpis.put("overdueCustomers", overdueCustomers.size());
        kpis.put("followupsDue", queue.due().size() + queue.missed().size());
        kpis.put("assigned", customerJpaRepository.countByAssignedStaffId(staffId));

        Map<String, Object> lists = new LinkedHashMap<>();
        lists.put("today", emiService.toItems(todayRows));
        lists.put("pending", emiService.toItems(pendingRows));
        lists.put("overdue", emiService.toItems(overdueRows));
        lists.put("upcoming", emiService.toItems(upcomingRows));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", today);
        result.put("kpis", kpis);
        result.put("lists", lists);
        return result;
    }

    private int bucketOf(long daysLate) {
        if (daysLate <= 30) {
            return 0;
        }
        if (daysLate <= 60) {
            return 1;
        }
        return daysLate <= 90 ? 2 : 3;
    }

    private Map<String, Object> labelled(String label, BigDecimal amount, int count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("amount", amount);
        row.put("count", count);
        return row;
    }

    private Map<String, String> staffNames(java.util.Collection<Customer> customers) {
        Map<String, String> names = new HashMap<>();
        userJpaRepository.findAllById(customers.stream().map(Customer::getAssignedStaffId)
                        .filter(id -> id != null).collect(Collectors.toSet()))
                .forEach((Users u) -> names.put(u.getUserID(), u.getFullName()));
        return names;
    }
}
