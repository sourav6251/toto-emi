package com.emi.controller;

import com.emi.dto.response.ApiResponse;
import com.emi.service.AuditService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
@AllArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> listAudit(@RequestParam(value = "action", required = false) String action,
                                       @RequestParam(value = "userId", required = false) String userId) {
        return ResponseEntity.ok(ApiResponse.success().add("items", auditService.recent(action, userId)));
    }
}
