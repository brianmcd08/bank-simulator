package com.bankcorp.banksim;

import java.util.Objects;

/**
 * Consumes one queue on its own thread until it takes {@link SQSQueue#POISON}.
 * Each message gets up to three attempts. A message that fails all three goes to the dead letter queue.
 * One audit entry is written per message, with its final outcome.
 */
public class QueueMessageProcessor implements Runnable {

    static final int MAX_ATTEMPTS = 3;

    private final SQSQueue queue;
    private final FailurePolicy failurePolicy;
    private final DeadLetterQueue dlq;
    private final AuditLog auditLog;

    public QueueMessageProcessor(SQSQueue queue, FailurePolicy failurePolicy, DeadLetterQueue dlq, AuditLog auditLog) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        this.dlq = Objects.requireNonNull(dlq, "dlq");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
    }

    @Override
    public void run() {
        try {
            while (true) {
                Message message = queue.take();
                if (message == SQSQueue.POISON) {
                    return;
                }
                process(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void process(Message message) {
        Outcome outcome = Outcome.DLQ;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (!failurePolicy.shouldFail()) {
                outcome = Outcome.SUCCESS;
                break;
            }
        }
        if (outcome == Outcome.DLQ) {
            dlq.push(message);
        }
        auditLog.write(message.eventType(), message.messageId(), message.bankId(), message.loanId(), outcome);
    }
}
