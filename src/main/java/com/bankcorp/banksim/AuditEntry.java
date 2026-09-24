package com.bankcorp.banksim;

import java.time.Instant;
import java.util.Objects;

/**
 * One line of the audit log: what happened to a message and when.
 * Unlike {@link Message}, the event type, bank id and loan id may be null. A rejected message is audited with
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
