package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.annotation.CurrentUserRole;
import com.emi.dto.request.FollowupRequest;
import com.emi.dto.response.ApiResponse;
import com.emi.service.FollowupService;
import com.emi.utils.enums.Role;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/followups")
@AllArgsConstructor
public class FollowupController {

    private final FollowupService followupService;

    // Daily calling queue: due (call today), missed, upcoming, and uncalled (overdue with no follow-up yet)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> getQueue(@Parameter(hidden = true)  @CurrentUserId String staffId,@Parameter(hidden = true) @CurrentUserRole Role role) {
        var queue = followupService.queue(staffId, isAdmin(role.name()));
        return ResponseEntity.ok(ApiResponse.success()
                .add("due", queue.due())
                .add("missed", queue.missed())
                .add("upcoming", queue.upcoming())
                .add("uncalled", queue.uncalled()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> logFollowup(@Parameter(hidden = true)  @CurrentUserId String staffId,
                                         @Parameter(hidden = true) @CurrentUserRole Role role,
                                         @Valid @RequestBody FollowupRequest request) {
        var followup = followupService.record(staffId, isAdmin(role.name()), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", followup));
    }

    private boolean isAdmin(String role) {
        return role.equals("ROLE_ADMIN");
    }
}
