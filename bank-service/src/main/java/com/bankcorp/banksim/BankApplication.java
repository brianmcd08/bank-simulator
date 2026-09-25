package com.bankcorp.banksim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the bank service: every bank sends its messages to the processor, then the application exits. The exit
 * code comes from SendRun: non-zero if any message was given up on.
 */
@SpringBootApplication
public class BankApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(BankApplication.class, args)));
    }
}
