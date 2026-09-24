package com.bankcorp.banksim;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Looks through the audit log for problems after processing has finished. Runs on main once every other thread
 * has stopped, and each check works from its own snapshot of the log.
 */
public class ReconciliationEngine {

    static final Duration PENDING_LIMIT = Duration.ofSeconds(60);

    /** Identifies a message for duplicate detection. A record, so null fields hash and compare safely. */
    private record Key(String bankId, String loanId, EventType eventType) {}

    private final AuditLog auditLog;
    private final Clock clock;

    public ReconciliationEngine(AuditLog auditLog, Clock clock) {
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void reconcile() {
        reportFindings(diffOutcomesForSameLoan(), "Different outcomes for same loan");
        reportFindings(duplicateMessages(), "Duplicate messages");
        reportFindings(stuckInPending(), "Stuck in pending");
    }

    /**
     * Entries whose outcome differs from the first outcome seen for the same loan. The Python version returned
     * (loan id, outcome) pairs; the entry carries both.
     */
    public List<AuditEntry> diffOutcomesForSameLoan() {
        Map<String, Outcome> firstOutcome = new HashMap<>();
        List<AuditEntry> result = new ArrayList<>();
        for (AuditEntry e : auditLog.entries()) {
            Outcome seen = firstOutcome.putIfAbsent(e.loanId(), e.outcome());
            if (seen != null && seen != e.outcome()) {
                result.add(e);
            }
        }
        return result;
    }

    /** Every entry after the first with the same bank, loan and event type. */
    public List<AuditEntry> duplicateMessages() {
        Set<Key> seen = new HashSet<>();
        List<AuditEntry> result = new ArrayList<>();
        for (AuditEntry e : auditLog.entries()) {
            if (!seen.add(new Key(e.bankId(), e.loanId(), e.eventType()))) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * PENDING entries older than the limit. Kept from the Python version even though nothing writes PENDING to the
     * audit log yet, so in phase 1 this never finds anything.
     */
    public List<AuditEntry> stuckInPending() {
        Instant now = clock.instant();
        List<AuditEntry> result = new ArrayList<>();
        for (AuditEntry e : auditLog.entries()) {
            if (e.outcome() == Outcome.PENDING
                    && Duration.between(e.timestamp(), now).compareTo(PENDING_LIMIT) > 0) {
                result.add(e);
            }
        }
        return result;
    }

    private static void reportFindings(List<AuditEntry> entries, String problem) {
        for (AuditEntry entry : entries) {
            System.out.println("Problem: " + problem);
            System.out.println(entry);
        }
    }
}
