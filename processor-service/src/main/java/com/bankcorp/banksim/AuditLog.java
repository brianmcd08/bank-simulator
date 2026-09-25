package com.bankcorp.banksim;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only record of message outcomes. Written by bank threads and both queue processor threads at once, so
 * every access holds the lock. Readers get a copy and never see the live list.
 * The Python version had an integrity check that always passed; it is left out until there is something to check.
 */
public class AuditLog {

    private final List<AuditEntry> entries = new ArrayList<>();

    public synchronized void write(EventType eventType, String messageId, String bankId, String loanId, Outcome outcome) {
        entries.add(new AuditEntry(eventType, messageId, bankId, loanId, outcome, Instant.now()));
    }

    /** A snapshot of the log. Later writes do not change the returned list. */
    public synchronized List<AuditEntry> entries() {
        return List.copyOf(entries);
    }
}
