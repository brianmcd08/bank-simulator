package com.bankcorp.banksim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class TopicMessageProcessorTest {

    private SQSQueue positions;
    private SQSQueue payments;
    private AuditLog auditLog;
    private TopicMessageProcessor processor;

    @BeforeEach
    void setUp() {
        positions = new SQSQueue();
        payments = new SQSQueue();
        SNSTopic topic = new SNSTopic();
        topic.registerQueue(positions, EventType.POSITION_UPDATE);
        topic.registerQueue(payments, EventType.PAYMENT);
        auditLog = new AuditLog();
        processor = new TopicMessageProcessor(JsonMapper.shared(), topic, auditLog);
    }

    @Test
    void validMessageIsPublishedToItsQueue() throws InterruptedException {
        processor.process(Main.WF_POSITION);

        Message message = positions.poll(Duration.ofSeconds(1));
        assertNotNull(message);
        assertEquals("wf_1334566", message.bankId());
        assertEquals("loan_002", message.loanId());
        assertEquals(EventType.POSITION_UPDATE, message.eventType());
        assertEquals(0, payments.size());
        assertEquals(List.of(), auditLog.entries());
    }

    @Test
    void invalidBankIsAuditedWithItsFields() {
        processor.process(Main.INVALID_BANK);

        AuditEntry entry = onlyRejection();
        assertEquals("unknown_999", entry.bankId());
        assertEquals("loan_003", entry.loanId());
        assertEquals(EventType.PAYMENT, entry.eventType());
    }

    @Test
    void unknownEventTypeIsAuditedWithoutOne() {
        processor.process("{\"bank_id\": \"boa_9423213\", \"loan_id\": \"loan_001\", \"event_type\": \"REFUND\"}");

        AuditEntry entry = onlyRejection();
        assertEquals("boa_9423213", entry.bankId());
        assertNull(entry.eventType());
    }

    @Test
    void jsonNullIsAMissingFieldNotTheStringNull() {
        processor.process("{\"bank_id\": \"boa_9423213\", \"loan_id\": null, \"event_type\": \"PAYMENT\"}");

        assertNull(onlyRejection().loanId());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"bank_id\": \"boa_9423213\", \"event_type\": \"PAYMENT\"}",
        "{\"bank_id\": null, \"loan_id\": \"loan_001\", \"event_type\": \"PAYMENT\"}",
        "{\"bank_id\": 42, \"loan_id\": \"loan_001\", \"event_type\": \"PAYMENT\"}",
        "{not json",
        "",
        "42",
        "null",
        "[]"
    })
    void unusableInputIsRejected(String raw) {
        processor.process(raw);

        onlyRejection();
    }

    private AuditEntry onlyRejection() {
        assertEquals(0, positions.size());
        assertEquals(0, payments.size());
        List<AuditEntry> entries = auditLog.entries();
        assertEquals(1, entries.size());
        AuditEntry entry = entries.getFirst();
        assertEquals(Outcome.PERMANENT_FAILURE, entry.outcome());
        return entry;
    }
}
