package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * A fake processor stands in for the real one. withException(ConnectException) is what RestClient sees when nothing
 * is listening, so it surfaces as ResourceAccessException, the same as a stopped processor.
 */
class BankTest {

    private static final String URL = "http://processor/messages";
    private static final String JSON = SampleMessages.WF_POSITION;

    private MockRestServiceServer server;
    private Bank bank;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://processor");
        server = MockRestServiceServer.bindTo(builder).build();
        bank = new Bank(Banks.WELLS_FARGO, builder.build(), JsonMapper.shared(), Duration.ofMillis(1));
    }

    @Test
    void acceptedFirstTime() {
        server.expect(once(), requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertTrue(bank.send(JSON));
        server.verify();
    }

    @Test
    void retriesWhileUnreachableThenSucceeds() {
        server.expect(times(2), requestTo(URL)).andRespond(withException(new ConnectException()));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.ACCEPTED));

        assertTrue(bank.send(JSON));
        server.verify();
    }

    /** A resend must carry the same id, or the processor cannot tell it from a new message. */
    @Test
    void everyAttemptCarriesTheSameMessageId() {
        List<String> ids = new ArrayList<>();
        RequestMatcher recordId = request -> ids.add(
                JsonMapper.shared().readTree(((MockClientHttpRequest) request).getBodyAsString())
                        .path("message_id").stringValue(null));
        server.expect(times(2), requestTo(URL)).andExpect(recordId).andRespond(withException(new ConnectException()));
        server.expect(once(), requestTo(URL)).andExpect(recordId).andRespond(withStatus(HttpStatus.ACCEPTED));

        bank.send(JSON);

        assertEquals(3, ids.size());
        assertNotNull(ids.getFirst());
        assertEquals(1, Set.copyOf(ids).size(), ids.toString());
    }

    @Test
    void eachSendGetsANewMessageId() {
        List<String> ids = new ArrayList<>();
        RequestMatcher recordId = request -> ids.add(
                JsonMapper.shared().readTree(((MockClientHttpRequest) request).getBodyAsString())
                        .path("message_id").stringValue(null));
        server.expect(times(2), requestTo(URL)).andExpect(recordId).andRespond(withStatus(HttpStatus.ACCEPTED));

        bank.send(JSON);
        bank.send(JSON);

        assertEquals(2, Set.copyOf(ids).size(), ids.toString());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        server.expect(times(Bank.MAX_ATTEMPTS), requestTo(URL)).andRespond(withException(new ConnectException()));

        assertFalse(bank.send(JSON));
        server.verify();
    }

    @Test
    void errorStatusIsNotRetried() {
        server.expect(once(), requestTo(URL)).andRespond(withBadRequest());

        assertFalse(bank.send(JSON));
        server.verify();
    }
}
