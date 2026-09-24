package com.bankcorp.banksim;

import java.util.concurrent.ThreadLocalRandom;

/** Decides whether one processing attempt fails. Injected so tests can force the success, retry and DLQ paths. */
@FunctionalInterface
public interface FailurePolicy {

    boolean shouldFail();

    /**
     * Fails with the given probability, from 0 (never) to 1 (always). Fixes the Python bug that compared a
     * 1..100 roll against a 0..1 rate. ThreadLocalRandom because two processor threads call this at once.
     */
    static FailurePolicy randomRate(double rate) {
        if (!(rate >= 0.0 && rate <= 1.0)) {
            throw new IllegalArgumentException("rate must be between 0 and 1: " + rate);
        }
        return () -> ThreadLocalRandom.current().nextDouble() < rate;
    }

    static FailurePolicy never() {
        return () -> false;
    }

    static FailurePolicy always() {
        return () -> true;
    }
}
