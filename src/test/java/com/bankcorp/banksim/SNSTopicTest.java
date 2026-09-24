package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SNSTopicTest {

    private static final Duration WAIT = Duration.ofSeconds(1);

    @Test
    void eachEventTypeGoesToItsOwnQueue() throws InterruptedException {
        SQSQueue positions = new SQSQueue();
        SQSQueue payments = new SQSQueue();
        SNSTopic topic = new SNSTopic();
        topic.registerQueue(positions, EventType.POSITION_UPDATE);
        topic.registerQueue(payments, EventType.PAYMENT);

        Message payment = Message.of("boa_9423213", "loan_001", EventType.PAYMENT);
        topic.publish(payment);

        assertSame(payment, payments.poll(WAIT));
        assertEquals(0, positions.size());
    }

    @Test
    void oneTypeFansOutToEveryRegisteredQueue() throws InterruptedException {
        SQSQueue a = new SQSQueue();
        SQSQueue b = new SQSQueue();
        SNSTopic topic = new SNSTopic();
        topic.registerQueue(a, EventType.PAYMENT);
        topic.registerQueue(b, EventType.PAYMENT);

        Message payment = Message.of("boa_9423213", "loan_001", EventType.PAYMENT);
        topic.publish(payment);

        assertSame(payment, a.poll(WAIT));
        assertSame(payment, b.poll(WAIT));
    }

    @Test
    void typeWithNoQueueIsDropped() {
        SQSQueue payments = new SQSQueue();
        SNSTopic topic = new SNSTopic();
        topic.registerQueue(payments, EventType.PAYMENT);

        topic.publish(Message.of("boa_9423213", "loan_001", EventType.POSITION_UPDATE));

        assertEquals(0, payments.size());
    }
}
