package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.response.ApiResponse;
import com.emi.service.DashboardService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@AllArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // Admin home: lending and collection totals, 6-month trend, overdue ageing, top overdue loans
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminDashboard() {
        return respond(dashboardService.admin());
    }

    // Care home: today's collection targets and overdue accounts, for the caller's assigned customers only
    @GetMapping("/care")
    @PreAuthorize("hasRole('CUSTOMER_CARE')")
    public ResponseEntity<?> careDashboard(@CurrentUserId String staffId) {
        return respond(dashboardService.care(staffId));
    }

    private ResponseEntity<?> respond(java.util.Map<String, Object> dashboard) {
        ApiResponse body = ApiResponse.success();
        dashboard.forEach(body::add);
        return ResponseEntity.ok(body);
    }
}
