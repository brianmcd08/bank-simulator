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
        processor = new TopicMessageProcessor(JsonMapper.shared(), topic, auditLog, new SeenMessageIds());
    }

    @Test
    void validMessageIsPublishedToItsQueue() throws InterruptedException {
        assertEquals(Receipt.ACCEPTED, processor.process(SampleMessages.WF_POSITION));

        Message message = positions.poll(Duration.ofSeconds(1));
        assertNotNull(message);
        assertEquals("wf_1334566", message.bankId());
        assertEquals("loan_002", message.loanId());
        assertEquals(EventType.POSITION_UPDATE, message.eventType());
        assertEquals("wf-1", message.messageId());
        assertEquals(0, payments.size());
        assertEquals(List.of(), auditLog.entries());
    }

    @Test
    void invalidBankIsAuditedWithItsFields() {
        processor.process(SampleMessages.INVALID_BANK);

        AuditEntry entry = onlyRejection();
        assertEquals("unknown_999", entry.bankId());
        assertEquals("loan_003", entry.loanId());
        assertEquals(EventType.PAYMENT, entry.eventType());
    }

    @Test
    void unknownEventTypeIsAuditedWithoutOne() {
        processor.process("{\"message_id\": \"m-1\", \"bank_id\": \"boa_9423213\", \"loan_id\": \"loan_001\", "
                + "\"event_type\": \"REFUND\"}");

        AuditEntry entry = onlyRejection();
        assertEquals("boa_9423213", entry.bankId());
        assertNull(entry.eventType());
    }

    @Test
    void jsonNullIsAMissingFieldNotTheStringNull() {
        processor.process("{\"message_id\": \"m-1\", \"bank_id\": \"boa_9423213\", \"loan_id\": null, "
                + "\"event_type\": \"PAYMENT\"}");

        assertNull(onlyRejection().loanId());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"bank_id\": \"boa_9423213\", \"event_type\": \"PAYMENT\", \"message_id\": \"m-1\"}",
        "{\"bank_id\": \"boa_9423213\", \"loan_id\": \"loan_001\", \"event_type\": \"PAYMENT\"}",
        "{\"bank_id\": null, \"loan_id\": \"loan_001\", \"event_type\": \"PAYMENT\"}",
        "{\"bank_id\": 42, \"loan_id\": \"loan_001\", \"event_type\": \"PAYMENT\"}",
        "{not json",
        "",
        "42",
        "null",
        "[]"
    })
    void unusableInputIsRejected(String raw) {
        assertEquals(Receipt.REJECTED, processor.process(raw));

        onlyRejection();
    }

    @Test
    void resendWithTheSameIdIsNotPublishedAgain() throws InterruptedException {
        processor.process(SampleMessages.WF_POSITION);

        assertEquals(Receipt.DUPLICATE, processor.process(SampleMessages.WF_POSITION));

        assertNotNull(positions.poll(Duration.ofSeconds(1)));
        assertEquals(0, positions.size());
        AuditEntry entry = auditLog.entries().getFirst();
        assertEquals(1, auditLog.entries().size());
        assertEquals(Outcome.DUPLICATE, entry.outcome());
        assertEquals("wf-1", entry.messageId());
    }

    @Test
    void sameBankLoanAndTypeWithADifferentIdIsANewMessage() {
        processor.process(SampleMessages.BOA_PAYMENT);

        assertEquals(Receipt.ACCEPTED, processor.process(SampleMessages.BOA_PAYMENT_DUPLICATE));

        assertEquals(2, payments.size());
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
