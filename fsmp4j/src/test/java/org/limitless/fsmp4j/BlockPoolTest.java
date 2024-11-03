package org.limitless.fsmp4j;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BlockPoolTest {

    @Test
    public void allocateRemoveOneSegment() {
        final var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class)
            .blocksPerSegment(16).build();
        final var block1 = pool.allocate().int64(101_0000).int32(200).int16((short) 1);
        assertEquals(0, block1.segment());
        assertEquals(0, block1.block());

        final var block2 = pool.allocate().int64(102_0000).int32(200).int16((short) 1);
        assertEquals(0, block2.segment());
        assertEquals(1, block2.block());

        final var block3 = pool.allocate().int64(103_0000).int32(300).int16((short) 2);
        assertEquals(0, block3.segment());
        assertEquals(2, block3.block());

        assertDoesNotThrow(() -> pool.free(block1));
        assertDoesNotThrow(() -> pool.free(block3));

        final var block7 = pool.allocate().int64(107_0000).int32(300).int16((short) 2);
        assertEquals(0, block7.segment());
        assertEquals(2, block7.block());

        final var block8 = pool.allocate().int64(108_0000).int32(300).int16((short) 2);
        assertEquals(0, block8.segment());
        assertEquals(0, block8.block());

        pool.close();
    }

    @Test
    public void allocateRemoveMultiblockeSegments() {
        final var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class).blocksPerSegment(64).build();
        final var block1 = pool.allocate().int64(101_0000).int32(200).int16((short) 1);
        assertEquals(0, block1.segment());
        assertEquals(0, block1.block());

        final var block2 = pool.allocate().int64(102_0000).int32(200).int16((short) 1);
        assertEquals(0, block2.segment());
        assertEquals(1, block2.block());

        final var block3 = pool.allocate().int64(103_0000).int32(300).int16((short) 2);
        assertEquals(0, block3.segment());
        assertEquals(2, block3.block());

        final var block4 = pool.allocate().int64(104_0000).int32(300).int16((short) 2);
        assertEquals(0, block4.segment());
        assertEquals(3, block4.block());

        for (int position = 0; position < 60; ++position) {
            pool.allocate();
        }

        final var block5 = pool.allocate().int64(105_0000).int32(300).int16((short) 2);
        assertEquals(1, block5.segment());
        assertEquals(0, block5.block());

        assertDoesNotThrow(() -> pool.free(block5));
        assertDoesNotThrow(() -> pool.free(block4));

        final var block7 = pool.allocate().int64(107_0000).int32(300).int16((short) 2);
        assertEquals(0, block7.segment());
        assertEquals(3, block7.block());

        final var block8 = pool.allocate().int64(108_0000).int32(300).int16((short) 2);
        assertEquals(1, block8.segment());
        assertEquals(0, block8.block());

        pool.close();
    }

    @Test
    public void addressMapping() {
        final var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class)
            .blocksPerSegment(16).build();
        final var block1 = pool.allocate().int64(101_0000).int32(200).int16((short) 1);
        assertNotNull(block1);

        final var address1 = block1.address();
        assertEquals(1L << 32, address1);
        assertEquals(0, block1.segment());
        assertEquals(0, block1.block());

        final var block2 = pool.get(address1);
        assertEquals(block1, block2);

        final var block3 = new TestFlyweight();
        assertNull(block3.memorySegment());
        assertEquals(-1, block3.address());
        pool.get(address1, block3);
        assertNotNull(block3.memorySegment());
        assertEquals(block1, block3);

        final var block4 = new TestFlyweight();
        assertNull(block4.memorySegment());
        pool.allocate(block4);
        assertEquals(0, block4.segment());
        assertEquals(2, block4.block());

        assertThrows(IllegalArgumentException.class, () -> pool.get(0, null));

        pool.close();
    }

    @Test
    public void invalidAllocate() {
        final var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class)
            .blocksPerSegment(16).build();
        assertThrows(IllegalArgumentException.class, () -> pool.allocate(null));
    }

    @Test
    public void invalidFree() {
        final var pool1 = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class)
                .blocksPerSegment(16).build();

        assertThrows(IllegalArgumentException.class, () -> pool1.free(0));
        assertThrows(IllegalArgumentException.class, () -> pool1.free(null));

        assertThrows(IllegalArgumentException.class, () -> pool1.free(null));

        var empty = new TestFlyweight();
        assertThrows(IllegalStateException.class, () -> pool1.free(empty));

        assertDoesNotThrow(() -> {
            final var pool2 = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class)
                    .blocksPerSegment(16).build();
            final var other1 = pool1.allocate();
            assertThrows(IllegalStateException.class, () -> pool2.free(other1));
            assertDoesNotThrow(() -> pool1.free(other1));

            pool1.close();
            pool2.close();
        });

        // closed
        assertThrows(IllegalStateException.class, pool1::allocate);
    }

    @Test
    public void preAllocSegments() {
        final var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class).blocksPerSegment(64).build();
        var block1 = pool.allocate();
        assertEquals(0, block1.segment());
        assertEquals(0, block1.block());

        var block2 = pool.allocate();
        assertEquals(0, block2.segment());
        assertEquals(1, block2.block());

        for (int position = 0; position < 62; ++position) {
            pool.allocate();
        }

        var block3 = pool.allocate();
        assertEquals(1, block3.segment());
        assertEquals(0, block3.block());

        var block4 = pool.allocate();
        assertEquals(1, block4.segment());
        assertEquals(1, block4.block());

        for (int position = 0; position < 62; ++position) {
            pool.allocate();
        }

        var block5 = pool.allocate();
        assertEquals(2, block5.segment());
        assertEquals(0, block5.block());

        pool.close();
    }

    @Test
    public void invalidPoolParams() {
        assertThrows(IllegalArgumentException.class,
                () -> new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class)
                        .allocatedSegments(-1).build());
    }

    @Test
    public void skipChecks() {
        final var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class)
            .blocksPerSegment(16).build();
        final var block1 = pool.allocate();
        assertEquals(0, block1.segment());
        assertEquals(0, block1.block());
    }

    @Test
    public void multipleSegments() {
        final var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class)
            .blocksPerSegment(1024 * 1024).build();
        var block = pool.allocate().int32(0);
        for (int i = 0; i < 10_000_000; ++i) {
            block = pool.allocate().int32(i + 2);
        }
        assertEquals(335_544_320L, pool.allocatedBytes());
        assertEquals(10_000_001, block.int32());
        assertEquals("BlockPool{ size = 32, blocks = 10 485 760, segments = 10, bytes = 335 544 320 }", pool.toString());
        pool.close();
    }

    @Test
    public void segmentListRealloc() {
        final var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class)
            .blocksPerSegment(1024 * 1024).build();
        var block = new TestFlyweight();
        for (int i = 0; i < 10_000_000; ++i) {
            pool.allocate(block).int32(i + 2);
        }
        assertEquals(335_544_320L, pool.allocatedBytes());
        assertEquals(10_000_001, block.int32());
        assertEquals("BlockPool{ size = 32, blocks = 10 485 760, segments = 10, bytes = 335 544 320 }", pool.toString());
        pool.close();
    }

    @Test
    public void nullArena() {
        assertThrows(IllegalArgumentException.class,
            () -> new BlockPool.Builder<>(null, TestFlyweight.class) .blocksPerSegment(1024).build());

        assertThrows(IllegalArgumentException.class,
            () -> new BlockPool.Builder<>(Arena.ofShared(), null) .blocksPerSegment(1024).build());
    }

    @Test
    public void manyAllocsAndFrees() {
        final var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class).blocksPerSegment(1_000_000).build();
        long[] addresses = new long[2_000_000];
        int count = 0;
        var block = new TestFlyweight();
        for (int i = 1; i <= 20_000_000; ++i) {
            pool.allocate(block).int32(i + 2);
            if (count < addresses.length) {
                addresses[count] = block.address();
                ++count;
            }
            if (i % (addresses.length *2) == 0) {
                for (var address : addresses) {
                    pool.free(address);
                }
                count = 0;
            }
        }
        pool.close();
    }

    @Test
    public void hashMap() {
        final var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class).blocksPerSegment(1_000).build();
        final TestFlyweight block = new TestFlyweight();
        final TestFlyweight found = new TestFlyweight();
        final HashMap<Long, Long> map = new HashMap<>();
        for (int i = 0; i < 1000; ++i) {
            pool.allocate(block).int64(i);
            map.put((long) i, block.address());
        }
        for (int i = 0; i < 1000; ++i) {
            Long address = map.get((long) i);
            assertNotNull(address);
            pool.get(address, found);
            assertEquals(i, found.int64());
        }
    }
}
