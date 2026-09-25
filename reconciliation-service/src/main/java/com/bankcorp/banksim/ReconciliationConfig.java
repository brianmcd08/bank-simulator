package com.bankcorp.banksim;

import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class ReconciliationConfig {

    @Bean
    OutcomeStore outcomeStore() {
        return new OutcomeStore();
    }

    /** Same name as the processor's exchange: that name and the routing key are the contract between them. */
    @Bean
    DirectExchange outcomesExchange() {
        return new DirectExchange("banksim.outcomes");
    }

    /** This service owns its queue. Declared durable, so it and its messages survive this service being down. */
    @Bean
    Queue outcomesQueue() {
        return new Queue(OutcomeListener.QUEUE);
    }

    @Bean
    Binding outcomesBinding(Queue outcomesQueue, DirectExchange outcomesExchange) {
        return BindingBuilder.bind(outcomesQueue).to(outcomesExchange).with("outcome");
    }

    @Bean
    ListenerChaos listenerChaos(ChaosProperties chaosProperties) {
        return new ListenerChaos(chaosProperties);
    }

    @Bean
    OutcomeListener outcomeListener(OutcomeStore store, JsonMapper jsonMapper, ListenerChaos listenerChaos) {
        return new OutcomeListener(store, jsonMapper, listenerChaos);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ReconciliationEngine reconciliationEngine(OutcomeStore store, Clock clock,
                                              @Value("${banksim.pending-limit}") Duration pendingLimit) {
        return new ReconciliationEngine(store, clock, pendingLimit);
    }
}
