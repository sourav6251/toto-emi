package com.emi.event.user;

import com.emi.event.DomainEvent;
import com.emi.utils.enums.Role;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        Instant occurredAt,
        String userId,
        Role role
) implements DomainEvent {

    public static UserRegisteredEvent of(String userId, Role role) {
        return new UserRegisteredEvent(UUID.randomUUID(), Instant.now(), userId, role);
    }
}
