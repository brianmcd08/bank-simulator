package com.bankcorp.banksim;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes each audit entry to the broker's outcomes exchange, as JSON. The broker holds it in the reconciliation
 * service's queue until that service takes it, so reconciliation being down no longer loses outcomes.
 * DUPLICATE entries stay here; reconciliation only hears about real outcomes.
 *
 * <p>The publish runs on this class's own single thread, not the caller's, so a slow or missing broker never holds
 * up message processing. One thread also keeps the outcomes in the order the entries were written.
 *
 * <p>Still one attempt: if the broker itself is down, the outcome is logged and lost. There are no publisher
 * confirms, so an outcome is also lost if the broker has no queue bound to the exchange yet.
 */
public class OutcomePublisher implements Consumer<AuditEntry>, AutoCloseable {

    /** The contract with the reconciliation service, which binds its queue to this exchange with this key. */
    static final String EXCHANGE = "banksim.outcomes";
    static final String ROUTING_KEY = "outcome";

    private final RabbitTemplate rabbit;
    private final JsonMapper mapper;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofPlatform().name("outcome-publisher").unstarted(runnable));

    public OutcomePublisher(RabbitTemplate rabbit, JsonMapper mapper) {
        this.rabbit = Objects.requireNonNull(rabbit, "rabbit");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void accept(AuditEntry entry) {
        if (entry.outcome() == Outcome.DUPLICATE) {
            return;
        }
        sender.execute(() -> publish(entry));
    }

    private void publish(AuditEntry entry) {
        try {
            rabbit.convertAndSend(EXCHANGE, ROUTING_KEY, mapper.writeValueAsString(entry));
        } catch (AmqpException e) {
            System.out.println(
                    "[PUBLISH] lost " + entry.outcome() + " for " + entry.messageId() + ": " + e.getMessage());
        }
    }

    /** Waits for publishes already handed over, then stops the thread. Spring calls this at shutdown. */
    @Override
    public void close() {
        sender.close();
    }
}
