package com.bankcorp.banksim;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A queue of messages for one event type. Bank threads push, one processor thread takes.
 * The queue is unbounded, so push never waits.
 */
public class SQSQueue {

    /**
     * The shutdown signal. A processor that takes this stops. Compare with {@code ==}, never equals: it is only the
     * sentinel by identity. Put exactly one on a queue per consumer, and never publish it through the topic.
     */
    public static final Message POISON = new Message("POISON", "POISON", EventType.PAYMENT, "POISON");

    private final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();

    public void push(Message message) {
        messages.add(Objects.requireNonNull(message, "message"));
    }

    /** Removes and returns the next message, waiting until one arrives. */
    public Message take() throws InterruptedException {
        return messages.take();
    }

    /** Removes and returns the next message, or null if none arrives within the timeout. */
    public Message poll(Duration timeout) throws InterruptedException {
        return messages.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    public int size() {
        return messages.size();
    }
}
