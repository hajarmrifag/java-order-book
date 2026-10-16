# Design decisions

One writer owns a single instrument. Price levels use ordered trees; intrusive FIFO nodes plus an active-ID hash index support cancellation without scanning a queue. Trades execute at the resting price. Integer ticks avoid floating-point comparison; positive long quantities are subtracted/minimized without notional multiplication or overflowing aggregate volume.

The ID contract is uniqueness among active orders, not durable request idempotency. Cancelled/filled IDs can be reused with new time priority. Incoming duplicate IDs are rejected before any match. Snapshots and returned trade lists are immutable copies. Out-of-memory/process failure does not have rollback or recovery semantics.

The reference oracle uses a different representation (arrival list and linear search). Each of 60,000 generated commands compares trades and full remaining state; quantity conservation and structural invariants provide additional checks. A finite seed set cannot prove every input or establish a concurrency guarantee.

The object-based design intentionally prioritizes explainability over minimum allocation. JMH separates warm-up/forks and measures bounded batches; JFR provides a qualitative allocation/GC check. Primitive collections, caching a best-level pointer or intrusive node pools require evidence of a bottleneck before adding complexity. A ConcurrentHashMap alone would not make matching atomic; future concurrency belongs at a documented ownership boundary, not inside individual maps.
