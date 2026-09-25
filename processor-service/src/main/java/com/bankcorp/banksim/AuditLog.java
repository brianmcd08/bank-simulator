package com.bankcorp.banksim;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Append-only record of message outcomes. Written by request threads and both queue processor threads at once, so
 * every access holds the lock. Readers get a copy and never see the live list.
 * The Python version had an integrity check that always passed; it is left out until there is something to check.
 *
 * <p>Every entry is also handed to a listener, which is how outcomes leave this service. The listener is called
 * while the lock is held, so it sees entries in the same order as the log. It must return quickly.
 */
public class AuditLog {

    private final List<AuditEntry> entries = new ArrayList<>();
    private final Consumer<AuditEntry> onWrite;

    public AuditLog() {
        this(entry -> {});
    }

    public AuditLog(Consumer<AuditEntry> onWrite) {
        this.onWrite = Objects.requireNonNull(onWrite, "onWrite");
    }

    public synchronized void write(EventType eventType, String messageId, String bankId, String loanId, Outcome outcome) {
        AuditEntry entry = new AuditEntry(eventType, messageId, bankId, loanId, outcome, Instant.now());
        entries.add(entry);
        onWrite.accept(entry);
    }

    /** A snapshot of the log. Later writes do not change the returned list. */
    public synchronized List<AuditEntry> entries() {
        return List.copyOf(entries);
    }
}
