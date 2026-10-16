package dev.hajar.orderbook;

import static dev.hajar.orderbook.OrderBook.Side.*;

/** Qualitative JFR allocation/GC workload, not a replacement for JMH timing. */
public final class ProfileWorkload {
    private ProfileWorkload() {}
    public static void main(String[] args) {
        var book = new OrderBook();
        long batches = 0;
        long checksum = 0;
        long end = System.nanoTime() + 15_000_000_000L;
        do {
            for (int i = 0; i < 1024; i++) {
                book.submit(new OrderBook.Order(1, SELL, 100, 10));
                book.submit(new OrderBook.Order(2, SELL, 100, 10));
                for (var trade : book.submit(new OrderBook.Order(3, BUY, 100, 15))) checksum += trade.quantity();
                for (var trade : book.submit(new OrderBook.Order(4, BUY, 100, 5))) checksum += trade.quantity();
                batches++;
            }
        } while (System.nanoTime() < end);
        book.checkInvariants();
        if (book.activeOrderCount() != 0 || checksum != batches * 20) throw new IllegalStateException("profile invariant");
        System.out.printf("Batches=%d checksum=%d active=%d%n", batches, checksum, book.activeOrderCount());
    }
}
