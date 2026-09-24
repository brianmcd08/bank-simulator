package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the whole application with no failures. The Simulation runner runs during startup, so by the time the
 * test method runs the audit log and dead letter queue hold its results. 8 messages: 3 with an invalid bank.
 */
@SpringBootTest(properties = {"banksim.position-failure-rate=0", "banksim.payment-failure-rate=0"})
class SimulationNoFailuresTest {

    @Autowired
    AuditLog auditLog;

    @Autowired
    DeadLetterQueue dlq;

    @Test
    void everyValidMessageSucceeds() {
        assertEquals(
                Map.of(Outcome.SUCCESS, 5, Outcome.PERMANENT_FAILURE, 3),
                SimulationTestSupport.countByOutcome(auditLog));
        assertEquals(List.of(), dlq.messages());
    }
}
