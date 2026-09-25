package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutcomeStoreTest {

    private final OutcomeStore store = new OutcomeStore();

    private static AuditEntry entry(String messageId, Outcome outcome) {
        return new AuditEntry(EventType.PAYMENT, messageId, "boa_9423213", "loan_001", outcome, Instant.now());
    }

    private List<Outcome> outcomes() {
        return store.entries().stream().map(AuditEntry::outcome).toList();
    }

    @Test
    void finalOutcomeReplacesPending() {
        store.add(entry("a", Outcome.PENDING));
        store.add(entry("a", Outcome.SUCCESS));

        assertEquals(List.of(Outcome.SUCCESS), outcomes());
    }

    @Test
    void pendingArrivingLateDoesNotUndoAFinalOutcome() {
        store.add(entry("a", Outcome.SUCCESS));
        store.add(entry("a", Outcome.PENDING));

        assertEquals(List.of(Outcome.SUCCESS), outcomes());
    }

    @Test
    void messagesStayInTheOrderTheyWereFirstHeardOf() {
        store.add(entry("a", Outcome.PENDING));
        store.add(entry("b", Outcome.PENDING));
        store.add(entry("a", Outcome.DLQ));

        assertEquals(List.of("a", "b"), store.entries().stream().map(AuditEntry::messageId).toList());
        assertEquals(List.of(Outcome.DLQ, Outcome.PENDING), outcomes());
    }

    /** The naive store's false alarm: a finished message must not be reported as stuck. */
    @Test
    void finishedMessageIsNotStuck() {
        store.add(new AuditEntry(EventType.PAYMENT, "a", "b", "l", Outcome.PENDING, Instant.EPOCH));
        store.add(entry("a", Outcome.SUCCESS));

        ReconciliationEngine engine = new ReconciliationEngine(store, Clock.systemUTC(), Duration.ofSeconds(5));
        assertEquals(List.of(), engine.stuckInPending());
    }
}
