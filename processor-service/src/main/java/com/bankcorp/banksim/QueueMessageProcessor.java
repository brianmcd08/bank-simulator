package com.bankcorp.banksim;

import java.time.Duration;
import java.util.Objects;

/**
 * Consumes one queue on its own thread until it takes {@link SQSQueue#POISON}.
 * Each message gets up to three attempts. A message that fails all three goes to the dead letter queue.
 * One audit entry is written per message, with its final outcome.
 *
 * <p>An optional processing delay holds each message before its first attempt, so a demo can watch it in flight.
 */
public class QueueMessageProcessor implements Runnable {

    static final int MAX_ATTEMPTS = 3;

    private final SQSQueue queue;
    private final FailurePolicy failurePolicy;
    private final DeadLetterQueue dlq;
    private final AuditLog auditLog;
    private final Duration processingDelay;

    public QueueMessageProcessor(SQSQueue queue, FailurePolicy failurePolicy, DeadLetterQueue dlq, AuditLog auditLog) {
        this(queue, failurePolicy, dlq, auditLog, Duration.ZERO);
    }

    public QueueMessageProcessor(SQSQueue queue, FailurePolicy failurePolicy, DeadLetterQueue dlq, AuditLog auditLog,
                                 Duration processingDelay) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        this.dlq = Objects.requireNonNull(dlq, "dlq");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.processingDelay = Objects.requireNonNull(processingDelay, "processingDelay");
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

    private void process(Message message) throws InterruptedException {
        Thread.sleep(processingDelay);
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
