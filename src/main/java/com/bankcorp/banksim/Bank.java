package com.bankcorp.banksim;

import java.util.Objects;

/**
 * A bank that sends raw JSON messages to the topic processor.
 * The Python version had one subclass per bank with no differences between them. They come back when a bank's
 * behavior actually differs.
 */
public class Bank {

    private final Banks bank;
    private final TopicMessageProcessor processor;

    public Bank(Banks bank, TopicMessageProcessor processor) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.processor = Objects.requireNonNull(processor, "processor");
    }

    public Banks bank() {
        return bank;
    }

    public void send(String json) {
        processor.process(json);
    }
}
