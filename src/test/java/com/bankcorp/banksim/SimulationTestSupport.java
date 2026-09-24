package com.bankcorp.banksim;

import java.util.EnumMap;
import java.util.Map;

final class SimulationTestSupport {

    static Map<Outcome, Integer> countByOutcome(AuditLog auditLog) {
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        auditLog.entries().forEach(e -> counts.merge(e.outcome(), 1, Integer::sum));
        return counts;
    }

    private SimulationTestSupport() {}
}
