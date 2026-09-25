package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SimulatorPropertiesTest {

    @Test
    void ratesAreRequired() {
        assertThrows(NullPointerException.class, () -> new SimulatorProperties(null, 0.3));
        assertThrows(NullPointerException.class, () -> new SimulatorProperties(0.4, null));
    }

    @Test
    void ratesMustBeBetweenZeroAndOne() {
        assertThrows(IllegalArgumentException.class, () -> new SimulatorProperties(40.0, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new SimulatorProperties(0.4, -0.1));
    }
}
