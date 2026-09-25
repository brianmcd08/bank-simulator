package com.bankcorp.banksim;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class BankConfig {

    /** One client for every bank, pointed at the processor. RestClient is safe to share between threads. */
    @Bean
    RestClient processorClient(RestClient.Builder builder, @Value("${banksim.processor-url}") String processorUrl) {
        return builder.baseUrl(processorUrl).build();
    }

    @Bean
    SendRun sendRun(RestClient processorClient, JsonMapper jsonMapper,
                    @Value("${banksim.first-retry-wait}") Duration firstWait) {
        return new SendRun(processorClient, jsonMapper, firstWait);
    }
}
