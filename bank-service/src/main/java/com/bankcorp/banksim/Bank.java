package com.bankcorp.banksim;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A bank that sends raw JSON messages to the processor service. Until the split this called
 * TopicMessageProcessor.process directly; the processor now lives in another JVM, so the call is an HTTP POST.
 *
 * <p>If the processor cannot be reached, or no reply comes back in time, the send is retried with a wait that
 * doubles each time, so the attempts cover a processor that is restarting. A reply with an error status is not
 * retried: the processor answered, and sending the same request again gets the same answer.
 *
 * <p>With no reply, the processor may already have the message, so a retry can deliver it twice. Each message gets
 * its id once, before the first attempt, and every retry carries that same id, so the processor can recognise a
 * resend and ignore it.
 */
public class Bank {

    static final int MAX_ATTEMPTS = 5;

    private final Banks bank;
    private final RestClient processor;
    private final JsonMapper mapper;
    private final Duration firstWait;

    /**
     * {@code processor} must already point at the processor service; see BankConfig. With 5 attempts and a first
     * wait of 1s, the waits are 1, 2, 4 and 8s, so the attempts cover 15 seconds of the processor being down.
     */
    public Bank(Banks bank, RestClient processor, JsonMapper mapper, Duration firstWait) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.firstWait = Objects.requireNonNull(firstWait, "firstWait");
    }

    public Banks bank() {
        return bank;
    }

    /** Returns true if the processor accepted the message, false if this bank gave up on it. Never throws. */
    public boolean send(String json) {
        String withId = withMessageId(json, UUID.randomUUID().toString());
        Duration wait = firstWait;
        for (int attempt = 1; ; attempt++) {
            try {
                processor.post()
                        .uri("/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(withId)
                        .retrieve()
                        .toBodilessEntity();
                System.out.println("[" + bank + "] sent " + withId);
                return true;
            } catch (RestClientResponseException e) {
                System.out.println(
                        "[" + bank + "] processor replied " + e.getStatusCode() + ", not retrying: " + withId);
                return false;
            } catch (ResourceAccessException e) {
                if (attempt == MAX_ATTEMPTS) {
                    System.out.println("[" + bank + "] gave up after " + attempt + " attempts: " + withId);
                    return false;
                }
                System.out.println("[" + bank + "] " + describe(e) + ", attempt " + attempt + " of " + MAX_ATTEMPTS
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

    /** Adds the message id as the first field. The JSON comes from the bank's own sample data, so it always parses. */
    private String withMessageId(String json, String messageId) {
        ObjectNode original = (ObjectNode) mapper.readTree(json);
        ObjectNode result = mapper.createObjectNode().put("message_id", messageId);
        result.setAll(original);
        return mapper.writeValueAsString(result);
    }

    /**
     * Connection refused and a connect timeout both fail before the request is sent, so the processor cannot have
     * the message. Anything else means the request went out and no reply came back: the processor may have it.
     */
    private static String describe(ResourceAccessException e) {
        Throwable cause = e.getCause();
        if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException) {
            return "processor unreachable";
        }
        return "no reply (the processor may have it)";
    }
}
