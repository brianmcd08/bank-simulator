package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Runs the whole threaded pipeline with fixed failure policies. main.py sends 8 messages: 3 with an invalid bank. */
@Timeout(10)
class MainTest {

    @Test
    void noFailures() throws InterruptedException {
        Main.Result result = Main.run(FailurePolicy.never(), FailurePolicy.never());

        assertEquals(Map.of(Outcome.SUCCESS, 5, Outcome.PERMANENT_FAILURE, 3), countByOutcome(result));
        assertEquals(List.of(), result.dlq().messages());
    }

    @Test
    void everyPositionUpdateFails() throws InterruptedException {
        Main.Result result = Main.run(FailurePolicy.always(), FailurePolicy.never());

        assertEquals(
                Map.of(Outcome.SUCCESS, 3, Outcome.PERMANENT_FAILURE, 3, Outcome.DLQ, 2), countByOutcome(result));
        assertEquals(2, result.dlq().messages().size());
        result.dlq().messages().forEach(m -> assertEquals(EventType.POSITION_UPDATE, m.eventType()));
    }

    private static Map<Outcome, Integer> countByOutcome(Main.Result result) {
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        result.auditLog().entries().forEach(e -> counts.merge(e.outcome(), 1, Integer::sum));
        return counts;
    }
}
