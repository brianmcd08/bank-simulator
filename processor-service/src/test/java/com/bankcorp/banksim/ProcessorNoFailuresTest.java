package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Starts the real service on a random port and sends the 8 sample messages over HTTP. 3 have an invalid bank. */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"banksim.position-failure-rate=0", "banksim.payment-failure-rate=0"})
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
        ProcessorTestSupport.awaitAuditEntries(auditLog, 8);

        assertEquals(
                Map.of(Outcome.SUCCESS, 5, Outcome.PERMANENT_FAILURE, 3),
                ProcessorTestSupport.countByOutcome(auditLog));
        assertEquals(List.of(), dlq.messages());
    }
}
