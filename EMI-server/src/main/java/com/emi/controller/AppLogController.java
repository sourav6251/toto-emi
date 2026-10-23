package com.emi.controller;

import com.emi.dto.response.ApiResponse;
import com.emi.service.AuditService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
public class AppLogController {

    private final AuditService auditService;

    /**
     * Public API returning application activity log data (AuditLog).
     * Accessible without authentication.
     * Optional filters: action, userId, limit (default 50, max 200).
     */
    @GetMapping({"/public/app-logs", "/app-logs"})
    public ResponseEntity<?> getAppLogs(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        List<AuditService.AppLogItem> logs = auditService.getAppLogs(action, userId, limit);
        return ResponseEntity.ok(ApiResponse.success()
                .add("items", logs)
                .add("total", logs.size()));
    }
}

