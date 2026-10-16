# JVM allocation and GC investigation

The object-based engine deliberately allocates input records, linked nodes and immutable trade results. A 15-second Java Flight Recorder run on 16 October 2026 investigated those costs before considering pooling or primitive collections. It uses `ProfileWorkload`, a four-submission loop that completely fills two resting orders and checks final state and quantity conservation. This workload is separate from the JMH batches.

## Reproduce

Use the environment in [the benchmark protocol](../benchmarks/README.md): Java 21, a 512 MiB heap and G1.

```sh
mvn --batch-mode -Pbenchmarks -DskipTests package
java -Xms512m -Xmx512m -XX:+UseG1GC \
  -XX:StartFlightRecording=filename=orderbook.jfr,settings=profile,dumponexit=true \
  -cp target/benchmarks.jar dev.hajar.orderbook.ProfileWorkload
jfr summary orderbook.jfr
jfr print --json --events jdk.ObjectAllocationSample,jdk.GarbageCollection,jdk.ExecutionSample \
  orderbook.jfr > orderbook-events.json
```

JFR files can include process/environment details; inspect them before sharing. The repository contains only the [derived measurement summary](../benchmarks/jfr-summary.json), including the workload source hash. The earlier JMH source manifest does not cover this later profiling helper.

## Observations

The run completed 128,687,104 batches with checksum 2,573,742,080 and zero remaining orders. The recording contains 4,388 allocation samples, 1,115 execution samples and 304 `G1New` collections. Summing each GC event's `sumOfPauses` yields 181.936 ms; the median per-collection pause sum is 0.535 ms and the maximum is 3.029 ms. These are observations for this run, not latency targets.

For allocation samples, group events by `objectClass.name` and sum `weight`; divide by the total sample weight for the percentages below. These weights estimate allocation, rather than counting every object or reporting retained heap.

| Allocated type | Share of sampled allocation weight |
|---|---:|
| `OrderBook.Trade` | 27.67% |
| `OrderBook.Order` | 25.48% |
| `Object[]` | 12.34% |
| `HashMap.Node` | 9.42% |
| `ImmutableCollections.List12` | 9.28% |
| `OrderBook.Node` | 8.89% |
| `TreeMap.Entry` | 6.87% |

The result supports examining the input/output contract and result-list construction before focusing only on the price tree. It does not establish that any proposed replacement improves performance. Pooling mutable trade results would conflict with the current immutable result contract unless ownership and lifetime were redesigned. Reducing incidental arrays may be a smaller experiment, but it still requires equivalent correctness and fresh JMH measurements.

## Limits

Recording begins at JVM startup and includes compilation/startup activity. Sampling is incomplete; escape analysis and workload shape affect which allocations remain. The book repeatedly returns to empty, prices and IDs recur, and there is no queue, network, contention or durable log. The profile count divided by elapsed time is not a benchmark result. Use the separately recorded JMH run for the labelled batch measurements. No before/after optimization or production GC guarantee is asserted.
