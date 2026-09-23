package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void factoryGeneratesADistinctIdEachTime() {
        Message first = Message.of("boa_9423213", "loan_001", EventType.PAYMENT);
        Message second = Message.of("boa_9423213", "loan_001", EventType.PAYMENT);

        assertNotEquals(first.messageId(), second.messageId());
        assertNotEquals(first, second);
    }

    @Test
    void recordsWithTheSameFieldsAreEqual() {
        Message a = new Message("boa_9423213", "loan_001", EventType.PAYMENT, "id-1");
        Message b = new Message("boa_9423213", "loan_001", EventType.PAYMENT, "id-1");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void nullFieldsAreRejected() {
        assertThrows(NullPointerException.class, () -> Message.of(null, "loan_001", EventType.PAYMENT));
        assertThrows(NullPointerException.class, () -> Message.of("boa_9423213", null, EventType.PAYMENT));
        assertThrows(NullPointerException.class, () -> Message.of("boa_9423213", "loan_001", null));
    }
}
