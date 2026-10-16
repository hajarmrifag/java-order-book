# Matching rules, data structures and verification

[Back to the project](../README.md)

Commands below run from the repository root.

## Semantics

- Submit positive signed-long IDs, integer price ticks and integer quantities. No floating-point money, unit conversion, arithmetic on total notional, market orders, replacement or persistence.
- Buy matches the lowest ask at or below its limit; sell matches the highest bid at or above its limit. Equal-price resting orders match FIFO by arrival to the owning thread. Caller timestamps have no priority role.
- Each trade executes at the resting maker's price. A partial maker keeps its FIFO position. An incoming remainder rests at its own limit. Returned trades and snapshots are immutable values.
- Duplicate **active** IDs are rejected before mutation, including duplicates on the other side. Filled/cancelled IDs can be reused and receive new arrival priority. This is deliberately not persistent request idempotency; network retries would need a separate command identity/outcome policy.
- Cancel of an unknown or nonpositive ID returns false. Invalid orders throw before mutation. Resource exhaustion is not transactionally recoverable; there is no durable crash recovery.

## Design and costs

`OrderBook.java` owns all state. `Demo.java` provides I/O and deterministic replay. There are no external runtime dependencies in the matching core.

Two `TreeMap<Long, Level>` instances hold prices, descending for bids and ascending for asks. A level owns a doubly linked FIFO of orders. A `HashMap<Long, Node>` indexes active IDs so cancellation can unlink an arbitrary order without scanning the queue. This is a deliberate small use of linked nodes; an `ArrayDeque.remove(order)` would scan a level.

Let P be the number of active price levels, N the number of active orders and F the number of resting orders touched by a submission:

| Operation | Cost under normal hash behaviour |
|---|---|
| Non-crossing insertion | O(log(P + 1)) tree lookup/creation + expected O(1) ID insertion and FIFO append |
| Matching submission | O((F + 1) log(P + 1)) conservative bound; this implementation calls tree `firstEntry()` for each match and deletes emptied levels |
| Cancellation | Expected O(1) lookup/unlink; O(log(P + 1)) if the last order removes a price level |
| Snapshot | O(N + P) traversal and O(N) output space |
| Stored state | O(N + P); no retained history of terminal IDs |

Hash-map average behaviour is an assumption, not a worst-case constant-time promise. Integer boxing, object nodes, records and immutable result copies allocate. The goal is clarity and correctness; measure allocation before replacing them with primitive collections or pools. No object-layout or cache-locality superiority is claimed.

## Invariants and tests

After every command: positive resting quantities; each order in exactly one correct-side/price FIFO; a consistent bidirectional chain; active index and nodes agree; no empty levels; no crossed resting book. Snapshot order is best price then FIFO. Test-only structural checks also detect duplicate nodes/cycles by ID.

`OrderBookTest` covers empty/non-crossed books, both taker sides, exact and partial fills, residual orders, maker pricing, FIFO, head/middle/tail/last cancellation, duplicate IDs, terminal-ID reuse, invalid/null inputs, long boundaries and immutable snapshots. `DemoIntegrationTest` checks a complete replay and emitted text.

`ReferenceModelTest` uses a deliberately different implementation: an arrival-ordered list with linear best-price search, no price trees or linked nodes. Forty fixed seeds × 1,500 mixed commands compare trades, remaining book, active count and rejection behaviour after each command; accepted submits additionally check quantity conservation. This is 60,000 generated commands, not 60,000 JUnit test cases. The finite seed set and shared agreed semantics are limits; a matching reference model does not prove all inputs correct.

Local verification: **20 tests passed, 0 failed/errors/skipped**. GitHub Actions provides a separate reproducibility check. The packaged demo was also executed. The real network/database/durability paths do not exist and are not tested.

## Concurrency decision

One thread owns the engine; callers must serialize all access, including queries. There is no claim of thread safety. Matching, ID indexing and level updates form one compound invariant; swapping in `ConcurrentHashMap` would not protect it. A future adapter could use a single owning event loop and bounded input queue, but must define rejection/backpressure, producer ordering, shutdown and result delivery. Another initial design is one lock around the whole operation. Neither is implemented simply to claim concurrency.

## Benchmarks

```sh
mvn --batch-mode -Pbenchmarks -DskipTests package
java -jar target/benchmarks.jar -l
java -jar target/benchmarks.jar -prof gc -rf json -rff benchmarks/results.json
```

JMH 1.37 provides warm-up, separate JVM forks and sampled duration/throughput modes. The benchmark source fixes three one-second warm-up iterations, five one-second measurement iterations, two forks, one thread, a 512 MiB heap and G1 GC. Four workload/size combinations run in each of two modes. `Blackhole` consumes results; trial teardown checks the book returned to its initial bounded state. Timing includes input/result allocations. See [method, environment and limitations](../benchmarks/README.md) and [measured results](../benchmarks/RESULTS.md).

Do not advertise “nanosecond exchange latency.” These are repeated in-process **batch** measurements without networking, external arrivals, queueing, durability or contention. A sampled batch p99 cannot be divided by six or nine to obtain a per-order p99.

## Scope

The engine has no network, persistence, external arrival process or concurrent writer. Tests and measurements demonstrate implementation behaviour within these boundaries, not operational suitability for live trading.
