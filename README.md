# bank-simulator (Java)

A Java port of a small Python simulation of a bank message pipeline. Three banks send loan events as JSON. A topic
validates each message and routes it by event type to a queue. One processor per queue retries failures and moves
messages that keep failing to a dead letter queue. Every outcome goes into an audit log, and a reconciliation pass
looks for problems at the end.

## Build, test, run

Needs Java 25 and Maven. Built on Spring Boot 4.1.

The project is being split into separately deployable services (see "Microservices split" below). So far there is
one module, `processor-service`, which runs as a web server on port 8081.

```sh
mvn test                                         # run every module's tests
mvn -q -pl processor-service spring-boot:run     # start the processor; Ctrl+C stops it

curl -i -H 'Content-Type: application/json' \
  -d '{"bank_id":"wf_1334566","loan_id":"loan_002","event_type":"POSITION_UPDATE"}' \
  localhost:8081/messages                        # 202 Accepted
curl -s localhost:8081/audit                     # also /dlq and /reconcile
```

## How it runs

```
POST /messages                        processor threads (1 per queue)
  MessageController.receive(json)        QueueMessageProcessor.run()
    -> TopicMessageProcessor               take() -> up to 3 attempts
         validate, audit rejections          -> SUCCESS, or DLQ
         -> SNSTopic.publish                 -> AuditLog
              -> SQSQueue  ------------------^
```

- `POST /messages` returns 202 once the message is queued. A queue processor handles it afterwards, so the outcome
  does not exist yet when the reply goes back. Rejected messages also get 202, because `process()` does not report
  rejections.
- Tomcat's request threads and the queue processors run at the same time.
- Shared state is the two queues (`LinkedBlockingQueue`), the audit log and the dead letter queue (both
  `synchronized`, readers get a copy), and the topic's routing map (built before any thread starts, then only read).
- `PipelineLifecycle` starts the queue processor threads with the service and, at shutdown, puts `SQSQueue.POISON`
  on each queue and joins them. Its phase is just below the web server's, so the threads are running before the
  first request and the pills go in only after the server has stopped taking requests.
- Reconciliation still runs in-process, on demand, through `GET /reconcile`.

## Spring Boot

Spring builds the objects, runs the web server and starts and stops the queue processor threads.

- `PipelineConfig` builds every object with `@Bean` methods. The pipeline classes have no Spring annotations, so all
  the wiring is in one file and the unit tests construct them directly. The two queues and two processors share a
  type, so they are injected by name with `@Qualifier`.
- The failure rates are set in `application.yml` under `banksim.*` and bound to `SimulatorProperties`. A missing or
  out-of-range rate stops the app at startup.
- `ProcessorNoFailuresTest` and `ProcessorPositionFailuresTest` start the real service on a random port with fixed
  rates, send the sample messages over HTTP, and check the audit log and dead letter queue once every message has
  an outcome.

## Things that differ from the Python version

- No singletons. Spring builds one `AuditLog`, `DeadLetterQueue` and `TopicMessageProcessor` (see `PipelineConfig`)
  and passes them in through constructors.
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

## Microservices split

The goal is three separately deployable services, each owning its own data and talking over HTTP or a real queue:
`bank-service`, `processor-service` and `reconciliation-service`. It is built in slices, one commit each on the
`microservices-split` branch.

- Slice 0 (done): multi-module build; the processor runs as a web server. `Bank` and `SampleMessages` are parked in
  the processor until the bank service exists.
