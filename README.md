# bank-simulator (Java)

A Java port of a small Python simulation of a bank message pipeline. Three banks send loan events as JSON. A topic
validates each message and routes it by event type to a queue. One processor per queue retries failures and moves
messages that keep failing to a dead letter queue. Every outcome goes into an audit log, and a reconciliation pass
looks for problems at the end.

## Build, test, run

Needs Java 25 and Maven. Built on Spring Boot 4.1.

The project is being split into separately deployable services (see "Microservices split" below). So far there are
two modules: `processor-service`, a web server on port 8081, and `bank-service`, which sends the sample messages to
it and exits.

```sh
mvn test                                         # run every module's tests
mvn -q -pl processor-service spring-boot:run     # start the processor; Ctrl+C stops it
mvn -q -pl bank-service spring-boot:run          # send every bank's messages; exit code 1 if any were given up on

curl -i -H 'Content-Type: application/json' \
  -d '{"message_id":"m-1","bank_id":"wf_1334566","loan_id":"loan_002","event_type":"POSITION_UPDATE"}' \
  localhost:8081/messages                        # 202 Accepted; the same message_id again gets 200
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
  does not exist yet when the reply goes back. Rejected messages also get 202 for now.
- Every message must carry a `message_id`, created by the bank. A message whose id was already accepted is audited
  as `DUPLICATE`, not processed again, and gets 200.
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

- Slice 0 (done): multi-module build; the processor runs as a web server.
- Slice 1 (done): `bank-service` POSTs each message with `RestClient`, one thread per bank.
  - If the processor cannot be reached, `Bank.send` retries up to 5 attempts, waiting 1, 2, 4 and 8 seconds, so it
    rides out about 15 seconds of the processor being down. A reply with an error status is not retried.
  - `send` never throws, so one failed message no longer ends the rest of that bank's sends.
  - Messages given up on are counted, and the service exits with code 1 if there were any.
  - Known limitations: each message rediscovers an outage on its own, so a bank with 4 messages spends about a
    minute giving up. A message given up on is lost, and nothing downstream knows it was ever sent.
- Slice 2 (done): retries no longer process a message twice.
  - The bank has a 2 second read timeout. With no reply, the processor may already have the message, so a retry
    can deliver it twice (at-least-once delivery).
  - Each message gets a `message_id` from the bank once, before the first attempt; every retry reuses it. The
    processor remembers accepted ids (`SeenMessageIds`) and ignores a resend, so handling is idempotent.
  - An id is not bank + loan + event type: `BOA_PAYMENT_DUPLICATE` is a genuine second payment with the same three
    fields, and must not be dropped.
  - `banksim.chaos.drop-first-reply-for-loan=loan_002` processes that loan's first message and holds the reply
    past the bank's timeout, to show the resend on demand.
  - Known limitations: the seen ids are in memory and never expire. A restarted processor, or a second copy of it,
    would process a resend again, and the set grows forever. At scale they belong in a shared store (a unique
    constraint in a database, or a key-value store with expiry), kept only as long as a resend can arrive: about
    25 seconds for this bank's retry schedule. A 202
    means the message is in an in-memory queue, not stored: a processor crash loses it and the bank will not resend.
- The services share no code. `Banks` exists in both; the processor's copy validates bank ids. `SampleMessages` is
  the bank service's data and a test fixture in the processor.
