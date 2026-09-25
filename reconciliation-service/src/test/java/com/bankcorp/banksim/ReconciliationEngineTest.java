package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReconciliationEngineTest {

    private static final Duration LIMIT = Duration.ofSeconds(60);

    private OutcomeStore log;

    @BeforeEach
    void setUp() {
        log = new OutcomeStore();
    }

    private ReconciliationEngine engine() {
        return new ReconciliationEngine(log, Clock.systemUTC(), LIMIT);
    }

    /** A clock set to the given time after the first entry was stored. */
    private ReconciliationEngine engineAt(Duration afterFirstEntry) {
        Instant first = log.entries().getFirst().timestamp();
        return new ReconciliationEngine(log, Clock.fixed(first.plus(afterFirstEntry), ZoneOffset.UTC), LIMIT);
    }

    private void write(EventType eventType, String messageId, String bankId, String loanId, Outcome outcome) {
        log.add(new AuditEntry(eventType, messageId, bankId, loanId, outcome, Instant.now()));
    }

    private static List<String> ids(List<AuditEntry> entries) {
        return entries.stream().map(AuditEntry::messageId).toList();
    }

    @Test
    void differentOutcomeForTheSameLoanIsReported() {
        write(EventType.PAYMENT, "a", "boa_9423213", "loan_001", Outcome.SUCCESS);
        write(EventType.POSITION_UPDATE, "b", "boa_9423213", "loan_001", Outcome.DLQ);
        write(EventType.PAYMENT, "c", "jpmc_764421", "loan_001", Outcome.SUCCESS);
        write(EventType.PAYMENT, "d", "wf_1334566", "loan_002", Outcome.SUCCESS);

        assertEquals(List.of("b"), ids(engine().diffOutcomesForSameLoan()));
    }

    @Test
    void comparesAgainstTheFirstOutcomeSeen() {
        write(EventType.PAYMENT, "a", "b", "loan_001", Outcome.SUCCESS);
        write(EventType.PAYMENT, "b", "b", "loan_001", Outcome.DLQ);
        write(EventType.PAYMENT, "c", "b", "loan_001", Outcome.DLQ);

        assertEquals(List.of("b", "c"), ids(engine().diffOutcomesForSameLoan()));
    }

    @Test
    void duplicatesAreEntriesAfterTheFirstWithTheSameKey() {
        write(EventType.PAYMENT, "a", "boa_9423213", "loan_001", Outcome.SUCCESS);
        write(EventType.POSITION_UPDATE, "b", "boa_9423213", "loan_001", Outcome.SUCCESS);
        write(EventType.PAYMENT, "c", "boa_9423213", "loan_001", Outcome.SUCCESS);
        write(EventType.PAYMENT, "d", "jpmc_764421", "loan_001", Outcome.SUCCESS);
        write(EventType.PAYMENT, "e", "boa_9423213", "loan_001", Outcome.DLQ);

        assertEquals(List.of("c", "e"), ids(engine().duplicateMessages()));
    }

    @Test
    void duplicateCheckHandlesNullFields() {
        write(null, "a", null, null, Outcome.PERMANENT_FAILURE);
        write(null, "b", null, null, Outcome.PERMANENT_FAILURE);

        assertEquals(List.of("b"), ids(engine().duplicateMessages()));
    }

    @Test
    void pendingOlderThanTheLimitIsStuck() {
        write(EventType.PAYMENT, "a", "b", "l", Outcome.PENDING);

        assertEquals(List.of("a"), ids(engineAt(Duration.ofSeconds(60).plusMillis(500)).stuckInPending()));
    }

    @Test
    void pendingAtOrUnderTheLimitIsNotStuck() {
        write(EventType.PAYMENT, "a", "b", "l", Outcome.PENDING);

        assertEquals(List.of(), engineAt(Duration.ofSeconds(59)).stuckInPending());
        assertEquals(List.of(), engineAt(Duration.ofSeconds(60)).stuckInPending());
    }

    @Test
    void oldEntriesThatAreNotPendingAreNotStuck() {
        write(EventType.PAYMENT, "a", "b", "l", Outcome.SUCCESS);

        assertEquals(List.of(), engineAt(Duration.ofHours(1)).stuckInPending());
    }
}
