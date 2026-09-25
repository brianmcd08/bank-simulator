package com.bankcorp.banksim;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Makes the listener throw once, for one loan's first final outcome, standing in for a crash. The broker treats a
 * listener that throws and a service that dies the same way: the message was not acknowledged. What happens next
 * depends on the acknowledge mode.
 */
public class ListenerChaos {

    private final ChaosProperties properties;
    private final AtomicBoolean fired = new AtomicBoolean();

    public ListenerChaos(ChaosProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void beforeStore(AuditEntry entry) {
        maybeFail(entry, ChaosProperties.FailPoint.BEFORE_STORE);
    }

    public void afterStore(AuditEntry entry) {
        maybeFail(entry, ChaosProperties.FailPoint.AFTER_STORE);
    }

    private void maybeFail(AuditEntry entry, ChaosProperties.FailPoint point) {
        String loan = properties.failOnceForLoan();
        if (loan == null || properties.failPoint() != point || entry.outcome() == Outcome.PENDING
                || !loan.equals(entry.loanId()) || !fired.compareAndSet(false, true)) {
            return;
        }
        System.out.println("[CHAOS] failing " + (point == ChaosProperties.FailPoint.BEFORE_STORE ? "before" : "after")
                + " storing " + entry.outcome() + " for " + entry.messageId());
        throw new IllegalStateException("chaos: simulated crash " + point);
    }
}
