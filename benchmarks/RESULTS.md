# Measured JMH results

Recorded 16 October 2026. All eight combinations completed successfully. These are **synthetic batch** measurements, not per-order or exchange/network latency. The order book is single-threaded; benchmark worker count is one. Read [method and environment](README.md) before interpreting them.

| Workload | Levels per side | Mean throughput (million batches/s) | JMH 99.9% confidence interval (million batches/s) | Sample p50 (µs/batch) | Sample p99 (µs/batch) | Sample p99.9 (µs/batch) | Allocated bytes/batch (throughput run) |
|---|---:|---:|---:|---:|---:|---:|---:|
| mixedBatch | 16 | 5.716 | 4.313–7.118 | 0.166 | 1.042 | 16.529 | 1304 |
| mixedBatch | 256 | 5.410 | 4.193–6.627 | 0.167 | 1.040 | 16.615 | 1304 |
| sweepBatch | 16 | 1.405 | 0.666–2.145 | 0.459 | 6.912 | 32.626 | 2992 |
| sweepBatch | 256 | 1.532 | 1.333–1.730 | 0.542 | 5.912 | 26.496 | 2992 |

The mixed batch contains five submits plus one cancel and produces three trades. The sweep batch contains eight maker submits plus one sweeping taker and produces eight trades. Throughput and sample-time values came from **separate forks/modes**; they are not simultaneous observations or reciprocals. No percentile is divided by command count.

The higher-level-count sweep has a higher throughput point estimate here. This is not evidence that larger books are inherently faster: confidence intervals, repeated sessions, scheduler/power state and workload-specific optimizations matter. Warm-up and measurement windows are short; inspect per-iteration variation in the raw log before drawing conclusions.

Approximately 1.3 KB and 3.0 KB are allocated per mixed/sweep batch respectively in this implementation. This is a useful JVM discussion point, not an argument to introduce object pools without measurement. No comparative optimization was performed.

## Reproduce and inspect

[Raw JMH JSON](results.json) contains run settings, per-iteration samples and percentile data. [Source hashes](SOURCE_SHA256.txt) identify the measured sources. Run the commands in the project README. No GPU/database/network/queueing/durability work is included. Later source changes need new measurements, not relabelled historical output.
