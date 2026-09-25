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
 * Looks through this service's copy of the outcomes for problems. Each check works from its own snapshot of the
 * store. The copy can be behind the processor's audit log, because events arrive over the network after the fact.
 */
public class ReconciliationEngine {

    /** Identifies a message for duplicate detection. A record, so null fields hash and compare safely. */
    private record Key(String bankId, String loanId, EventType eventType) {}

    private final OutcomeStore store;
    private final Clock clock;
    private final Duration pendingLimit;

    public ReconciliationEngine(OutcomeStore store, Clock clock, Duration pendingLimit) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pendingLimit = Objects.requireNonNull(pendingLimit, "pendingLimit");
    }

    /**
     * Entries whose outcome differs from the first outcome seen for the same loan. The Python version returned
     * (loan id, outcome) pairs; the entry carries both.
     */
    public List<AuditEntry> diffOutcomesForSameLoan() {
        Map<String, Outcome> firstOutcome = new HashMap<>();
        List<AuditEntry> result = new ArrayList<>();
        for (AuditEntry e : store.entries()) {
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
        for (AuditEntry e : store.entries()) {
            if (!seen.add(new Key(e.bankId(), e.loanId(), e.eventType()))) {
                result.add(e);
            }
        }
        return result;
    }

    /** PENDING entries older than the limit. */
    public List<AuditEntry> stuckInPending() {
        Instant now = clock.instant();
        List<AuditEntry> result = new ArrayList<>();
        for (AuditEntry e : store.entries()) {
            if (e.outcome() == Outcome.PENDING
                    && Duration.between(e.timestamp(), now).compareTo(pendingLimit) > 0) {
                result.add(e);
            }
        }
        return result;
    }
}
