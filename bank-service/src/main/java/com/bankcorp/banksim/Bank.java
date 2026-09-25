package com.bankcorp.banksim;

import java.time.Duration;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * A bank that sends raw JSON messages to the processor service. Until the split this called
 * TopicMessageProcessor.process directly; the processor now lives in another JVM, so the call is an HTTP POST.
 *
 * <p>If the processor cannot be reached, the send is retried with a wait that doubles each time, so the attempts
 * cover a processor that is restarting. A reply with an error status is not retried: the processor answered, and
 * sending the same request again gets the same answer.
 */
public class Bank {

    static final int MAX_ATTEMPTS = 5;

    private final Banks bank;
    private final RestClient processor;
    private final Duration firstWait;

    /**
     * {@code processor} must already point at the processor service; see BankConfig. With 5 attempts and a first
     * wait of 1s, the waits are 1, 2, 4 and 8s, so the attempts cover 15 seconds of the processor being down.
     */
    public Bank(Banks bank, RestClient processor, Duration firstWait) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.firstWait = Objects.requireNonNull(firstWait, "firstWait");
    }

    public Banks bank() {
        return bank;
    }

    /** Returns true if the processor accepted the message, false if this bank gave up on it. Never throws. */
    public boolean send(String json) {
        Duration wait = firstWait;
        for (int attempt = 1; ; attempt++) {
            try {
                processor.post()
                        .uri("/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json)
                        .retrieve()
                        .toBodilessEntity();
                System.out.println("[" + bank + "] sent " + json);
                return true;
            } catch (RestClientResponseException e) {
                System.out.println("[" + bank + "] processor replied " + e.getStatusCode() + ", not retrying: " + json);
                return false;
            } catch (ResourceAccessException e) {
                if (attempt == MAX_ATTEMPTS) {
                    System.out.println("[" + bank + "] gave up after " + attempt + " attempts: " + json);
                    return false;
                }
                System.out.println("[" + bank + "] processor unreachable, attempt " + attempt + " of " + MAX_ATTEMPTS
                        + ", retrying in " + wait.toMillis() + "ms");
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                wait = wait.multipliedBy(2);
            }
        }
    }
}
