package com.bankcorp.banksim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This service's own copy of message outcomes, built from the events the processor publishes. It holds the latest state
 * of each message, keyed by message id: a final outcome replaces PENDING, so a message that went PENDING and then
 * SUCCESS appears once, as SUCCESS.
 *
 * <p>A PENDING that arrives after a final outcome is ignored. The processor sends them in order, but nothing about a
 * network promises order, and a message that has finished must never look unfinished again.
 *
 * <p>Written by the listener thread and read by request threads, so every access holds the lock; readers get a
 * copy.
 */
public class OutcomeStore {

    /** Insertion order, so entries come back in the order messages were first heard of. */
    private final Map<String, AuditEntry> latest = new LinkedHashMap<>();

    public synchronized void add(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        AuditEntry current = latest.get(entry.messageId());
        if (current != null && current.outcome() != Outcome.PENDING && entry.outcome() == Outcome.PENDING) {
            return;
        }
        latest.put(entry.messageId(), entry);
    }

    /** A snapshot of each message's latest state. Later events do not change the returned list. */
    public synchronized List<AuditEntry> entries() {
        return List.copyOf(latest.values());
    }
}
