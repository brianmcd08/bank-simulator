package com.bankcorp.banksim;

import java.util.List;
import java.util.Map;

/**
 * The JSON the bank service sends, kept here as test data. The same eight messages as the Python main.py, three with
 * an invalid bank.
 */
final class SampleMessages {

    static final String BOA_PAYMENT =
            "{\"bank_id\": \"boa_9423213\", \"loan_id\": \"loan_001\", \"event_type\": \"PAYMENT\"}";
    static final String BOA_POSITION =
            "{\"bank_id\": \"boa_9423213\", \"loan_id\": \"loan_001\", \"event_type\": \"POSITION_UPDATE\"}";
    static final String JPMC_PAYMENT =
            "{\"bank_id\": \"jpmc_764421\", \"loan_id\": \"loan_001\", \"event_type\": \"PAYMENT\"}";
    static final String WF_POSITION =
            "{\"bank_id\": \"wf_1334566\", \"loan_id\": \"loan_002\", \"event_type\": \"POSITION_UPDATE\"}";
    /** Same bank, loan and event type as BOA_PAYMENT. */
    static final String BOA_PAYMENT_DUPLICATE =
            "{\"bank_id\": \"boa_9423213\", \"loan_id\": \"loan_001\", \"event_type\": \"PAYMENT\"}";
    static final String INVALID_BANK =
            "{\"bank_id\": \"unknown_999\", \"loan_id\": \"loan_003\", \"event_type\": \"PAYMENT\"}";

    static final Map<Banks, List<String>> BY_BANK = Map.of(
            Banks.BANK_OF_AMERICA, List.of(BOA_PAYMENT, BOA_POSITION, BOA_PAYMENT_DUPLICATE, INVALID_BANK),
            Banks.JPMORGAN_CHASE, List.of(JPMC_PAYMENT, INVALID_BANK),
            Banks.WELLS_FARGO, List.of(WF_POSITION, INVALID_BANK));

    private SampleMessages() {}
}
