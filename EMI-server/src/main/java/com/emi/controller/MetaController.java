package com.emi.controller;

import com.emi.config.properties.CompanyProperties;
import com.emi.dto.response.ApiResponse;
import com.emi.model.reposetory.UserJpaRepository;
import com.emi.utils.DocumentTypes;
import com.emi.utils.enums.PaymentMethod;
import com.emi.utils.enums.ProductType;
import com.emi.utils.enums.Role;
import com.emi.utils.enums.UserStatus;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/meta")
@AllArgsConstructor
public class MetaController {

    private final CompanyProperties companyProperties;
    private final UserJpaRepository userJpaRepository;

    // Defaults and lists the forms need. Any signed-in user may read it; it changes only when settings change.
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMeta() {
        CompanyProperties c = companyProperties;
        CompanyProperties.Defaults d = c.getDefaults();

        Map<String, Object> company = new LinkedHashMap<>();
        company.put("name", c.getName());
        company.put("short", c.getShortName());
        company.put("address", c.getAddress());
        company.put("phone", c.getPhone());
        company.put("email", c.getEmail());
        company.put("gstin", c.getGstin());

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("rate", d.getRate());
        defaults.put("interestType", d.getInterestType());
        defaults.put("tenure", d.getTenure());
        defaults.put("dueDay", d.getDueDay());
        defaults.put("graceDays", d.getGraceDays());
        defaults.put("lateFee", d.getLateFee());
        defaults.put("processingFeePct", d.getProcessingFeePct());
        defaults.put("minDownPaymentPct", d.getMinDownPaymentPct());

        List<String> paymentMethods = List.of(PaymentMethod.UPI, PaymentMethod.CASH, PaymentMethod.BANK_TRANSFER,
                PaymentMethod.CHEQUE, PaymentMethod.PAYMENT_GATEWAY).stream().map(PaymentMethod::label).toList();

        var staff = userJpaRepository.findAllByRole(Role.CUSTOMER_CARE, Sort.by("fullName")).stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(u -> Map.of("id", u.getUserID(), "name", u.getFullName() == null ? "" : u.getFullName()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success()
                .add("company", company)
                .add("upiId", c.getUpi())
                .add("defaults", defaults)
                .add("paymentMethods", paymentMethods)
                .add("productTypes", Arrays.stream(ProductType.values()).map(Enum::name).toList())
                .add("requiredDocs", DocumentTypes.ALL)
                .add("staff", staff));
    }
}
