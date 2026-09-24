# bank-simulator (Java)

A Java port of a small Python simulation of a bank message pipeline. Three banks send loan events as JSON. A topic
validates each message and routes it by event type to a queue. One processor per queue retries failures and moves
messages that keep failing to a dead letter queue. Every outcome goes into an audit log, and a reconciliation pass
looks for problems at the end.

## Build, test, run

Needs Java 25 and Maven.

```sh
mvn test                     # run the tests
mvn -q compile exec:java     # run the simulation
```

## How it runs

```
bank threads (3, fixed pool)          processor threads (1 per queue)
  Bank.send(json)                        QueueMessageProcessor.run()
    -> TopicMessageProcessor               take() -> up to 3 attempts
         validate, audit rejections          -> SUCCESS, or DLQ
         -> SNSTopic.publish                 -> AuditLog
              -> SQSQueue  ------------------^
```

- The banks and the queue processors run at the same time.
- Shared state is the two queues (`LinkedBlockingQueue`), the audit log and the dead letter queue (both
  `synchronized`, readers get a copy), and the topic's routing map (built before any thread starts, then only read).
- Shutdown uses a poison pill. `Main` closes the sender pool, which waits for every send. Then it puts
  `SQSQueue.POISON` on each queue and joins the processor threads. Each processor stops when it takes the pill.
  The pills are pushed from a `finally` block, so an error partway through cannot leave the program hanging.
- Reconciliation runs on `main` after every other thread has stopped.

## Things that differ from the Python version

- No singletons. `Main` builds one `AuditLog`, `DeadLetterQueue` and `TopicMessageProcessor` and passes them in
  through constructors.
- One `Bank` class instead of one subclass per bank. The subclasses were identical; they come back when a bank's
  behavior actually differs.
- `Message` is an immutable record with no outcome field. The outcome lives only in the audit log.
- The failure rate is a real probability (the Python compared a 1..100 roll against 0.4), and it is injectable
  through `FailurePolicy` so tests can force every path.
- Anything the topic cannot use (bad JSON, a missing field, an unknown event type, an invalid bank) is audited as
  `PERMANENT_FAILURE`. There is no parse retry, because parsing the same string again fails the same way.

## Output varies from run to run

With a 40% failure rate and 3 attempts, about 6% of POSITION messages reach the dead letter queue. The banks send at
the same time, so the order of audit entries also changes. That changes which entry reconciliation reports as
"the duplicate" or as having a different outcome.

"Stuck in pending" is kept from the Python version, but nothing writes `PENDING` to the audit log yet, so it never
fires.

## Next

Phase 2 converts this to Spring Boot. The constructor injection here maps directly onto Spring's container.
