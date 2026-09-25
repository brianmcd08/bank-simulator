package com.bankcorp.banksim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Starts the reconciliation service: takes outcome events from the broker and reconciles them on request. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ReconciliationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationApplication.class, args);
    }
}
