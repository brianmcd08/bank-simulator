package com.bankcorp.banksim;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code banksim.chaos.*} that make the listener fail on demand, so a demo does the same thing every
 * run. Off unless failOnceForLoan is set.
 *
 * @param failOnceForLoan the first final outcome (not PENDING) for this loan makes the listener throw, once
 * @param failPoint       whether it throws before or after storing the outcome
 */
@ConfigurationProperties("banksim.chaos")
public record ChaosProperties(String failOnceForLoan, FailPoint failPoint) {

    public enum FailPoint { BEFORE_STORE, AFTER_STORE }

    public ChaosProperties {
        if (failPoint == null) {
            failPoint = FailPoint.BEFORE_STORE;
        }
    }
}
