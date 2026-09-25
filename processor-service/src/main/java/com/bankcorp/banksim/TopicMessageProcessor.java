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
 *
 * <p>The message id comes from the bank, which creates it once and sends the same id on every retry. A message whose
 * id was already accepted is audited as DUPLICATE and not published again, so a resend has no further effect.
 * An accepted message is audited as PENDING; a queue processor writes its final outcome later.
 */
public class TopicMessageProcessor {

    private final JsonMapper mapper;
    private final SNSTopic topic;
    private final AuditLog auditLog;
    private final SeenMessageIds seenIds;

    public TopicMessageProcessor(JsonMapper mapper, SNSTopic topic, AuditLog auditLog, SeenMessageIds seenIds) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.topic = Objects.requireNonNull(topic, "topic");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.seenIds = Objects.requireNonNull(seenIds, "seenIds");
    }

    public Receipt process(String raw) {
        JsonNode root;
        try {
            root = mapper.readTree(raw == null ? "" : raw);
        } catch (JacksonException e) {
            return reject(null, null, null, "unparseable JSON");
        }

        // stringValue(null) is null for a missing field, a JSON null, or a non-string value. asString() would turn
        // a JSON null into the string "null".
        String bankId = root.path("bank_id").stringValue(null);
        String loanId = root.path("loan_id").stringValue(null);
        String eventTypeName = root.path("event_type").stringValue(null);
        String messageId = root.path("message_id").stringValue(null);
        if (bankId == null || loanId == null || eventTypeName == null || messageId == null) {
            return reject(null, bankId, loanId, "missing field");
        }

        EventType eventType;
        try {
            eventType = EventType.valueOf(eventTypeName);
        } catch (IllegalArgumentException e) {
            return reject(null, bankId, loanId, "unknown event_type: " + eventTypeName);
        }

        Message message = new Message(bankId, loanId, eventType, messageId);
        if (!Banks.isValidId(bankId)) {
            auditLog.write(eventType, messageId, bankId, loanId, Outcome.PERMANENT_FAILURE);
            System.out.println("[TOPIC] Invalid bank_id: " + bankId + " — rejecting message");
            return Receipt.REJECTED;
        }

        if (!seenIds.firstSighting(messageId)) {
            auditLog.write(eventType, messageId, bankId, loanId, Outcome.DUPLICATE);
            System.out.println("[TOPIC] Already have message " + messageId + " — not processing it again");
            return Receipt.DUPLICATE;
        }

        // PENDING goes in before the message is published, so it is always ahead of the final outcome in the log.
        auditLog.write(eventType, messageId, bankId, loanId, Outcome.PENDING);
        topic.publish(message);
        return Receipt.ACCEPTED;
    }

    /** A rejected message may have no usable id of its own, so its audit entry gets a new one. */
    private Receipt reject(EventType eventType, String bankId, String loanId, String reason) {
        auditLog.write(eventType, UUID.randomUUID().toString(), bankId, loanId, Outcome.PERMANENT_FAILURE);
        System.out.println("[TOPIC] " + reason + " — rejecting message");
        return Receipt.REJECTED;
    }
}
