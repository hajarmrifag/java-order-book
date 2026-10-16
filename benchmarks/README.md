# Benchmark protocol and environment

This is a baseline characterization, not an optimization or before/after speedup claim. No competitor implementation is benchmarked.

## Workloads

The book is seeded with one order at each of 16 or 256 bid levels and the same number of ask levels: 32 or 512 initial active orders. Prices are deliberately separated. All transient IDs are reused only after termination, as the documented ID policy allows. Input orders are constructed inside timed methods.

- **mixedBatch:** two resting sells at one new price; one buy partially consumes their combined volume; another buy completes the remainder; one new-price buy and cancellation. Five submissions plus one cancel, three trades total. Each timed invocation restores the initial book.
- **sweepBatch:** eight resting sells at eight new prices followed by one aggressive buy sweeping all eight. Nine submissions, eight trades. Each timed invocation restores the initial book.

These synthetic workloads exercise insertion, same-price FIFO, partial/full fills, price sweeps and cancellation with two background tree sizes. They are not an empirically calibrated market-message mix. They exclude rejection-heavy traffic, arbitrary order distributions, many instruments and sustained book growth. Same IDs and prices recur; JIT and cache behaviour can be unusually favourable. No snapshot occurs inside timing.

## Settings

JMH 1.37; 1 thread; 2 forks; 3 × 1 s warm-up and 5 × 1 s measurement for each workload/size/mode. Throughput unit is **batches per microsecond**. Sample-time unit is **microseconds per batch**. The summary converts throughput to batches/second and durations to the units labelled there. Every batch contains multiple commands. `-prof gc` records allocation/GC secondary metrics. Fork JVM arguments: `-Xms512m -Xmx512m -XX:+UseG1GC`.

Default annotations are used in the saved run; no warm-up or fork reduction. Repeating on a quiet machine and inspecting per-iteration stationarity is necessary before making a strong performance claim. Two forks and one-second windows do not establish stable production tail behaviour.

## Recorded environment

16 October 2026, Asia/Shanghai. Apple M5, 10 logical processors, 16 GiB memory; arm64 macOS 26.5 (build 25F71). Eclipse Temurin OpenJDK 21.0.12.1+1 LTS, 64-bit Server VM. Maven 3.9.9. No CPU affinity, frequency lock, isolated cores or verified power/thermal state. Background application activity was possible. Do not compare directly with x86 deployment hardware.

Source hashes in `SOURCE_SHA256.txt` identify the measured Java/core/benchmark sources. The JMH JSON retains iteration samples and run configuration.

## Interpretation limits

JMH's confidence interval and raw iteration scores help describe this run; sample percentiles are service durations of the benchmark method. They include harness sampling and process pauses but not external waiting time. Closed-loop measurements can hide queueing/coordinated-omission effects. Neither p99 nor maximum is a service-level objective. Do not divide a batch percentile into a per-order percentile. Throughput divided by command count is not appropriate either: command throughput would be batch throughput **multiplied** by the known count, but such an average hides the differing costs.

The project deliberately retains allocation and clear object structures. Use `gc.alloc.rate.norm` to investigate allocation per batch before proposing pooling or primitive structures. A comparison would need equivalent semantics, the same workloads and correctness tests, multiple runs and attention to tails and memory, not only a single faster mean.

Reference: [OpenJDK JMH source and samples](https://github.com/openjdk/jmh). Build and run commands are in the project README. The JSON is the measured evidence; `RESULTS.md` is a labelled summary of them.
