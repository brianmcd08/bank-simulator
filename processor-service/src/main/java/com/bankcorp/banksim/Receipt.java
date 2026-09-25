package com.bankcorp.banksim;

/** What the topic processor did with one incoming message. */
public enum Receipt {
    /** Published to its queue; the outcome comes later. */
    ACCEPTED,
    /** Its message id was seen before, so it was not published again. */
    DUPLICATE,
    /** Unusable: audited as PERMANENT_FAILURE. */
    REJECTED
}
