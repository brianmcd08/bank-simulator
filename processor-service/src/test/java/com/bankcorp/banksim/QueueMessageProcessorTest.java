package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class QueueMessageProcessorTest {

    private SQSQueue queue;
    private DeadLetterQueue dlq;
    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        queue = new SQSQueue();
        dlq = new DeadLetterQueue();
        auditLog = new AuditLog();
    }

    /** Counts calls and fails the first {@code failures} of them. */
    private static final class CountingPolicy implements FailurePolicy {
        private final int failures;
        final AtomicInteger calls = new AtomicInteger();

        CountingPolicy(int failures) {
            this.failures = failures;
        }

        @Override
        public boolean shouldFail() {
            return calls.incrementAndGet() <= failures;
        }
    }

    // These tests preload the queue, end it with POISON, and call run() on the test thread, so nothing is racing.

    @Test
    void successOnFirstAttempt() {
        CountingPolicy policy = new CountingPolicy(0);
        Message message = Message.of("boa_9423213", "loan_001", EventType.PAYMENT);
        queue.push(message);
        queue.push(SQSQueue.POISON);

        new QueueMessageProcessor(queue, policy, dlq, auditLog).run();

        assertEquals(1, policy.calls.get());
        assertEquals(Outcome.SUCCESS, auditLog.entries().getFirst().outcome());
        assertEquals(message.messageId(), auditLog.entries().getFirst().messageId());
        assertEquals(List.of(), dlq.messages());
    }

    @Test
    void successAfterTwoFailuresIsNotDeadLettered() {
        CountingPolicy policy = new CountingPolicy(2);
        queue.push(Message.of("boa_9423213", "loan_001", EventType.PAYMENT));
        queue.push(SQSQueue.POISON);

        new QueueMessageProcessor(queue, policy, dlq, auditLog).run();

        assertEquals(3, policy.calls.get());
        assertEquals(Outcome.SUCCESS, auditLog.entries().getFirst().outcome());
        assertEquals(List.of(), dlq.messages());
    }

    @Test
    void threeFailuresGoToTheDeadLetterQueue() {
        CountingPolicy policy = new CountingPolicy(Integer.MAX_VALUE);
        Message message = Message.of("wf_1334566", "loan_002", EventType.POSITION_UPDATE);
        queue.push(message);
        queue.push(SQSQueue.POISON);

        new QueueMessageProcessor(queue, policy, dlq, auditLog).run();

        assertEquals(QueueMessageProcessor.MAX_ATTEMPTS, policy.calls.get());
        assertEquals(List.of(message), dlq.messages());
        assertEquals(1, auditLog.entries().size());
        assertEquals(Outcome.DLQ, auditLog.entries().getFirst().outcome());
    }

    @Test
    void messagesAfterPoisonAreLeftAlone() {
        queue.push(SQSQueue.POISON);
        queue.push(Message.of("boa_9423213", "loan_001", EventType.PAYMENT));

        new QueueMessageProcessor(queue, FailurePolicy.never(), dlq, auditLog).run();

        assertEquals(1, queue.size());
        assertEquals(List.of(), auditLog.entries());
    }

    @Test
    void aMessageEqualToPoisonIsNotPoison() {
        Message lookalike = new Message(
                SQSQueue.POISON.bankId(), SQSQueue.POISON.loanId(), SQSQueue.POISON.eventType(),
                SQSQueue.POISON.messageId());
        queue.push(lookalike);
        queue.push(SQSQueue.POISON);

        new QueueMessageProcessor(queue, FailurePolicy.never(), dlq, auditLog).run();

        assertEquals(1, auditLog.entries().size());
    }

    @Test
    void waitsOnAnEmptyQueueThenStopsOnPoison() throws InterruptedException {
        Thread thread = Thread.ofPlatform().start(
                new QueueMessageProcessor(queue, FailurePolicy.never(), dlq, auditLog));

        thread.join(100);
        assertTrue(thread.isAlive(), "should be waiting on take()");

        queue.push(SQSQueue.POISON);
        thread.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(thread.isAlive());
    }

    @Test
    void interruptStopsTheProcessor() throws InterruptedException {
        Thread thread = Thread.ofPlatform().start(
                new QueueMessageProcessor(queue, FailurePolicy.never(), dlq, auditLog));

        thread.interrupt();
        thread.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(thread.isAlive());
    }
}
