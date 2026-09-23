package com.bankcorp.banksim;

import java.util.Objects;
import java.util.UUID;

/**
 * A parsed bank message. Immutable: once created it is never changed, so any number of threads may read it safely.
 * The processing outcome is deliberately not a field here; it belongs to the audit log.
 */
public record Message(String bankId, String loanId, EventType eventType, String messageId) {

    public Message {
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(loanId, "loanId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(messageId, "messageId");
    }

    /** Creates a message with a freshly generated id. This is the normal way to build one. */
    public static Message of(String bankId, String loanId, EventType eventType) {
        return new Message(bankId, loanId, eventType, UUID.randomUUID().toString());
    }
}
