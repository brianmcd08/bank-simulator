package com.bankcorp.banksim;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.web.client.RestClient;

/**
 * Sends every bank's sample messages once, each bank on its own thread, then returns. There is no web server in this
 * service, so the application exits when this does, with exit code 1 if any message was given up on.
 */
public class SendRun implements CommandLineRunner, ExitCodeGenerator {

    private final RestClient processorClient;
    private final Duration firstWait;

    /** Written by every bank thread, read by main after the pool has closed. */
    private final AtomicInteger gaveUp = new AtomicInteger();

    public SendRun(RestClient processorClient, Duration firstWait) {
        this.processorClient = Objects.requireNonNull(processorClient, "processorClient");
        this.firstWait = Objects.requireNonNull(firstWait, "firstWait");
    }

    @Override
    public void run(String... args) {
        // close() waits for every bank to finish. execute() rather than submit(): an exception in a sender is
        // printed, not hidden in a Future. Bank.send no longer throws, so that should not happen.
        try (ExecutorService senders = Executors.newFixedThreadPool(SampleMessages.BY_BANK.size())) {
            SampleMessages.BY_BANK.forEach((id, messages) -> {
                Bank bank = new Bank(id, processorClient, firstWait);
                senders.execute(() -> messages.forEach(json -> {
                    if (!bank.send(json)) {
                        gaveUp.incrementAndGet();
                    }
                }));
            });
        }
        if (gaveUp.get() > 0) {
            System.out.println(gaveUp.get() + " message(s) were given up on");
        }
    }

    /** Spring asks for this when the application exits. 0 means every message reached the processor. */
    @Override
    public int getExitCode() {
        return gaveUp.get() == 0 ? 0 : 1;
    }

    int gaveUp() {
        return gaveUp.get();
    }
}
