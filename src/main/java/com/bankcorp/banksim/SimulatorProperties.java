package com.bankcorp.banksim;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code banksim.*} in application.yml. Each rate is the chance, from 0 to 1, that one processing
 * attempt on that queue fails. Both are required: a missing or out-of-range rate stops the app at startup.
 */
@ConfigurationProperties("banksim")
public record SimulatorProperties(Double positionFailureRate, Double paymentFailureRate) {

    public SimulatorProperties {
        requireRate(positionFailureRate, "banksim.position-failure-rate");
        requireRate(paymentFailureRate, "banksim.payment-failure-rate");
    }

    private static void requireRate(Double rate, String name) {
        Objects.requireNonNull(rate, name + " is required");
        if (!(rate >= 0.0 && rate <= 1.0)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1: " + rate);
        }
    }
}
