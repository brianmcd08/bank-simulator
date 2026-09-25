package com.bankcorp.banksim;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Holds back the reply to the first message for one loan, after that message has been processed. The processor has
 * done the work, but a client with a shorter read timeout gives up waiting and cannot tell that it was done.
 */
public class ReplyChaos {

    private final ChaosProperties properties;
    private final JsonMapper mapper;
    private final AtomicBoolean dropped = new AtomicBoolean();

    public ReplyChaos(ChaosProperties properties, JsonMapper mapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Called after the message has been processed and before the reply is sent. */
    public void afterProcessing(String json) {
        String loan = properties.dropFirstReplyForLoan();
        if (loan == null || !loan.equals(loanId(json)) || !dropped.compareAndSet(false, true)) {
            return;
        }
        System.out.println("[CHAOS] processed " + loan + ", holding the reply for " + properties.replyDelay());
        try {
            Thread.sleep(properties.replyDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String loanId(String json) {
        try {
            return mapper.readTree(json == null ? "" : json).path("loan_id").stringValue(null);
        } catch (JacksonException e) {
            return null;
        }
    }
}
