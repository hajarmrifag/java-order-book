package dev.hajar.orderbook;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static dev.hajar.orderbook.OrderBook.Side.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {
    private final OrderBook book = new OrderBook();
    private static OrderBook.Order order(long id, OrderBook.Side side, long price, long quantity) {
        return new OrderBook.Order(id, side, price, quantity);
    }

    @Test void emptyBookAndUnknownCancellation() {
        assertEquals(new OrderBook.Snapshot(List.of(), List.of()), book.snapshot());
        assertFalse(book.cancel(42));
        assertFalse(book.cancel(-1));
        book.checkInvariants();
    }

    @Test void pricePriorityThenTimeAndMakerPrice() {
        book.submit(order(1, SELL, 102, 3));
        book.submit(order(2, SELL, 100, 2));
        book.submit(order(3, SELL, 100, 4));
        assertEquals(List.of(new OrderBook.Trade(2,4,100,2), new OrderBook.Trade(3,4,100,4),
                new OrderBook.Trade(1,4,102,1)), book.submit(order(4,BUY,103,7)));
        assertEquals(List.of(order(1,SELL,102,2)),book.snapshot().asks());
        book.checkInvariants();
    }

    @Test void sellTakerConsumesHighestBidFirst() {
        book.submit(order(1,BUY,99,2));
        book.submit(order(2,BUY,101,3));
        assertEquals(List.of(new OrderBook.Trade(2,3,101,3), new OrderBook.Trade(1,3,99,1)),
                book.submit(order(3,SELL,99,4)));
        assertEquals(List.of(order(1,BUY,99,1)),book.snapshot().bids());
        book.checkInvariants();
    }

    @Test void exactFillRemovesBothOrdersAndPriceLevel() {
        book.submit(order(1,SELL,100,5));
        assertEquals(List.of(new OrderBook.Trade(1,2,100,5)),book.submit(order(2,BUY,100,5)));
        assertEquals(0,book.activeOrderCount());
        assertFalse(book.cancel(1));
        book.checkInvariants();
    }

    @Test void residualIncomingRestsAtItsLimit() {
        book.submit(order(1,SELL,100,2));
        book.submit(order(2,BUY,105,7));
        assertEquals(List.of(order(2,BUY,105,5)),book.snapshot().bids());
        book.checkInvariants();
    }

    @Test void nonCrossingOrdersRemainAndSnapshotIsImmutable() {
        book.submit(order(1,BUY,99,2));
        assertTrue(book.submit(order(2,SELL,100,3)).isEmpty());
        var snapshot=book.snapshot();
        assertThrows(UnsupportedOperationException.class,()->snapshot.bids().clear());
        book.cancel(1);
        assertEquals(1,snapshot.bids().size());
        book.checkInvariants();
    }

    @Test void duplicateIdIsRejectedBeforeAnyMutationEvenAcrossSides() {
        book.submit(order(1,BUY,100,7));
        var before=book.snapshot();
        assertThrows(IllegalArgumentException.class,()->book.submit(order(1,SELL,99,8)));
        assertEquals(before,book.snapshot());
    }

    @Test void cancelHeadMiddleTailAndLastOrder() {
        for(int i=1;i<=5;i++) book.submit(order(i,BUY,100,2));
        assertTrue(book.cancel(3)); // middle
        assertTrue(book.cancel(1)); // head
        assertTrue(book.cancel(5)); // tail
        assertEquals(List.of(order(2,BUY,100,2),order(4,BUY,100,2)),book.snapshot().bids());
        book.cancel(2); book.cancel(4);
        assertEquals(0,book.activeOrderCount());
        book.checkInvariants();
    }

    @Test void partiallyFilledOrderRetainsPriorityAndCanBeCancelled() {
        book.submit(order(1,SELL,100,5));
        book.submit(order(2,SELL,100,5));
        book.submit(order(3,BUY,100,2));
        assertEquals(List.of(new OrderBook.Trade(1,4,100,2)),book.submit(order(4,BUY,100,2)));
        assertTrue(book.cancel(1));
        assertEquals(List.of(order(2,SELL,100,5)),book.snapshot().asks());
        book.checkInvariants();
    }

    @Test void terminalIdsMayBeReusedWithNewTimePriority() {
        book.submit(order(1,BUY,100,1)); book.submit(order(2,BUY,100,1));
        book.cancel(1); book.submit(order(1,BUY,100,1));
        assertEquals(List.of(new OrderBook.Trade(2,3,100,1),new OrderBook.Trade(1,3,100,1)),
                book.submit(order(3,SELL,100,2)));
        assertTrue(book.submit(order(3,SELL,101,1)).isEmpty());
        book.checkInvariants();
    }

    @ParameterizedTest @CsvSource({"0,1,1","-1,1,1","1,0,1","1,-1,1","1,1,0","1,1,-1"})
    void rejectsInvalidOrders(long id,long price,long quantity) {
        assertThrows(IllegalArgumentException.class,()->order(id,BUY,price,quantity));
    }

    @Test void rejectsNulls() {
        assertThrows(NullPointerException.class,()->order(1,null,1,1));
        assertThrows(NullPointerException.class,()->book.submit(null));
    }

    @Test void longBoundsDoNotOverflowComparisonsOrQuantities() {
        book.submit(order(Long.MAX_VALUE,SELL,Long.MAX_VALUE,Long.MAX_VALUE));
        assertTrue(book.submit(order(2,BUY,1,1)).isEmpty());
        assertEquals(List.of(new OrderBook.Trade(Long.MAX_VALUE,3,Long.MAX_VALUE,Long.MAX_VALUE)),
                book.submit(order(3,BUY,Long.MAX_VALUE,Long.MAX_VALUE)));
        book.checkInvariants();
    }
}
