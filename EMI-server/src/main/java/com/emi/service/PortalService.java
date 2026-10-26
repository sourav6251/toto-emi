package com.emi.service;

import com.emi.config.properties.CompanyProperties;
import com.emi.dto.response.FinanceDetailResponse;
import com.emi.model.entity.Finance;
import com.emi.model.reposetory.FinanceJpaRepository;
import com.emi.utils.enums.FinanceStatus;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@AllArgsConstructor
public class PortalService {

    private final CustomerService customerService;
    private final FinanceService financeService;
    private final FinanceJpaRepository financeJpaRepository;
    private final CompanyProperties companyProperties;

    public Map<String, Object> portal(String callerId, Authentication authentication) {
        var customer = customerService.getCustomerDetail(callerId, authentication, callerId);

        List<FinanceDetailResponse> finances = new ArrayList<>();
        for (Finance f : financeJpaRepository.findAllByCustomerId(customer.getCustomerId()).stream()
                .filter(f -> f.getStatus() != FinanceStatus.CANCELLED)
                .sorted(Comparator.comparing(Finance::getCreatedAt).reversed()).toList()) {
            finances.add(financeService.getFinanceDetail(callerId, authentication, f.getFinanceNumber()));
        }

        CompanyProperties c = companyProperties;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", customer);
        result.put("finances", finances);
        result.put("upiId", c.getUpi());
        result.put("upiLinks", upiLinks(finances));
        return result;
    }

    private List<Map<String, Object>> upiLinks(List<FinanceDetailResponse> finances) {
        String upi = companyProperties.getUpi();
        if (upi == null || upi.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> links = new ArrayList<>();
        for (FinanceDetailResponse f : finances) {
            if (f.getNextDue() == null || f.getNextDue().signum() <= 0 || f.getSchedule() == null) {
                continue;
            }
            var next = f.getSchedule().stream()
                    .filter(r -> !Set.of("paid", "waived", "cancelled").contains(r.getStatus()))
                    .findFirst();
            if (next.isEmpty()) {
                continue;
            }
            String note = f.getFinanceNumber() + " EMI " + next.get().getNo();
            BigDecimal amount = f.getNextDue().setScale(2, RoundingMode.HALF_UP);
            String link = "upi://pay?pa=" + encode(upi)
                    + "&pn=" + encode(companyProperties.getName() == null ? "" : companyProperties.getName())
                    + "&am=" + amount.toPlainString()
                    + "&cu=INR&tn=" + encode(note);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("financeId", f.getFinanceNumber());
            row.put("emiNo", next.get().getNo());
            row.put("amount", amount);
            row.put("note", note);
            row.put("link", link);
            links.add(row);
        }
        return links;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
