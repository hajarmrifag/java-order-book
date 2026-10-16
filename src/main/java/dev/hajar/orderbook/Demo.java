package dev.hajar.orderbook;

import static dev.hajar.orderbook.OrderBook.Side.*;

/** Deterministic replay example; deliberately separate from the matching core. */
public final class Demo {
    private Demo() {}
    public static void main(String[] args) {
        var book = new OrderBook();
        book.submit(new OrderBook.Order(1, SELL, 100, 5));
        book.submit(new OrderBook.Order(2, SELL, 100, 4));
        for (var trade : book.submit(new OrderBook.Order(3, BUY, 101, 7))) {
            System.out.printf("TRADE maker=%d taker=%d price=%d quantity=%d%n",
                    trade.makerId(), trade.takerId(), trade.priceTicks(), trade.quantity());
        }
        System.out.println("CANCEL 2 " + book.cancel(2));
        System.out.println("ACTIVE " + book.activeOrderCount());
    }
}
