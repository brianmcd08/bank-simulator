package com.bankcorp.banksim;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The processor's HTTP edge. Translates requests into calls on the pipeline objects, which know nothing about HTTP.
 */
@RestController
public class MessageController {

    private final TopicMessageProcessor topicProcessor;
    private final AuditLog auditLog;
    private final DeadLetterQueue dlq;
    private final ReconciliationEngine reconciliationEngine;

    public MessageController(TopicMessageProcessor topicProcessor, AuditLog auditLog, DeadLetterQueue dlq,
                             ReconciliationEngine reconciliationEngine) {
        this.topicProcessor = Objects.requireNonNull(topicProcessor, "topicProcessor");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.dlq = Objects.requireNonNull(dlq, "dlq");
        this.reconciliationEngine = Objects.requireNonNull(reconciliationEngine, "reconciliationEngine");
    }

    /**
     * 202, not 200: the message is validated and queued here, but a queue processor thread handles it later, so its
     * outcome does not exist yet when the reply goes back. A rejected message also gets 202, because process() does
     * not report rejections.
     */
    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void receive(@RequestBody String json) {
        topicProcessor.process(json);
    }

    @GetMapping("/audit")
    public List<AuditEntry> audit() {
        return auditLog.entries();
    }

    @GetMapping("/dlq")
    public List<Message> dlq() {
        return dlq.messages();
    }

    /** In-process until reconciliation becomes its own service. */
    @GetMapping("/reconcile")
    public Map<String, List<AuditEntry>> reconcile() {
        return Map.of(
                "differentOutcomesForSameLoan", reconciliationEngine.diffOutcomesForSameLoan(),
                "duplicateMessages", reconciliationEngine.duplicateMessages(),
                "stuckInPending", reconciliationEngine.stuckInPending());
    }
}
