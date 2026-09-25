package com.bankcorp.banksim;

import java.util.Objects;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import tools.jackson.databind.json.JsonMapper;

/**
 * Takes outcome events from this service's queue on the broker and stores them. When the acknowledgement is sent,
 * before or after this method runs, is set by spring.rabbitmq.listener.simple.acknowledge-mode.
 */
public class OutcomeListener {

    /** This service's queue. It is durable, so outcomes wait in it while this service is down. */
    static final String QUEUE = "reconciliation.outcomes";

    private final OutcomeStore store;
    private final JsonMapper mapper;
    private final ListenerChaos chaos;

    public OutcomeListener(OutcomeStore store, JsonMapper mapper, ListenerChaos chaos) {
        this.store = Objects.requireNonNull(store, "store");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.chaos = Objects.requireNonNull(chaos, "chaos");
    }

    @RabbitListener(queues = QUEUE)
    public void receive(String json, @Header(AmqpHeaders.REDELIVERED) boolean redelivered) {
        AuditEntry entry = mapper.readValue(json, AuditEntry.class);
        chaos.beforeStore(entry);
        store.add(entry);
        System.out.println("[RECON] stored " + entry.outcome() + " for " + entry.messageId()
                + (redelivered ? " (redelivered)" : ""));
        chaos.afterStore(entry);
    }
}
