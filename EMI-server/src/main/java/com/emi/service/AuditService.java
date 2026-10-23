package com.emi.service;

import com.emi.exception.DomainException;
import com.emi.model.entity.AuditLog;
import com.emi.model.entity.Users;
import com.emi.model.reposetory.AuditLogJpaRepository;
import com.emi.model.reposetory.UserJpaRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Append-only: this service only inserts and reads, there is no update or delete path
@Service
@AllArgsConstructor
public class AuditService {

    private static final int MAX_ITEMS = 200;

    private final AuditLogJpaRepository auditLogJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public record AuditItem(OffsetDateTime at, String user, String action, String detail, String ref) {
    }

    public record AppLogItem(
            String id,
            String userId,
            String user,
            String action,
            String detail,
            String ref,
            OffsetDateTime createdAt
    ) {
    }

    // Joins the caller's transaction, so an entry is only kept if the action it describes is committed
    @Transactional
    public void log(String userId, String action, String detail, String ref) {
        auditLogJpaRepository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .detail(detail)
                .ref(ref == null ? "" : ref)
                .build());
    }

    // Newest first, capped at 200. action and userId are optional filters.
    @Transactional(readOnly = true)
    public List<AuditItem> recent(String action, String userId) {
        boolean allActions = action == null || action.isBlank();
        boolean allUsers = userId == null || userId.isBlank();
        if (!allUsers && userJpaRepository.findById(userId.trim()).isEmpty()) {
            throw new DomainException("User not found", org.springframework.http.HttpStatus.NOT_FOUND);
        }
        List<AuditLog> logs = auditLogJpaRepository.search(allActions, allActions ? "" : action.trim(),
                allUsers, allUsers ? "" : userId.trim(), PageRequest.of(0, MAX_ITEMS));

        Map<String, String> names = new HashMap<>();
        userJpaRepository.findAllById(logs.stream().map(AuditLog::getUserId).collect(Collectors.toSet()))
                .forEach((Users u) -> names.put(u.getUserID(), u.getFullName()));

        return logs.stream()
                .map(a -> new AuditItem(a.getCreatedAt(), names.getOrDefault(a.getUserId(), "Unknown"),
                        a.getAction(), a.getDetail() == null ? "" : a.getDetail(), a.getRef()))
                .toList();
    }

    // Public app logs with full item details, newest first (capped at 200)
    @Transactional(readOnly = true)
    public List<AppLogItem> getAppLogs(String action, String userId, int limit) {
        int cappedLimit = Math.min(Math.max(limit, 1), MAX_ITEMS);
        boolean allActions = action == null || action.isBlank();
        boolean allUsers = userId == null || userId.isBlank();

        List<AuditLog> logs = auditLogJpaRepository.search(allActions, allActions ? "" : action.trim(),
                allUsers, allUsers ? "" : userId.trim(), PageRequest.of(0, cappedLimit));

        Map<String, String> names = new HashMap<>();
        userJpaRepository.findAllById(logs.stream().map(AuditLog::getUserId).collect(Collectors.toSet()))
                .forEach((Users u) -> names.put(u.getUserID(), u.getFullName()));

        return logs.stream()
                .map(a -> new AppLogItem(
                        a.getAuditId(),
                        a.getUserId(),
                        names.getOrDefault(a.getUserId(), "Unknown"),
                        a.getAction(),
                        a.getDetail() == null ? "" : a.getDetail(),
                        a.getRef(),
                        a.getCreatedAt()))
                .toList();
    }
}
