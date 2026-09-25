package org.limitless.fsmp4j;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.HashSet;

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
        assertEquals(1, block4.block()); // get() must not consume a block

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
        assertEquals("BlockPool{ size = 32, blocks = 10,485,760, segments = 10, bytes = 335,544,320 }", pool.toString());
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
        assertEquals("BlockPool{ size = 32, blocks = 10,485,760, segments = 10, bytes = 335,544,320 }", pool.toString());
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

    @Test
    public void builderWithFactory() {
        try (var pool = BlockPool.builder(Arena.ofConfined(), TestFlyweight::new).build()) {
            final var block = pool.allocate().int64(42);
            assertEquals(42, pool.get(block.address()).int64());
            assertEquals(32, pool.blockLength());
            assertEquals(BlockPool.DEFAULT_BLOCKS_PER_SEGMENT * 32L, pool.allocatedBytes());
        }
    }

    @Test
    public void getDoesNotAllocate() {
        try (var pool = new BlockPool.Builder<>(Arena.ofConfined(), TestFlyweight.class).blocksPerSegment(4).build()) {
            final var block = pool.allocate();
            assertEquals(1, pool.blocksInUse());
            pool.get(block.address());
            assertEquals(1, pool.blocksInUse());

            for (int i = 0; i < 9; ++i) {
                pool.allocate();
            }
            assertEquals(10, pool.blocksInUse());
            pool.free(block);
            assertEquals(9, pool.blocksInUse());
        }
    }

    @Test
    public void manyPreAllocatedSegments() {
        try (var pool = BlockPool.builder(Arena.ofConfined(), TestFlyweight::new)
                .blocksPerSegment(1).allocatedSegments(100).build()) {
            for (int i = 0; i < 200; ++i) {
                pool.allocate();
            }
            assertEquals(200, pool.blocksInUse());
        }
    }

    @Test
    public void invalidBuilderArguments() {
        try (Arena arena = Arena.ofConfined()) {
            assertThrows(IllegalArgumentException.class,
                () -> BlockPool.builder(arena, TestFlyweight::new).blocksPerSegment(0).build());
            assertThrows(IllegalArgumentException.class,
                () -> BlockPool.builder(arena, TestFlyweight::new).allocatedSegments(0).build());
            assertThrows(IllegalArgumentException.class,
                () -> BlockPool.builder(arena, () -> (TestFlyweight) null).build());
            assertThrows(IllegalArgumentException.class,
                () -> new BlockPool.Builder<>(arena, NoDefaultConstructor.class).build());

            final var shared = new TestFlyweight();
            assertThrows(IllegalArgumentException.class, () -> BlockPool.builder(arena, () -> shared).build());
        }
    }

    public static class NoDefaultConstructor extends BlockFlyweight {
        public NoDefaultConstructor(int ignored) {
        }

        @Override
        public int encodedLength() {
            return Long.BYTES;
        }
    }

    // 20 bytes, padded to 24 by the pool
    public static class OddSizeFlyweight extends BlockFlyweight {
        private static final int ID_OFFSET = 0;
        private static final int VALUE_OFFSET = ID_OFFSET + Long.BYTES + Long.BYTES;
        private static final int BYTES = VALUE_OFFSET + Integer.BYTES;

        @Override
        public int encodedLength() {
            return BYTES;
        }

        public long id() {
            return nativeLong(ID_OFFSET);
        }

        public int value() {
            return nativeInt(VALUE_OFFSET);
        }

        public OddSizeFlyweight set(long id, int value) {
            nativeLong(ID_OFFSET, id);
            nativeInt(VALUE_OFFSET, value);
            return this;
        }
    }

    @Test
    public void unpaddedBlockSize() {
        try (var pool = BlockPool.builder(Arena.ofConfined(), OddSizeFlyweight::new).blocksPerSegment(16).build()) {
            assertEquals(24, pool.blockLength());
            final long[] addresses = new long[100];
            final var unique = new HashSet<Long>();
            for (int i = 0; i < addresses.length; ++i) {
                addresses[i] = pool.allocate().set(i, -i).address();
                assertTrue(unique.add(addresses[i]), "block handed out twice");
            }
            final var block = new OddSizeFlyweight();
            for (int i = 0; i < addresses.length; ++i) {
                pool.get(addresses[i], block);
                assertEquals(i, block.id());
                assertEquals(-i, block.value());
            }

            pool.free(addresses[1]);
            assertThrows(IllegalStateException.class, () -> pool.free(addresses[1]));
        }
    }

    @Test
    public void closeUncloseableArena() {
        assertDoesNotThrow(() -> {
            try (var pool = BlockPool.builder(Arena.global(), TestFlyweight::new).blocksPerSegment(1).build()) {
                pool.allocate();
            }
            try (var pool = BlockPool.builder(Arena.ofAuto(), TestFlyweight::new).blocksPerSegment(1).build()) {
                pool.allocate();
            }
        });
    }

    // same type as the pool, but a larger block
    public static class LargerFlyweight extends TestFlyweight {
        @Override
        public int encodedLength() {
            return 64;
        }
    }

    @Test
    public void rejectMismatchedBlockSize() {
        try (Arena arena = Arena.ofConfined()) {
            final var pool = BlockPool.builder(arena, TestFlyweight::new).blocksPerSegment(16).build();
            final var block = pool.allocate().int64(1);
            final var larger = new LargerFlyweight();
            assertThrows(IllegalArgumentException.class, () -> pool.allocate(larger));
            assertThrows(IllegalArgumentException.class, () -> pool.get(block.address(), larger));

            larger.wrap(block.memorySegment(), block.segment(), block.block());
            assertThrows(IllegalArgumentException.class, () -> pool.free(larger));
            assertEquals(1, pool.blocksInUse());
            assertEquals(1, block.int64());
        }
    }

    public static class HugeFlyweight extends BlockFlyweight {
        @Override
        public int encodedLength() {
            return Integer.MAX_VALUE;
        }
    }

    @Test
    public void rejectHugeBlockSize() {
        try (Arena arena = Arena.ofConfined()) {
            final var error = assertThrows(IllegalArgumentException.class,
                () -> BlockPool.builder(arena, HugeFlyweight::new).blocksPerSegment(1).build());
            assertTrue(error.getMessage().startsWith("encodedLength is too large"), error.getMessage());
        }
    }

    public static class IntFlyweight extends BlockFlyweight {
        @Override
        public int encodedLength() {
            return Integer.BYTES;
        }

        public int value() {
            return nativeInt(0);
        }

        public IntFlyweight value(int value) {
            nativeInt(0, value);
            return this;
        }
    }

    @Test
    public void smallBlocks() {
        try (Arena arena = Arena.ofConfined()) {
            final var pool = BlockPool.builder(arena, IntFlyweight::new).blocksPerSegment(16).build();
            assertEquals(Long.BYTES, pool.blockLength());
            final long[] addresses = new long[100];
            for (int i = 0; i < addresses.length; ++i) {
                addresses[i] = pool.allocate().value(i).address();
            }
            final var block = new IntFlyweight();
            for (int i = 0; i < addresses.length; ++i) {
                assertEquals(i, pool.get(addresses[i], block).value());
            }
            for (int i = 0; i < addresses.length; i += 2) {
                pool.free(addresses[i]);
            }
            assertEquals(50, pool.blocksInUse());
            assertThrows(IllegalStateException.class, () -> pool.free(addresses[0]));
        }
    }

    @Test
    public void userDataIsNotMistakenForFreeBlock() {
        try (Arena arena = Arena.ofConfined()) {
            final var pool = BlockPool.builder(arena, TestFlyweight::new).blocksPerSegment(16).build();
            // the old free list cookie, and bit patterns close to the free list encoding
            final var block1 = pool.allocate().int64(0).int32(0xdeadbeef);
            final var block2 = pool.allocate().int64(-1L).int32(0xdeadbeef);
            final var block3 = pool.allocate().int64(0xdeadbeefcafebabeL);
            assertDoesNotThrow(() -> pool.free(block1));
            assertDoesNotThrow(() -> pool.free(block2));
            assertDoesNotThrow(() -> pool.free(block3));
            assertEquals(0, pool.blocksInUse());
        }
    }
}
