package com.bankcorp.banksim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Messages that failed every retry. Written by both queue processor threads, so every access holds the lock. */
public class DeadLetterQueue {

    private final List<Message> messages = new ArrayList<>();

    public synchronized void push(Message message) {
        messages.add(Objects.requireNonNull(message, "message"));
    }

    /** A snapshot of the queue. Later pushes do not change the returned list. */
    public synchronized List<Message> messages() {
        return List.copyOf(messages);
    }
}
