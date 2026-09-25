package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SQSQueueTest {

    @Test
    void takeReturnsMessagesInPushOrder() throws InterruptedException {
        SQSQueue queue = new SQSQueue();
        Message first = Message.of("boa_9423213", "loan_001", EventType.PAYMENT);
        Message second = Message.of("boa_9423213", "loan_002", EventType.PAYMENT);

        queue.push(first);
        queue.push(second);

        assertEquals(2, queue.size());
        assertSame(first, queue.take());
        assertSame(second, queue.take());
        assertEquals(0, queue.size());
    }

    @Test
    void pollReturnsNullWhenEmpty() throws InterruptedException {
        assertNull(new SQSQueue().poll(Duration.ofMillis(10)));
    }
}
