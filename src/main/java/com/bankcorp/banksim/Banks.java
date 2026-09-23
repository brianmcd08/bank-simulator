package com.bankcorp.banksim;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** The banks known to the simulator. A message whose bank id is not in this list is rejected. */
public enum Banks {
    BANK_OF_AMERICA("Bank of America", "boa_9423213"),
    JPMORGAN_CHASE("JP Morgan Chase", "jpmc_764421"),
    WELLS_FARGO("Wells Fargo", "wf_1334566");

    private static final Set<String> VALID_IDS =
            Arrays.stream(values()).map(Banks::bankId).collect(Collectors.toUnmodifiableSet());

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

    /**
     * True if some bank in this enum has the given id. Replaces the set comprehension in the Python validator.
     * Null is a valid question to ask (a message may be missing its bank id) and the answer is no.
     * The explicit check is needed because unmodifiable sets throw on contains(null).
     */
    public static boolean isValidId(String bankId) {
        return bankId != null && VALID_IDS.contains(bankId);
    }
}
