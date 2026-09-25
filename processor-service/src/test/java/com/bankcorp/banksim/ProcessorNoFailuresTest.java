package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Starts the real service on a random port and sends the 8 sample messages over HTTP. 3 have an invalid bank, and the
 * other 5 are audited twice: PENDING, then SUCCESS. Each
 * test gets a fresh service, because the audit log and the seen ids would otherwise carry over.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "banksim.position-failure-rate=0", "banksim.payment-failure-rate=0", "banksim.reconciliation-url="})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProcessorNoFailuresTest {

    @LocalServerPort
    int port;

    @Autowired
    AuditLog auditLog;

    @Autowired
    DeadLetterQueue dlq;

    @Test
    void everyValidMessageSucceeds() throws Exception {
        ProcessorTestSupport.sendSampleMessages(port);
        ProcessorTestSupport.awaitAuditEntries(auditLog, 13);

        assertEquals(
                Map.of(Outcome.PENDING, 5, Outcome.SUCCESS, 5, Outcome.PERMANENT_FAILURE, 3),
                ProcessorTestSupport.countByOutcome(auditLog));
        assertEquals(List.of(), dlq.messages());
    }

    @Test
    void resendGets200AndIsNotProcessedAgain() throws Exception {
        ProcessorTestSupport.sendSampleMessages(port);
        ProcessorTestSupport.awaitAuditEntries(auditLog, 13);

        assertEquals(200, ProcessorTestSupport.post(port, SampleMessages.WF_POSITION));

        ProcessorTestSupport.awaitAuditEntries(auditLog, 14);
        assertEquals(1, ProcessorTestSupport.countByOutcome(auditLog).get(Outcome.DUPLICATE));
        assertEquals(5, ProcessorTestSupport.countByOutcome(auditLog).get(Outcome.SUCCESS));
    }
}
