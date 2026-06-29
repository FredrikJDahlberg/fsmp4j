package org.limitless.fsmp4j;

import java.lang.foreign.Arena;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MemoryErrorsTest {

    // Bug 1: dstOffset parameter is silently ignored — data always lands at position 0
    @Test
    public void dstOffsetIsRespected() {
        try (Arena arena = Arena.ofConfined()) {
            var pool = new BlockPool.Builder<>(arena, TestFlyweight.class)
                .blocksPerSegment(16).build();
            var block = pool.allocate();
            block.bytes(new byte[]{1, 2, 3, 4, 5});

            byte[] dest = new byte[10];
            block.bytesAt(3, dest);

            assertEquals(0, dest[0], "positions before dstOffset must be zero");
            assertEquals(0, dest[1]);
            assertEquals(0, dest[2]);
            assertEquals(1, dest[3], "data must start at dstOffset");
            assertEquals(2, dest[4]);
            assertEquals(3, dest[5]);
        }
    }

    // Bug 2: get() does no bounds check — invalid address causes ArrayIndexOutOfBoundsException
    @Test
    public void invalidAddressThrowsIllegalState() {
        try (Arena arena = Arena.ofConfined()) {
            var pool = new BlockPool.Builder<>(arena, TestFlyweight.class)
                .blocksPerSegment(16).build();
            var block = new TestFlyweight();
            assertThrows(IllegalStateException.class, () -> pool.get(Long.MAX_VALUE, block));
        }
    }

    // Bug 3: calling native accessors on a cleared flyweight (blockIndex == -1) throws NPE
    @Test
    public void accessAfterFreeThrowsIllegalState() {
        try (Arena arena = Arena.ofConfined()) {
            var pool = new BlockPool.Builder<>(arena, TestFlyweight.class)
                .blocksPerSegment(16).build();
            var block = pool.allocate();
            pool.free(block);
            assertThrows(IllegalStateException.class, () -> block.int32());
        }
    }

    // Bug 4: nativeString length check uses char count, not UTF-8 byte count
    @Test
    public void multiByteStringOverflowThrows() {
        try (Arena arena = Arena.ofConfined()) {
            var pool = new BlockPool.Builder<>(arena, TestFlyweight.class)
                .blocksPerSegment(16).build();
            var block = pool.allocate();
            // 5 chars < STRING_LENGTH(12), but 15 UTF-8 bytes + null > 12
            assertThrows(IllegalArgumentException.class, () -> block.string("日本語世界"));
        }
    }

    // Bug 5: free(long address) called twice silently corrupts the free list
    @Test
    public void doubleFreeViaAddressThrows() {
        try (Arena arena = Arena.ofConfined()) {
            var pool = new BlockPool.Builder<>(arena, TestFlyweight.class)
                .blocksPerSegment(16).build();
            var block = pool.allocate();
            long address = block.address();
            pool.free(address);
            assertThrows(IllegalStateException.class, () -> pool.free(address));
        }
    }
}
