package com.bankcorp.banksim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Starts Spring, which builds the pipeline from {@link PipelineConfig} and runs {@link Simulation}. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BankSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankSimulatorApplication.class, args);
    }
}
