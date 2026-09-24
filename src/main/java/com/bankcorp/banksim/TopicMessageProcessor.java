package com.bankcorp.banksim;

import java.util.Objects;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses raw JSON from the banks, rejects what it cannot use, and publishes the rest to the topic.
 * Called from several bank threads at once, so it keeps no per-call state. The JsonMapper is immutable and safe to
 * share.
 *
 * <p>Every rejection is audited as PERMANENT_FAILURE and process never throws. The Python version retried a
 * failed parse three times, but parsing the same string again always fails the same way, so there is no retry.
 */
public class TopicMessageProcessor {

    private final JsonMapper mapper;
    private final SNSTopic topic;
    private final AuditLog auditLog;

    public TopicMessageProcessor(JsonMapper mapper, SNSTopic topic, AuditLog auditLog) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.topic = Objects.requireNonNull(topic, "topic");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
    }

    public void process(String raw) {
        JsonNode root;
        try {
            root = mapper.readTree(raw == null ? "" : raw);
        } catch (JacksonException e) {
            reject(null, null, null, "unparseable JSON");
            return;
        }

        // stringValue(null) is null for a missing field, a JSON null, or a non-string value. asString() would turn
        // a JSON null into the string "null".
        String bankId = root.path("bank_id").stringValue(null);
        String loanId = root.path("loan_id").stringValue(null);
        String eventTypeName = root.path("event_type").stringValue(null);
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
