package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AuditLogTest {

    @Test
    void writeRecordsEveryField() {
        AuditLog log = new AuditLog();
        log.write(EventType.PAYMENT, "id-1", "boa_9423213", "loan_001", Outcome.SUCCESS);

        AuditEntry entry = log.entries().getFirst();
        assertEquals(EventType.PAYMENT, entry.eventType());
        assertEquals("id-1", entry.messageId());
        assertEquals("boa_9423213", entry.bankId());
        assertEquals("loan_001", entry.loanId());
        assertEquals(Outcome.SUCCESS, entry.outcome());
    }

    @Test
    void rejectedInputMayHaveNullFields() {
        AuditLog log = new AuditLog();
        log.write(null, "id-1", null, null, Outcome.PERMANENT_FAILURE);

        assertEquals(1, log.entries().size());
    }

    @Test
    void messageIdAndOutcomeAreRequired() {
        AuditLog log = new AuditLog();
        assertThrows(NullPointerException.class, () -> log.write(EventType.PAYMENT, null, "b", "l", Outcome.SUCCESS));
        assertThrows(NullPointerException.class, () -> log.write(EventType.PAYMENT, "id", "b", "l", null));
    }

    @Test
    void entriesIsASnapshot() {
        AuditLog log = new AuditLog();
        log.write(EventType.PAYMENT, "id-1", "b", "l", Outcome.SUCCESS);
        List<AuditEntry> snapshot = log.entries();

        log.write(EventType.PAYMENT, "id-2", "b", "l", Outcome.SUCCESS);

        assertEquals(1, snapshot.size());
        assertEquals(2, log.entries().size());
    }

    /** Guards against a regression. A racy list can pass this by luck, so it does not prove thread safety. */
    @Test
    @Timeout(10)
    void concurrentWritesAreNotLost() throws InterruptedException {
        AuditLog log = new AuditLog();
        int threads = 8;
        int writesPerThread = 10_000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> writers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            writers.add(Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < writesPerThread; i++) {
                    log.write(EventType.PAYMENT, "id", "b", "l", Outcome.SUCCESS);
                }
            }));
        }
        start.countDown();
        for (Thread writer : writers) {
            writer.join(TimeUnit.SECONDS.toMillis(10));
        }

        assertEquals(threads * writesPerThread, log.entries().size());
    }
}
