package com.bankcorp.banksim;

import java.time.Instant;
import java.util.Objects;

/**
 * One outcome event published by the processor service: what happened to a message and when. This service's own copy
 * of the record, deserialized from the JSON the processor sends; the two services share no code.
 * Unlike the processor's Message, the event type, bank id and loan id may be null. A rejected message is audited with
 * whatever fields could be read from it, and some may be missing.
 */
public record AuditEntry(
        EventType eventType, String messageId, String bankId, String loanId, Outcome outcome, Instant timestamp) {

    public AuditEntry {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
