package com.emi.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that already happened in the domain. Publish it with ApplicationEventPublisher;
 * handle it in a listener annotated with @Async and @TransactionalEventListener.
 * Events carry IDs, not PII: listeners load whatever details they need.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();
}
