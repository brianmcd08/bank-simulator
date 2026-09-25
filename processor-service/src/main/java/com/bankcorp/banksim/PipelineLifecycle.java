package com.bankcorp.banksim;

import java.util.Objects;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.SmartLifecycle;

/**
 * Runs one queue processor thread per queue for as long as the service is up. This replaces Simulation: there is no
 * longer a moment when the banks are done, so the threads start with the service and stop when it shuts down.
 *
 * <p>The phase is just below the web server's. Spring starts lower phases first and stops them last, so the
 * processors are running before the first request can arrive, and the poison pills go in only after the web server
 * has stopped taking requests. A message published after its queue's pill would never be processed.
 */
public class PipelineLifecycle implements SmartLifecycle {

    private final SQSQueue positionQueue;
    private final SQSQueue paymentQueue;
    private final QueueMessageProcessor positionProcessor;
    private final QueueMessageProcessor paymentProcessor;

    private Thread positionThread;
    private Thread paymentThread;
    private volatile boolean running;

    public PipelineLifecycle(SQSQueue positionQueue, SQSQueue paymentQueue,
                             QueueMessageProcessor positionProcessor, QueueMessageProcessor paymentProcessor) {
        this.positionQueue = Objects.requireNonNull(positionQueue, "positionQueue");
        this.paymentQueue = Objects.requireNonNull(paymentQueue, "paymentQueue");
        this.positionProcessor = Objects.requireNonNull(positionProcessor, "positionProcessor");
        this.paymentProcessor = Objects.requireNonNull(paymentProcessor, "paymentProcessor");
    }

    @Override
    public void start() {
        positionThread = Thread.ofPlatform().name("qmp-position").start(positionProcessor);
        paymentThread = Thread.ofPlatform().name("qmp-payment").start(paymentProcessor);
        running = true;
    }

    @Override
    public void stop() {
        positionQueue.push(SQSQueue.POISON);
        paymentQueue.push(SQSQueue.POISON);
        try {
            positionThread.join();
            paymentThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return WebServerApplicationContext.START_STOP_LIFECYCLE_PHASE - 1;
    }
}
