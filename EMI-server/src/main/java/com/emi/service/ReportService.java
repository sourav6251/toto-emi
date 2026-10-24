package com.emi.service;

import com.emi.exception.DomainException;
import com.emi.model.entity.Customer;
import com.emi.model.entity.EmiInstallment;
import com.emi.model.entity.Finance;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.CustomerJpaRepository;
import com.emi.model.reposetory.EmiInstallmentJpaRepository;
import com.emi.model.reposetory.FinanceJpaRepository;
import com.emi.model.reposetory.PaymentJpaRepository;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.InstallmentStatus;
import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ReportService {

    private final FinanceJpaRepository financeJpaRepository;
    private final EmiInstallmentJpaRepository emiInstallmentJpaRepository;
    private final CustomerJpaRepository customerJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final EmiService emiService;

    private static final class CustomerMonth {
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        boolean overdue;
        boolean pending;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> monthly(String month) {
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new DomainException("month must be in YYYY-MM format");
        }
        LocalDate today = LocalDate.now();

        List<Object[]> rows = financeJpaRepository.findInScope(List.of(FinanceStatus.CANCELLED), true, "", true);
        Map<String, List<EmiInstallment>> installments = rows.isEmpty() ? Map.of()
                : emiInstallmentJpaRepository.findAllByFinanceIdIn(
                                rows.stream().map(r -> ((Finance) r[0]).getFinanceId()).toList()).stream()
                        .collect(Collectors.groupingBy(EmiInstallment::getFinanceId));

        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        int emiCount = 0;
        int paidCount = 0;
        Map<String, CustomerMonth> byCustomer = new HashMap<>();
        Map<String, Customer> customers = new HashMap<>();

        for (Object[] r : rows) {
            Finance f = (Finance) r[0];
            Customer c = (Customer) r[1];
            customers.put(c.getCustomerId(), c);
            for (EmiInstallment i : installments.getOrDefault(f.getFinanceId(), List.of())) {
                if (i.getStatus() == InstallmentStatus.WAIVED || i.getStatus() == InstallmentStatus.CANCELLED
                        || !YearMonth.from(i.getDueDate()).equals(ym)) {
                    continue;
                }
                BigDecimal paid = i.getAmountPaid().min(i.getEmiAmount());
                String status = emiService.derive(i, f, today);
                expected = expected.add(i.getEmiAmount());
                received = received.add(paid);
                emiCount++;
                CustomerMonth cm = byCustomer.computeIfAbsent(c.getCustomerId(), k -> new CustomerMonth());
                cm.expected = cm.expected.add(i.getEmiAmount());
                cm.received = cm.received.add(paid);
                if ("paid".equals(status)) {
                    paidCount++;
                } else if ("overdue".equals(status)) {
                    overdue = overdue.add(emiService.unpaid(i));
                    cm.overdue = true;
                } else {
                    cm.pending = true;
                }
            }
        }

        List<Map<String, Object>> staff = new ArrayList<>();
        for (Users u : userJpaRepository.findAllByRole(Role.CUSTOMER_CARE, Sort.by("fullName"))) {
            if (u.getStatus() != UserStatus.ACTIVE) {
                continue;
            }
            int paid = 0;
            int pending = 0;
            int overdueCustomers = 0;
            BigDecimal staffExpected = BigDecimal.ZERO;
            BigDecimal staffReceived = BigDecimal.ZERO;
            for (Map.Entry<String, CustomerMonth> e : byCustomer.entrySet()) {
                if (!u.getUserID().equals(customers.get(e.getKey()).getAssignedStaffId())) {
                    continue;
                }
                CustomerMonth cm = e.getValue();
                if (cm.overdue) {
                    overdueCustomers++;
                } else if (cm.pending) {
                    pending++;
                } else {
                    paid++;
                }
                staffExpected = staffExpected.add(cm.expected);
                staffReceived = staffReceived.add(cm.received);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getUserID());
            row.put("name", u.getFullName());
            row.put("assigned", customerJpaRepository.countByAssignedStaffId(u.getUserID()));
            row.put("paid", paid);
            row.put("pending", pending);
            row.put("overdue", overdueCustomers);
            row.put("expected", staffExpected);
            row.put("received", staffReceived);
            row.put("rate", percent(staffReceived, staffExpected));
            staff.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", ym.toString());
        result.put("label", ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear());
        result.put("expected", expected);
        result.put("received", received);
        result.put("pending", expected.subtract(received));
        result.put("overdue", overdue);
        result.put("collectionPct", percent(received, expected));
        result.put("cashReceived", paymentJpaRepository.sumVerified(ym.atDay(1), ym.atEndOfMonth(), true, ""));
        result.put("emiCount", emiCount);
        result.put("paidCount", paidCount);
        result.put("staff", staff);
        return result;
    }

    private BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
