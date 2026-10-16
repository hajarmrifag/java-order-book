package dev.hajar.orderbook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** One instrument, one owning thread. Not thread-safe; no I/O or wall clock in matching. */
public final class OrderBook {
    public enum Side { BUY, SELL }

    public record Order(long id, Side side, long priceTicks, long quantity) {
        public Order {
            Objects.requireNonNull(side, "side");
            if (id <= 0 || priceTicks <= 0 || quantity <= 0) {
                throw new IllegalArgumentException("id, priceTicks and quantity must be positive");
            }
        }
    }

    /** Execution price is the resting maker's price, not the incoming limit. */
    public record Trade(long makerId, long takerId, long priceTicks, long quantity) {}
    public record Snapshot(List<Order> bids, List<Order> asks) {
        public Snapshot { bids = List.copyOf(bids); asks = List.copyOf(asks); }
    }

    private static final class Level {
        Node head;
        Node tail;
    }

    private static final class Node {
        final Order order;
        final Level level;
        long remaining;
        Node previous;
        Node next;
        Node(Order order, long remaining, Level level) {
            this.order = order;
            this.remaining = remaining;
            this.level = level;
        }
    }

    private final NavigableMap<Long, Level> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, Level> asks = new TreeMap<>();
    private final HashMap<Long, Node> active = new HashMap<>();

    /** IDs must be unique among active orders; terminal IDs may be reused. */
    public List<Trade> submit(Order incoming) {
        Objects.requireNonNull(incoming, "incoming");
        if (active.containsKey(incoming.id())) {
            throw new IllegalArgumentException("duplicate active id: " + incoming.id());
        }
        long remaining = incoming.quantity();
        var trades = new ArrayList<Trade>();
        var opposite = book(incoming.side() == Side.BUY ? Side.SELL : Side.BUY);
        while (remaining > 0 && !opposite.isEmpty()) {
            var best = opposite.firstEntry();
            boolean crosses = incoming.side() == Side.BUY
                    ? incoming.priceTicks() >= best.getKey()
                    : incoming.priceTicks() <= best.getKey();
            if (!crosses) break;
            Node maker = best.getValue().head;
            long filled = Math.min(remaining, maker.remaining);
            trades.add(new Trade(maker.order.id(), incoming.id(), maker.order.priceTicks(), filled));
            remaining -= filled;
            maker.remaining -= filled;
            if (maker.remaining == 0) unlink(maker);
        }
        if (remaining > 0) {
            Level level = book(incoming.side()).computeIfAbsent(incoming.priceTicks(), ignored -> new Level());
            Node node = new Node(incoming, remaining, level);
            node.previous = level.tail;
            if (level.tail == null) level.head = node;
            else level.tail.next = node;
            level.tail = node;
            active.put(incoming.id(), node);
        }
        return List.copyOf(trades);
    }

    /** Unknown/non-positive IDs are harmless misses. */
    public boolean cancel(long id) {
        Node node = active.get(id);
        if (node == null) return false;
        unlink(node);
        return true;
    }

    public int activeOrderCount() { return active.size(); }

    /** Best price first, then arrival order within each price; immutable remaining quantities. */
    public Snapshot snapshot() { return new Snapshot(orders(bids), orders(asks)); }

    private NavigableMap<Long, Level> book(Side side) { return side == Side.BUY ? bids : asks; }

    private void unlink(Node node) {
        Level level = node.level;
        if (node.previous == null) level.head = node.next;
        else node.previous.next = node.next;
        if (node.next == null) level.tail = node.previous;
        else node.next.previous = node.previous;
        active.remove(node.order.id());
        if (level.head == null) book(node.order.side()).remove(node.order.priceTicks());
        node.previous = null;
        node.next = null;
    }

    private static List<Order> orders(NavigableMap<Long, Level> side) {
        var result = new ArrayList<Order>();
        for (Level level : side.values()) {
            for (Node node = level.head; node != null; node = node.next) {
                Order o = node.order;
                result.add(new Order(o.id(), o.side(), o.priceTicks(), node.remaining));
            }
        }
        return result;
    }

    /** Expensive structural checks used by tests, never by the benchmark hot path. */
    void checkInvariants() {
        int count = 0;
        var seen = new java.util.HashSet<Long>();
        for (Side side : Side.values()) {
            for (var entry : book(side).entrySet()) {
                Level level = entry.getValue();
                if (level.head == null || level.tail == null) throw new IllegalStateException("empty level");
                Node previous = null;
                for (Node node = level.head; node != null; node = node.next) {
                    if (!seen.add(node.order.id()) || node.previous != previous || node.level != level
                            || node.order.side() != side || node.order.priceTicks() != entry.getKey()
                            || node.remaining <= 0 || active.get(node.order.id()) != node) {
                        throw new IllegalStateException("index, FIFO or quantity invariant");
                    }
                    previous = node;
                    count++;
                }
                if (previous != level.tail) throw new IllegalStateException("tail invariant");
            }
        }
        if (count != active.size()) throw new IllegalStateException("index size");
        if (!bids.isEmpty() && !asks.isEmpty() && bids.firstKey() >= asks.firstKey()) {
            throw new IllegalStateException("crossed resting book");
        }
    }
}
