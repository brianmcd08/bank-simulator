package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Every position update fails, so both reach the dead letter queue. */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"banksim.position-failure-rate=1", "banksim.payment-failure-rate=0"})
class ProcessorPositionFailuresTest {

    @LocalServerPort
    int port;

    @Autowired
    AuditLog auditLog;

    @Autowired
    DeadLetterQueue dlq;

    @Test
    void positionUpdatesGoToTheDeadLetterQueue() throws Exception {
        ProcessorTestSupport.sendSampleMessages(port);
        ProcessorTestSupport.awaitAuditEntries(auditLog, 8);

        assertEquals(
                Map.of(Outcome.SUCCESS, 3, Outcome.PERMANENT_FAILURE, 3, Outcome.DLQ, 2),
                ProcessorTestSupport.countByOutcome(auditLog));
        assertEquals(2, dlq.messages().size());
        dlq.messages().forEach(m -> assertEquals(EventType.POSITION_UPDATE, m.eventType()));
    }
}
