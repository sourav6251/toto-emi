package com.emi.controller;

import com.emi.dto.response.ApiResponse;
import com.emi.service.FinanceService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/do-numbers")
@AllArgsConstructor
public class DoNumberController {

    private final FinanceService financeService;

    // Tells the create form whether a DO number can be used; POST /finances re-checks, so this is advisory
    @GetMapping("/check")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> checkDoNumber(@RequestParam("doNumber") String doNumber) {
        var check = financeService.checkDoNumber(doNumber);
        return ResponseEntity.ok(ApiResponse.success()
                .add("doNumber", check.doNumber())
                .add("available", check.available())
                .add("message", check.message()));
    }
}
