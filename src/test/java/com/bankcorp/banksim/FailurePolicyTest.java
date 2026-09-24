package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FailurePolicyTest {

    @Test
    void rateOutsideZeroToOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FailurePolicy.randomRate(-0.1));
        assertThrows(IllegalArgumentException.class, () -> FailurePolicy.randomRate(1.1));
        assertThrows(IllegalArgumentException.class, () -> FailurePolicy.randomRate(40));
        assertThrows(IllegalArgumentException.class, () -> FailurePolicy.randomRate(Double.NaN));
    }

    @Test
    void rateZeroNeverFailsAndRateOneAlwaysFails() {
        FailurePolicy zero = FailurePolicy.randomRate(0.0);
        FailurePolicy one = FailurePolicy.randomRate(1.0);
        for (int i = 0; i < 1_000; i++) {
            assertFalse(zero.shouldFail());
            assertTrue(one.shouldFail());
        }
    }

    @Test
    void fixedPolicies() {
        assertFalse(FailurePolicy.never().shouldFail());
        assertTrue(FailurePolicy.always().shouldFail());
    }
}
