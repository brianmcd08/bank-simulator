package com.bankcorp.banksim;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Pushes each audit entry to the reconciliation service, fire-and-forget: one attempt, and a failure is only logged.
 * DUPLICATE entries stay here; reconciliation only hears about real outcomes.
 *
 * <p>The POST runs on this class's own single thread, not the caller's, so a slow or missing reconciliation service
 * never holds up message processing. One thread also keeps the pushes in the order the entries were written.
 */
public class OutcomePublisher implements Consumer<AuditEntry>, AutoCloseable {

    private final RestClient reconciliation;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofPlatform().name("outcome-publisher").unstarted(runnable));

    /** {@code reconciliation} must already point at the reconciliation service. */
    public OutcomePublisher(RestClient reconciliation) {
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation");
    }

    @Override
    public void accept(AuditEntry entry) {
        if (entry.outcome() == Outcome.DUPLICATE) {
            return;
        }
        sender.execute(() -> push(entry));
    }

    private void push(AuditEntry entry) {
        try {
            reconciliation.post()
                    .uri("/outcomes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(entry)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            System.out.println("[PUSH] lost " + entry.outcome() + " for " + entry.messageId() + ": " + e.getMessage());
        }
    }

    /** Waits for pushes already handed over, then stops the thread. Spring calls this at shutdown. */
    @Override
    public void close() {
        sender.close();
    }
}
