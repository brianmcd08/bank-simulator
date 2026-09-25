package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BanksTest {

    /** Must match the processor service's copy, or every message from that bank is rejected. */
    @Test
    void idsMatchTheProcessorService() {
        assertEquals("boa_9423213", Banks.BANK_OF_AMERICA.bankId());
        assertEquals("jpmc_764421", Banks.JPMORGAN_CHASE.bankId());
        assertEquals("wf_1334566", Banks.WELLS_FARGO.bankId());
    }
}
