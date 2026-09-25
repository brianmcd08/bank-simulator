package com.bankcorp.banksim;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReconciliationConfig {

    @Bean
    OutcomeStore outcomeStore() {
        return new OutcomeStore();
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
