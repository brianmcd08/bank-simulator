package com.bankcorp.banksim;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.CommandLineRunner;

/**
 * Runs the simulation once, when the application starts. Each bank sends on its own thread while one processor
 * thread per queue consumes.
 *
 * <p>Shutdown: the sender pool is closed, which waits for every send to finish. Only then does each queue get a
 * poison pill, and this thread joins the processor threads. The pills go in from a finally block, so an exception
 * partway through cannot leave the processors waiting forever. Reconciliation runs after everything has stopped.
 * With no threads left and no web server, the application then exits on its own.
 */
public class Simulation implements CommandLineRunner {

    private final TopicMessageProcessor topicProcessor;
    private final SQSQueue positionQueue;
    private final SQSQueue paymentQueue;
    private final QueueMessageProcessor positionProcessor;
    private final QueueMessageProcessor paymentProcessor;
    private final AuditLog auditLog;
    private final DeadLetterQueue dlq;
    private final ReconciliationEngine reconciliationEngine;

    public Simulation(TopicMessageProcessor topicProcessor,
                      SQSQueue positionQueue, SQSQueue paymentQueue,
                      QueueMessageProcessor positionProcessor, QueueMessageProcessor paymentProcessor,
                      AuditLog auditLog, DeadLetterQueue dlq, ReconciliationEngine reconciliationEngine) {
        this.topicProcessor = Objects.requireNonNull(topicProcessor, "topicProcessor");
        this.positionQueue = Objects.requireNonNull(positionQueue, "positionQueue");
        this.paymentQueue = Objects.requireNonNull(paymentQueue, "paymentQueue");
        this.positionProcessor = Objects.requireNonNull(positionProcessor, "positionProcessor");
        this.paymentProcessor = Objects.requireNonNull(paymentProcessor, "paymentProcessor");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.dlq = Objects.requireNonNull(dlq, "dlq");
        this.reconciliationEngine = Objects.requireNonNull(reconciliationEngine, "reconciliationEngine");
    }

    @Override
    public void run(String... args) throws InterruptedException {
        Thread positionThread = Thread.ofPlatform().name("qmp-position").start(positionProcessor);
        Thread paymentThread = Thread.ofPlatform().name("qmp-payment").start(paymentProcessor);

        try {
            // close() waits for every send, with no timeout, so no pill goes in ahead of a real message.
            // execute() rather than submit(): an exception in a sender is printed, not hidden in a Future.
            try (ExecutorService senders = Executors.newFixedThreadPool(SampleMessages.BY_BANK.size())) {
                SampleMessages.BY_BANK.forEach((id, messages) -> {
                    Bank bank = new Bank(id, topicProcessor);
                    senders.execute(() -> messages.forEach(bank::send));
                });
            }
        } finally {
            positionQueue.push(SQSQueue.POISON);
            paymentQueue.push(SQSQueue.POISON);
            positionThread.join();
            paymentThread.join();
        }

        printSummary();
        System.out.println();
        System.out.println("Reconciliation:");
        reconciliationEngine.reconcile();
    }

    private void printSummary() {
        System.out.println();
        System.out.println("Audit log (" + auditLog.entries().size() + " entries):");
        auditLog.entries().forEach(e -> System.out.println("  " + e));
        System.out.println("Dead letter queue (" + dlq.messages().size() + " messages):");
        dlq.messages().forEach(m -> System.out.println("  " + m));
    }
}
