package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeadLetterQueueTest {

    @Test
    void pushedMessagesAreKeptInOrder() {
        DeadLetterQueue dlq = new DeadLetterQueue();
        Message first = Message.of("boa_9423213", "loan_001", EventType.PAYMENT);
        Message second = Message.of("wf_1334566", "loan_002", EventType.POSITION_UPDATE);

        dlq.push(first);
        dlq.push(second);

        assertEquals(List.of(first, second), dlq.messages());
    }

    @Test
    void messagesIsASnapshot() {
        DeadLetterQueue dlq = new DeadLetterQueue();
        dlq.push(Message.of("boa_9423213", "loan_001", EventType.PAYMENT));
        List<Message> snapshot = dlq.messages();

        dlq.push(Message.of("boa_9423213", "loan_002", EventType.PAYMENT));

        assertEquals(1, snapshot.size());
    }

    @Test
    void nullIsRejected() {
        assertThrows(NullPointerException.class, () -> new DeadLetterQueue().push(null));
    }
}
