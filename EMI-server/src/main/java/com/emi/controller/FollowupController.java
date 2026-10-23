package com.emi.controller;

import com.emi.annotation.CurrentUserId;
import com.emi.dto.request.FollowupRequest;
import com.emi.dto.response.ApiResponse;
import com.emi.service.FollowupService;
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
    public ResponseEntity<?> getQueue(@CurrentUserId String staffId, Authentication authentication) {
        var queue = followupService.queue(staffId, isAdmin(authentication));
        return ResponseEntity.ok(ApiResponse.success()
                .add("due", queue.due())
                .add("missed", queue.missed())
                .add("upcoming", queue.upcoming())
                .add("uncalled", queue.uncalled()));
    }

    // Logs the outcome of a call or visit; calls are not recorded automatically
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
    public ResponseEntity<?> logFollowup(@CurrentUserId String staffId,
                                         Authentication authentication,
                                         @Valid @RequestBody FollowupRequest request) {
        var followup = followupService.record(staffId, isAdmin(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success().add("data", followup));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
