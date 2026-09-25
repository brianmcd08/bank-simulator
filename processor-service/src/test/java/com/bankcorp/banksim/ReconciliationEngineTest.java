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

    private AuditLog log;

    @BeforeEach
    void setUp() {
        log = new AuditLog();
    }

    private ReconciliationEngine engine() {
        return new ReconciliationEngine(log, Clock.systemUTC());
    }

    /** A clock set to the given time after the first audit entry was written. */
    private ReconciliationEngine engineAt(Duration afterFirstEntry) {
        Instant first = log.entries().getFirst().timestamp();
        return new ReconciliationEngine(log, Clock.fixed(first.plus(afterFirstEntry), ZoneOffset.UTC));
    }

    private static List<String> ids(List<AuditEntry> entries) {
        return entries.stream().map(AuditEntry::messageId).toList();
    }

    @Test
    void differentOutcomeForTheSameLoanIsReported() {
        log.write(EventType.PAYMENT, "a", "boa_9423213", "loan_001", Outcome.SUCCESS);
        log.write(EventType.POSITION_UPDATE, "b", "boa_9423213", "loan_001", Outcome.DLQ);
        log.write(EventType.PAYMENT, "c", "jpmc_764421", "loan_001", Outcome.SUCCESS);
        log.write(EventType.PAYMENT, "d", "wf_1334566", "loan_002", Outcome.SUCCESS);

        assertEquals(List.of("b"), ids(engine().diffOutcomesForSameLoan()));
    }

    @Test
    void comparesAgainstTheFirstOutcomeSeen() {
        log.write(EventType.PAYMENT, "a", "b", "loan_001", Outcome.SUCCESS);
        log.write(EventType.PAYMENT, "b", "b", "loan_001", Outcome.DLQ);
        log.write(EventType.PAYMENT, "c", "b", "loan_001", Outcome.DLQ);

        assertEquals(List.of("b", "c"), ids(engine().diffOutcomesForSameLoan()));
    }

    @Test
    void duplicatesAreEntriesAfterTheFirstWithTheSameKey() {
        log.write(EventType.PAYMENT, "a", "boa_9423213", "loan_001", Outcome.SUCCESS);
        log.write(EventType.POSITION_UPDATE, "b", "boa_9423213", "loan_001", Outcome.SUCCESS);
        log.write(EventType.PAYMENT, "c", "boa_9423213", "loan_001", Outcome.SUCCESS);
        log.write(EventType.PAYMENT, "d", "jpmc_764421", "loan_001", Outcome.SUCCESS);
        log.write(EventType.PAYMENT, "e", "boa_9423213", "loan_001", Outcome.DLQ);

        assertEquals(List.of("c", "e"), ids(engine().duplicateMessages()));
    }

    @Test
    void duplicateCheckHandlesNullFields() {
        log.write(null, "a", null, null, Outcome.PERMANENT_FAILURE);
        log.write(null, "b", null, null, Outcome.PERMANENT_FAILURE);

        assertEquals(List.of("b"), ids(engine().duplicateMessages()));
    }

    @Test
    void pendingOlderThanTheLimitIsStuck() {
        log.write(EventType.PAYMENT, "a", "b", "l", Outcome.PENDING);

        assertEquals(List.of("a"), ids(engineAt(Duration.ofSeconds(60).plusMillis(500)).stuckInPending()));
    }

    @Test
    void pendingAtOrUnderTheLimitIsNotStuck() {
        log.write(EventType.PAYMENT, "a", "b", "l", Outcome.PENDING);

        assertEquals(List.of(), engineAt(Duration.ofSeconds(59)).stuckInPending());
        assertEquals(List.of(), engineAt(Duration.ofSeconds(60)).stuckInPending());
    }

    @Test
    void oldEntriesThatAreNotPendingAreNotStuck() {
        log.write(EventType.PAYMENT, "a", "b", "l", Outcome.SUCCESS);

        assertEquals(List.of(), engineAt(Duration.ofHours(1)).stuckInPending());
    }
}
