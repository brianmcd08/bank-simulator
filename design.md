Classes. Which Python classes become which Java types? (Where does a Java enum or record fit?)
dataclasses are likely records. Singletons -> Enums, Also Enum->Enum


What runs at the same time. In phase 1, what runs on its own thread: each queue processor? each bank sending? something else?
I'm not sure.


Shared state. Which objects will more than one thread touch? (The picture's red boxes are a start.) What goes wrong if two threads write to them at once?
AuditLog, DLQ. I'm not sure.

Finishing and shutting down. How does main know every message has been processed, and how does the program stop cleanly? This was item 4 of Seguno's review.
should use an executor with a pool, right?

The singletons. Python uses __new__ for AuditLog, the DLQ and TopicMessageProcessor. What do you use in Java, and is a singleton still what you want?
Shouldn't we use an Enum for a Singleton in Java? I'm not sure if a singleton is right or not.

The failure-rate bug. Copy it faithfully, or fix it? 
Fix it. I've given some answers already for this.


Plain Java or a bare Spring Boot shell. Plain Java puts nothing between you and the threads, so there's less to learn at once. Spring Boot is where the later phases (persistence, microservices) live, so starting plain means converting later.
Let's do plain first and get it committed to GitHub. Then convert to Spring Boot and utilize microservices.


----------------------------------------------------------------------
Settled (2026-09-23, after review)
----------------------------------------------------------------------

1. Classes.
   - Enum -> enum: EventType, Outcome, Banks (Banks carries fullName and bankId as fields).
   - Message is an immutable record: bankId, loanId, eventType, messageId. It has NO outcome
     field. Outcome is a result of processing and lives only in the audit log. This removes a
     piece of shared mutable state.
   - AuditEntry is a record.
   - The three Bank subclasses collapse to one Bank class that takes a Banks constant. The
     original intent was to signal that banks will likely diverge in production. Per-bank
     classes come back when behavior actually differs; a README note records this.
   - SQSQueue wraps a LinkedBlockingQueue<Message>. take() removes, so the Python
     marked_for_removal logic disappears.
   - Everything else (SNSTopic, DeadLetterQueue, TopicMessageProcessor, QueueMessageProcessor,
     AuditLog, ReconciliationEngine) is a plain class.

2. What runs at the same time.
   Full pipeline. Each bank sends on its own thread. Each QueueMessageProcessor consumes on
   its own thread, at the same time as the senders. TopicMessageProcessor.process is called
   from three bank threads at once and must hold no per-call state. Reconciliation runs on
   main after everything else has finished.

3. Shared state.
   - The two SQS queues (banks push, processor takes). This is the main one and the diagram
     did not mark it red.
   - AuditLog: written by bank threads (invalid bank id) and both processor threads.
   - DeadLetterQueue: written by both processor threads.
   - SNSTopic's routing map: read by three threads, safe only because it is fully built
     before any thread starts.
   What goes wrong: two threads appending to an ArrayList can lose a write or throw
   ArrayIndexOutOfBoundsException when size and the backing array disagree.
   Fixes: BlockingQueue for the queues; synchronized write plus copy-on-read for AuditLog
   and the DLQ; ThreadLocalRandom instead of a shared Random.

4. Finishing and shutting down.
   An executor answers "who runs the threads," not "how does a consumer blocked on take()
   learn nothing more is coming." Answer: poison pill.
   - Bank senders run in an ExecutorService. Main calls shutdown() then awaitTermination().
   - Main then puts one sentinel message on each queue.
   - Each QueueMessageProcessor exits when it takes the sentinel.
   - Main joins the processor threads, then runs reconciliation.
   The two processors are long-lived, one per queue, so a plain Thread each is fine.

5. The singletons.
   None. The Python singleton was standing in for dependency injection. Main builds one
   AuditLog, one DeadLetterQueue, one TopicMessageProcessor and passes them in by
   constructor. This is what Spring's container does in phase 2, so the conversion is nearly
   mechanical. An enum singleton is global state that cannot be reset between tests.
   Note: the Python TopicMessageProcessor silently ignores its topic argument after the
   first call.

6. The failure-rate bug.
   Fix it. Compare nextDouble() against a rate in 0..1. Make the failure decision injectable
   (a small functional interface) so tests can force the retry and DLQ paths. With a real
   40% rate and 3 attempts, about 6% of POSITION messages will reach the DLQ, so output is
   nondeterministic by design. Drop the bare except around the DLQ push; it cannot fail.

7. Plain Java or Spring Boot.
   Plain Java first, Maven, Java 25, constructor injection throughout. Spring Boot in
   phase 2. Microservices are a phase 3 conversation, not something Spring Boot itself needs.

Other calls made during review
   - Unknown event_type in the JSON is audited as PERMANENT_FAILURE, same as an invalid bank
     id. (The Python silently drops it with no audit entry.)
   - Stuck-in-PENDING reconciliation is kept faithful even though PENDING never reaches the
     audit log in phase 1, so it cannot fire.
   - JSON parsing uses Jackson, the one dependency.

Diff 1 (this commit): pom.xml, .gitignore, EventType, Outcome, Banks, Message, and tests.
No concurrency yet. The queue processors are next and are written by hand.
