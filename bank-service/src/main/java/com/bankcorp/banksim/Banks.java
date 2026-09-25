package com.bankcorp.banksim;

/**
 * The banks this service sends for. The processor service keeps its own copy to validate bank ids; the two are
 * separate on purpose, because the services share no code.
 */
public enum Banks {
    BANK_OF_AMERICA("Bank of America", "boa_9423213"),
    JPMORGAN_CHASE("JP Morgan Chase", "jpmc_764421"),
    WELLS_FARGO("Wells Fargo", "wf_1334566");

    private final String fullName;
    private final String bankId;

    Banks(String fullName, String bankId) {
        this.fullName = fullName;
        this.bankId = bankId;
    }

    public String fullName() {
        return fullName;
    }

    public String bankId() {
        return bankId;
    }
}
