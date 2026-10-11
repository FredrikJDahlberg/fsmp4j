package org.limitless.fsmp4j.examples;

import org.limitless.fsmp4j.BlockPool;

import java.lang.foreign.Arena;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps live orders off-heap in a {@link BlockPool} and indexes them by id using the block addresses.
 * <p>
 * Run with {@code ./gradlew :fsmp4j-examples:run}.
 */
public class OrderBookExample {

    public static void main(String[] args) {
        try (Arena arena = Arena.ofConfined()) {
            BlockPool<Order> pool = BlockPool.builder(arena, Order::new)
                .blocksPerSegment(1024)
                .build();

            // order id -> block address; the address is a compact handle to the block
            Map<Long, Long> orders = new HashMap<>();

            // one flyweight reused for every access, so the loop below creates no garbage per order
            Order cursor = new Order();

            String[] symbols = { "AAPL", "MSFT", "NVDA" };
            for (long id = 1; id <= 6; ++id) {
                pool.allocate(cursor)
                    .id(id)
                    .symbol(symbols[(int) (id % symbols.length)])
                    .buy(id % 2 == 0)
                    .price(100.0 + id)
                    .quantity((int) id * 10);
                orders.put(id, cursor.address());
            }
            System.out.println("Added:     " + orders.size() + " orders, " + pool);
            print(pool, orders, cursor);

            // partial fill: update the order in place
            pool.get(orders.get(2L), cursor);
            cursor.quantity(cursor.quantity() - 5);
            System.out.println("Filled:    " + cursor);

            // cancel: return the block to the pool
            long cancelled = orders.remove(3L);
            pool.free(cancelled);
            System.out.println("Cancelled: order 3, blocks in use = " + pool.blocksInUse());

            // the freed block is handed out again by the next allocation
            pool.allocate(cursor).id(7).symbol("TSLA").buy(true).price(250.0).quantity(15);
            orders.put(7L, cursor.address());
            System.out.println("Added:     " + cursor + ", reused block = " + (cursor.address() == cancelled));

            print(pool, orders, cursor);
        }
    }

    private static void print(BlockPool<Order> pool, Map<Long, Long> orders, Order cursor) {
        orders.values().forEach(address -> System.out.println("    " + pool.get(address, cursor)));
    }
}
