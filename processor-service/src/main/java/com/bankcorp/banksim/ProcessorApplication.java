package com.bankcorp.banksim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Starts the processor service: Spring builds the pipeline from {@link PipelineConfig}, {@link PipelineLifecycle}
 * starts the queue processor threads, and the embedded web server listens for bank messages. It runs until stopped.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessorApplication.class, args);
    }
}
