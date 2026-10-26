package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.response.ApiResponse;
import com.emi.service.PortalService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portal")
@AllArgsConstructor
public class PortalController {

    private final PortalService portalService;

    @GetMapping
    // @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getPortal(@CurrentUserId String userId, Authentication authentication) {
        ApiResponse body = ApiResponse.success();
        portalService.portal(userId, authentication).forEach(body::add);
        return ResponseEntity.ok(body);
    }
}
