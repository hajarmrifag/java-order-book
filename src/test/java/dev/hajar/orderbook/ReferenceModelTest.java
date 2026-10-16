package dev.hajar.orderbook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static dev.hajar.orderbook.OrderBook.Side.*;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceModelTest {
    /** Independent O(N) search over an arrival-ordered list; no price maps or linked nodes. */
    private static final class Reference {
        final List<OrderBook.Order> resting=new ArrayList<>();
        boolean cancel(long id) { return resting.removeIf(o->o.id()==id); }
        List<OrderBook.Trade> submit(OrderBook.Order incoming) {
            if(resting.stream().anyMatch(o->o.id()==incoming.id())) throw new IllegalArgumentException();
            var trades=new ArrayList<OrderBook.Trade>();
            long remaining=incoming.quantity();
            while(remaining>0) {
                int best=-1;
                for(int i=0;i<resting.size();i++) {
                    var candidate=resting.get(i);
                    if(candidate.side()==incoming.side()) continue;
                    boolean crosses=incoming.side()==BUY ? candidate.priceTicks()<=incoming.priceTicks()
                            : candidate.priceTicks()>=incoming.priceTicks();
                    if(!crosses) continue;
                    if(best<0 || (incoming.side()==BUY ? candidate.priceTicks()<resting.get(best).priceTicks()
                            : candidate.priceTicks()>resting.get(best).priceTicks())) best=i;
                }
                if(best<0) break;
                var maker=resting.get(best);
                long quantity=Math.min(remaining,maker.quantity());
                trades.add(new OrderBook.Trade(maker.id(),incoming.id(),maker.priceTicks(),quantity));
                remaining-=quantity;
                if(quantity==maker.quantity()) resting.remove(best);
                else resting.set(best,new OrderBook.Order(maker.id(),maker.side(),maker.priceTicks(),maker.quantity()-quantity));
            }
            if(remaining>0) resting.add(new OrderBook.Order(incoming.id(),incoming.side(),incoming.priceTicks(),remaining));
            return trades;
        }
        OrderBook.Snapshot snapshot() {
            // Stream sort is stable: original arrival order breaks equal-price ties.
            return new OrderBook.Snapshot(resting.stream().filter(o->o.side()==BUY)
                    .sorted(Comparator.comparingLong(OrderBook.Order::priceTicks).reversed()).toList(),
                    resting.stream().filter(o->o.side()==SELL)
                    .sorted(Comparator.comparingLong(OrderBook.Order::priceTicks)).toList());
        }
    }

    @Test void seededMixedCommandsMatchIndependentModelAndConserveQuantity() {
        for(int seed=0;seed<40;seed++) {
            var random=new Random(seed); var book=new OrderBook(); var reference=new Reference();
            for(int step=0;step<1500;step++) {
                String context="seed="+seed+" step="+step;
                long id=1+random.nextInt(160);
                if(random.nextInt(4)==0) assertEquals(reference.cancel(id),book.cancel(id),context);
                else {
                    var order=new OrderBook.Order(id,random.nextBoolean()?BUY:SELL,90+random.nextInt(21),1+random.nextInt(100));
                    boolean duplicate=reference.resting.stream().anyMatch(o->o.id()==id);
                    if(duplicate) {
                        var before=book.snapshot();
                        assertThrows(IllegalArgumentException.class,()->book.submit(order),context);
                        assertEquals(before,book.snapshot(),context);
                    } else {
                        long before=volume(book.snapshot());
                        var expected=reference.submit(order); var actual=book.submit(order);
                        assertEquals(expected,actual,context);
                        long filled=actual.stream().mapToLong(OrderBook.Trade::quantity).sum();
                        assertEquals(before+order.quantity()-2*filled,volume(book.snapshot()),context);
                    }
                }
                assertEquals(reference.snapshot(),book.snapshot(),context);
                assertEquals(reference.resting.size(),book.activeOrderCount(),context);
                book.checkInvariants();
            }
        }
    }

    private static long volume(OrderBook.Snapshot snapshot) {
        return snapshot.bids().stream().mapToLong(OrderBook.Order::quantity).sum()
                +snapshot.asks().stream().mapToLong(OrderBook.Order::quantity).sum();
    }
}
