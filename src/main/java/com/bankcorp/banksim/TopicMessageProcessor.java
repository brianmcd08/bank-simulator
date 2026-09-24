package com.bankcorp.banksim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;

/**
 * Parses raw JSON from the banks, rejects what it cannot use, and publishes the rest to the topic.
 * Called from several bank threads at once, so it keeps no per-call state. The ObjectMapper is safe to share once
 * built.
 *
 * <p>Every rejection is audited as PERMANENT_FAILURE and process never throws. The Python version retried a
 * failed parse three times, but parsing the same string again always fails the same way, so there is no retry.
 */
public class TopicMessageProcessor {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SNSTopic topic;
    private final AuditLog auditLog;

    public TopicMessageProcessor(SNSTopic topic, AuditLog auditLog) {
        this.topic = Objects.requireNonNull(topic, "topic");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
    }

    public void process(String raw) {
        JsonNode root;
        try {
            root = mapper.readTree(raw == null ? "" : raw);
        } catch (JsonProcessingException e) {
            reject(null, null, null, "unparseable JSON");
            return;
        }

        // textValue() is null for a missing field, a JSON null, or a non-string value. asText() would turn a JSON
        // null into the string "null".
        String bankId = root.path("bank_id").textValue();
        String loanId = root.path("loan_id").textValue();
        String eventTypeName = root.path("event_type").textValue();
        if (bankId == null || loanId == null || eventTypeName == null) {
            reject(null, bankId, loanId, "missing field");
            return;
        }

        EventType eventType;
        try {
            eventType = EventType.valueOf(eventTypeName);
        } catch (IllegalArgumentException e) {
            reject(null, bankId, loanId, "unknown event_type: " + eventTypeName);
            return;
        }

        Message message = Message.of(bankId, loanId, eventType);
        if (!Banks.isValidId(bankId)) {
            auditLog.write(eventType, message.messageId(), bankId, loanId, Outcome.PERMANENT_FAILURE);
            System.out.println("[TOPIC] Invalid bank_id: " + bankId + " — rejecting message");
            return;
        }

        topic.publish(message);
    }

    private void reject(EventType eventType, String bankId, String loanId, String reason) {
        auditLog.write(eventType, UUID.randomUUID().toString(), bankId, loanId, Outcome.PERMANENT_FAILURE);
        System.out.println("[TOPIC] " + reason + " — rejecting message");
    }
}
