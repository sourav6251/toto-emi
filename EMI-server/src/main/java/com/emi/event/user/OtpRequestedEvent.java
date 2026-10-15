package com.emi.event.user;

import com.emi.event.DomainEvent;
import com.emi.utils.enums.OtpPurpose;

import java.time.Instant;
import java.util.UUID;

public record OtpRequestedEvent(
        UUID eventId,
        Instant occurredAt,
        String userId,
        OtpPurpose purpose
) implements DomainEvent {

    public static OtpRequestedEvent of(String userId, OtpPurpose purpose) {
        return new OtpRequestedEvent(UUID.randomUUID(), Instant.now(), userId, purpose);
    }
}
