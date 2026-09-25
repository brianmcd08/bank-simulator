package com.bankcorp.banksim;

/**
 * The result of processing a message. Recorded in the audit log, never stored on the message itself.
 */
public enum Outcome {
    PENDING,
    SUCCESS,
    DUPLICATE,
    TRANSIENT_FAILURE,
    PERMANENT_FAILURE,
    DLQ
}
