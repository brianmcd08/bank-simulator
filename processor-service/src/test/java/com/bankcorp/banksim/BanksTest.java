package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BanksTest {

    @Test
    void everyKnownBankIdIsValid() {
        for (Banks bank : Banks.values()) {
            assertTrue(Banks.isValidId(bank.bankId()), bank.name());
        }
    }

    @Test
    void unknownIdIsInvalid() {
        assertFalse(Banks.isValidId("unknown_999"));
        assertFalse(Banks.isValidId(""));
        assertFalse(Banks.isValidId(null));
    }

    @Test
    void idsMatchThePythonOriginal() {
        assertEquals("boa_9423213", Banks.BANK_OF_AMERICA.bankId());
        assertEquals("jpmc_764421", Banks.JPMORGAN_CHASE.bankId());
        assertEquals("wf_1334566", Banks.WELLS_FARGO.bankId());
    }
}
