package dev.hajar.orderbook;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import static dev.hajar.orderbook.OrderBook.Side.*;

/** Bounded, steady-state synthetic batches. Each invocation restores its initial book. */
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=3,time=1)
@Measurement(iterations=5,time=1)
@Fork(value=2,jvmArgsAppend={"-Xms512m","-Xmx512m","-XX:+UseG1GC"})
@Threads(1)
public class OrderBookBenchmark {
    @Param({"16","256"}) public int levels;
    private OrderBook book;

    @Setup(Level.Trial) public void setup() {
        book=new OrderBook();
        for(int i=0;i<levels;i++) {
            book.submit(new OrderBook.Order(i+1,BUY,1000-i,100));
            book.submit(new OrderBook.Order(levels+i+1,SELL,1100+i,100));
        }
    }

    /** 5 submits + 1 cancel; partial fill, same-price FIFO, complete fills, new-level cancel. */
    @Benchmark public void mixedBatch(Blackhole blackhole) {
        blackhole.consume(book.submit(new OrderBook.Order(1_000_001,SELL,1050,10)));
        blackhole.consume(book.submit(new OrderBook.Order(1_000_002,SELL,1050,10)));
        blackhole.consume(book.submit(new OrderBook.Order(1_000_003,BUY,1050,15)));
        blackhole.consume(book.submit(new OrderBook.Order(1_000_004,BUY,1050,5)));
        blackhole.consume(book.submit(new OrderBook.Order(1_000_005,BUY,1001,10)));
        blackhole.consume(book.cancel(1_000_005));
    }

    /** An aggressive buy sweeps 8 prices; 8 maker inserts plus 1 taker submit. */
    @Benchmark public void sweepBatch(Blackhole blackhole) {
        for(int i=0;i<8;i++) blackhole.consume(book.submit(new OrderBook.Order(2_000_000+i,SELL,1050+i,10)));
        blackhole.consume(book.submit(new OrderBook.Order(2_000_100,BUY,1057,80)));
    }

    @TearDown(Level.Trial) public void verifyBoundedState() {
        book.checkInvariants();
        if(book.activeOrderCount()!=2*levels) throw new IllegalStateException("workload leaked orders");
    }
}
