package com.bankcorp.banksim;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds and wires every object in the pipeline. This is what the Python singletons stood in for: one AuditLog,
 * one DeadLetterQueue and one TopicMessageProcessor, shared by whoever needs them. The pipeline classes themselves
 * stay plain Java with no Spring annotations, so all the wiring is here and the unit tests build them by hand.
 *
 * <p>There are two queues and two processors of the same type, so those are named and injected by qualifier.
 * The JsonMapper comes from Spring Boot's Jackson auto-configuration.
 */
@Configuration
public class PipelineConfig {

    @Bean
    AuditLog auditLog() {
        return new AuditLog();
    }

    @Bean
    DeadLetterQueue deadLetterQueue() {
        return new DeadLetterQueue();
    }

    @Bean
    SQSQueue positionQueue() {
        return new SQSQueue();
    }

    @Bean
    SQSQueue paymentQueue() {
        return new SQSQueue();
    }

    /** Every queue is registered here, before any thread starts, which is what makes publishing lock-free. */
    @Bean
    SNSTopic snsTopic(@Qualifier("positionQueue") SQSQueue positionQueue,
                      @Qualifier("paymentQueue") SQSQueue paymentQueue) {
        SNSTopic topic = new SNSTopic();
        topic.registerQueue(positionQueue, EventType.POSITION_UPDATE);
        topic.registerQueue(paymentQueue, EventType.PAYMENT);
        return topic;
    }

    @Bean
    SeenMessageIds seenMessageIds() {
        return new SeenMessageIds();
    }

    @Bean
    TopicMessageProcessor topicMessageProcessor(JsonMapper jsonMapper, SNSTopic snsTopic, AuditLog auditLog,
                                                SeenMessageIds seenMessageIds) {
        return new TopicMessageProcessor(jsonMapper, snsTopic, auditLog, seenMessageIds);
    }

    @Bean
    QueueMessageProcessor positionProcessor(@Qualifier("positionQueue") SQSQueue positionQueue,
                                            SimulatorProperties properties, DeadLetterQueue dlq, AuditLog auditLog) {
        return new QueueMessageProcessor(
                positionQueue, FailurePolicy.randomRate(properties.positionFailureRate()), dlq, auditLog);
    }

    @Bean
    QueueMessageProcessor paymentProcessor(@Qualifier("paymentQueue") SQSQueue paymentQueue,
                                           SimulatorProperties properties, DeadLetterQueue dlq, AuditLog auditLog) {
        return new QueueMessageProcessor(
                paymentQueue, FailurePolicy.randomRate(properties.paymentFailureRate()), dlq, auditLog);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ReconciliationEngine reconciliationEngine(AuditLog auditLog, Clock clock) {
        return new ReconciliationEngine(auditLog, clock);
    }

    @Bean
    ReplyChaos replyChaos(ChaosProperties chaosProperties, JsonMapper jsonMapper) {
        return new ReplyChaos(chaosProperties, jsonMapper);
    }

    @Bean
    PipelineLifecycle pipelineLifecycle(@Qualifier("positionQueue") SQSQueue positionQueue,
                                        @Qualifier("paymentQueue") SQSQueue paymentQueue,
                                        @Qualifier("positionProcessor") QueueMessageProcessor positionProcessor,
                                        @Qualifier("paymentProcessor") QueueMessageProcessor paymentProcessor) {
        return new PipelineLifecycle(positionQueue, paymentQueue, positionProcessor, paymentProcessor);
    }
}
