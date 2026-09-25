package com.bankcorp.banksim;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OutcomeController {

    private final OutcomeStore store;
    private final ReconciliationEngine engine;

    public OutcomeController(OutcomeStore store, ReconciliationEngine engine) {
        this.store = Objects.requireNonNull(store, "store");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /** One outcome event from the processor. */
    @PostMapping("/outcomes")
    public void receive(@RequestBody AuditEntry entry) {
        store.add(entry);
    }

    /** Everything this service has been told, so it can be compared with the processor's /audit. */
    @GetMapping("/outcomes")
    public List<AuditEntry> outcomes() {
        return store.entries();
    }

    @GetMapping("/reconcile")
    public Map<String, List<AuditEntry>> reconcile() {
        return Map.of(
                "differentOutcomesForSameLoan", engine.diffOutcomesForSameLoan(),
                "duplicateMessages", engine.duplicateMessages(),
                "stuckInPending", engine.stuckInPending());
    }
}
