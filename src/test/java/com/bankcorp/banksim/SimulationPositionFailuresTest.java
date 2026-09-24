package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Starts the whole application with every position update failing, so both reach the dead letter queue. */
@SpringBootTest(properties = {"banksim.position-failure-rate=1", "banksim.payment-failure-rate=0"})
class SimulationPositionFailuresTest {

    @Autowired
    AuditLog auditLog;

    @Autowired
    DeadLetterQueue dlq;

    @Test
    void positionUpdatesGoToTheDeadLetterQueue() {
        assertEquals(
                Map.of(Outcome.SUCCESS, 3, Outcome.PERMANENT_FAILURE, 3, Outcome.DLQ, 2),
                SimulationTestSupport.countByOutcome(auditLog));
        assertEquals(2, dlq.messages().size());
        dlq.messages().forEach(m -> assertEquals(EventType.POSITION_UPDATE, m.eventType()));
    }
}
