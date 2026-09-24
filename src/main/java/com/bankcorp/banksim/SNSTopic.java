package com.bankcorp.banksim;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Routes each message to every queue registered for its event type.
 * Register all queues before any thread starts. After that the routing map is only read, which is what makes it
 * safe to publish from several bank threads without a lock.
 */
public class SNSTopic {

    private final Map<EventType, List<SQSQueue>> queuesByType = new EnumMap<>(EventType.class);

    public void registerQueue(SQSQueue queue, EventType... eventTypes) {
        Objects.requireNonNull(queue, "queue");
        for (EventType eventType : eventTypes) {
            queuesByType.computeIfAbsent(eventType, t -> new ArrayList<>()).add(queue);
        }
    }

    public void publish(Message message) {
        for (SQSQueue queue : queuesByType.getOrDefault(message.eventType(), List.of())) {
            queue.push(message);
        }
    }
}
