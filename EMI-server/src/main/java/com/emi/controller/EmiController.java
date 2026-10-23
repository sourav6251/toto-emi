package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.response.ApiResponse;
import com.emi.service.EmiService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/emis")
@AllArgsConstructor
public class EmiController {

    private final EmiService emiService;

    // Daily collection worklist: status is overdue (default), due, partial, upcoming or paid;
    // q matches customer name, mobile or finance number; days widens upcoming (default 30) and paid (default 45)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> listEmis(@CurrentUserId String staffId,
                                      Authentication authentication,
                                      @RequestParam(value = "status", required = false) String status,
                                      @RequestParam(value = "q", required = false) String q,
                                      @RequestParam(value = "days", required = false) Integer days) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        var result = emiService.worklist(staffId, isAdmin, status, q, days);
        return ResponseEntity.ok(ApiResponse.success()
                .add("items", result.items())
                .add("total", result.total())
                .add("totalAmount", result.totalAmount()));
    }
}
