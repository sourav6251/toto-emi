package com.emi.controller;

import com.emi.dto.response.ApiResponse;
import com.emi.service.ReportService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@AllArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // Monthly collection performance: GET /reports/monthly?month=2026-10 (defaults to the current month)
    @GetMapping("/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> monthlyReport(@RequestParam(value = "month", required = false) String month) {
        ApiResponse body = ApiResponse.success();
        reportService.monthly(month).forEach(body::add);
        return ResponseEntity.ok(body);
    }
}
