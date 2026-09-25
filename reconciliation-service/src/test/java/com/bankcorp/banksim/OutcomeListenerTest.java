package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OutcomeListenerTest {

    private static final String SUCCESS_JSON = "{\"eventType\":\"POSITION_UPDATE\",\"messageId\":\"m-1\","
            + "\"bankId\":\"wf_1334566\",\"loanId\":\"loan_002\",\"outcome\":\"SUCCESS\","
            + "\"timestamp\":\"2026-09-25T20:33:41.733Z\"}";

    private final OutcomeStore store = new OutcomeStore();

    private OutcomeListener listener(String failOnceForLoan, ChaosProperties.FailPoint point) {
        return new OutcomeListener(
                store, JsonMapper.shared(), new ListenerChaos(new ChaosProperties(failOnceForLoan, point)));
    }

    private List<Outcome> outcomes() {
        return store.entries().stream().map(AuditEntry::outcome).toList();
    }

    @Test
    void storesTheOutcomeFromTheProcessorsJson() {
        listener(null, null).receive(SUCCESS_JSON, false);

        assertEquals(List.of(Outcome.SUCCESS), outcomes());
        assertEquals("m-1", store.entries().getFirst().messageId());
    }

    @Test
    void failingBeforeStoreStoresNothingThenTheRedeliveryIsStored() {
        OutcomeListener listener = listener("loan_002", ChaosProperties.FailPoint.BEFORE_STORE);

        assertThrows(IllegalStateException.class, () -> listener.receive(SUCCESS_JSON, false));
        assertEquals(List.of(), outcomes());

        listener.receive(SUCCESS_JSON, true);
        assertEquals(List.of(Outcome.SUCCESS), outcomes());
    }

    /** Stored, then no ack, then the same message again: the store still holds it once. */
    @Test
    void redeliveryAfterStoringLeavesOneEntry() {
        OutcomeListener listener = listener("loan_002", ChaosProperties.FailPoint.AFTER_STORE);

        assertThrows(IllegalStateException.class, () -> listener.receive(SUCCESS_JSON, false));
        listener.receive(SUCCESS_JSON, true);

        assertEquals(List.of(Outcome.SUCCESS), outcomes());
    }
}
