package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class ProcessorTestSupport {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    static Map<Outcome, Integer> countByOutcome(AuditLog auditLog) {
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        auditLog.entries().forEach(e -> counts.merge(e.outcome(), 1, Integer::sum));
        return counts;
    }

    /** POSTs every sample message to a running processor, the way the banks will, and checks each reply is 202. */
    static void sendSampleMessages(int port) throws IOException, InterruptedException {
        List<String> all = SampleMessages.BY_BANK.values().stream().flatMap(List::stream).toList();
        for (String json : all) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/messages"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            assertEquals(202, CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }

    /**
     * Waits for the audit log to reach the expected size. The reply comes back before a queue processor thread has
     * handled the message, so the entries appear a little after the last POST returns.
     */
    static void awaitAuditEntries(AuditLog auditLog, int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (auditLog.entries().size() < expected) {
            if (Instant.now().isAfter(deadline)) {
                fail("expected " + expected + " audit entries, found " + auditLog.entries().size());
            }
            Thread.sleep(20);
        }
    }

    private ProcessorTestSupport() {}
}
