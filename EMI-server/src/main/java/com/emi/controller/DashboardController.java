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

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@AllArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminDashboard() {
        return respond(dashboardService.admin());
    }

    @GetMapping("/care")
    @PreAuthorize("hasRole('CUSTOMER_CARE')")
    public ResponseEntity<?> careDashboard(@CurrentUserId String staffId) {
        return respond(dashboardService.care(staffId));
    }

    private ResponseEntity<?> respond(Map<String, Object> dashboard) {
        ApiResponse body = ApiResponse.success();
        dashboard.forEach(body::add);
        return ResponseEntity.ok(body);
    }
}
