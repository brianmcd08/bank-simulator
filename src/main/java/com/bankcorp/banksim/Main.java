package com.bankcorp.banksim;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the simulation. Each bank sends on its own thread while one processor thread per queue consumes.
 *
 * <p>Shutdown: the sender pool is closed, which waits for every send to finish. Only then does each queue get a
 * poison pill, and main joins the processor threads. The pills go in from a finally block, so an exception partway
 * through cannot leave the processors waiting forever. Reconciliation runs on main after everything has stopped.
 */
public class Main {

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

    static final Map<Banks, List<String>> MESSAGES = Map.of(
            Banks.BANK_OF_AMERICA, List.of(BOA_PAYMENT, BOA_POSITION, BOA_PAYMENT_DUPLICATE, INVALID_BANK),
            Banks.JPMORGAN_CHASE, List.of(JPMC_PAYMENT, INVALID_BANK),
            Banks.WELLS_FARGO, List.of(WF_POSITION, INVALID_BANK));

    /** What a run produced. */
    record Result(AuditLog auditLog, DeadLetterQueue dlq) {}

    public static void main(String[] args) throws InterruptedException {
        Result result = run(FailurePolicy.randomRate(0.4), FailurePolicy.randomRate(0.3));

        System.out.println();
        System.out.println("Audit log (" + result.auditLog().entries().size() + " entries):");
        result.auditLog().entries().forEach(e -> System.out.println("  " + e));
        System.out.println("Dead letter queue (" + result.dlq().messages().size() + " messages):");
        result.dlq().messages().forEach(m -> System.out.println("  " + m));

        System.out.println();
        System.out.println("Reconciliation:");
        new ReconciliationEngine(result.auditLog(), Clock.systemUTC()).reconcile();
    }

    static Result run(FailurePolicy positionPolicy, FailurePolicy paymentPolicy) throws InterruptedException {
        SQSQueue positionQueue = new SQSQueue();
        SQSQueue paymentQueue = new SQSQueue();

        SNSTopic topic = new SNSTopic();
        topic.registerQueue(positionQueue, EventType.POSITION_UPDATE);
        topic.registerQueue(paymentQueue, EventType.PAYMENT);

        AuditLog auditLog = new AuditLog();
        DeadLetterQueue dlq = new DeadLetterQueue();
        TopicMessageProcessor topicProcessor = new TopicMessageProcessor(topic, auditLog);

        Thread positionProcessor = Thread.ofPlatform().name("qmp-position")
                .start(new QueueMessageProcessor(positionQueue, positionPolicy, dlq, auditLog));
        Thread paymentProcessor = Thread.ofPlatform().name("qmp-payment")
                .start(new QueueMessageProcessor(paymentQueue, paymentPolicy, dlq, auditLog));

        try {
            // close() waits for every send, with no timeout, so no pill goes in ahead of a real message.
            // execute() rather than submit(): an exception in a sender is printed, not hidden in a Future.
            try (ExecutorService senders = Executors.newFixedThreadPool(MESSAGES.size())) {
                MESSAGES.forEach((id, messages) -> {
                    Bank bank = new Bank(id, topicProcessor);
                    senders.execute(() -> messages.forEach(bank::send));
                });
            }
        } finally {
            positionQueue.push(SQSQueue.POISON);
            paymentQueue.push(SQSQueue.POISON);
            positionProcessor.join();
            paymentProcessor.join();
        }

        return new Result(auditLog, dlq);
    }
}
