package org.limitless.fsmp4j;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.*;

public class BlockFlyweightTest {

    @Test
    public void basics() {
        var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class).blocksPerSegment(24).build();
        var block = pool.allocate()
            .int64(1_000_000_000_001L).int32(1_000_001).int16((short) 2).int8((byte) 83)
            .bytes("bytes".getBytes()).string("string");
        assertEquals(1_000_000_000_001L, block.int64());
        assertEquals(1_000_001, block.int32());
        assertEquals(2, block.int16());
        assertEquals(83, block.int8());
        assertEquals("bytes", new String(block.bytes()));
        assertEquals("string", block.string());

        StringBuilder builder = new StringBuilder(64);
        assertEquals("{Test, int64=1000000000001, int32=1000001, int16=2, int8=83, bytes=bytes, string=string}",
            block.append(builder).toString());
    }

    public static class ArrayFlyweight extends BlockFlyweight {
        private static final int INT_OFFSET = 0;
        private static final int INT_LENGTH = Integer.BYTES;
        private static final int ARRAY_OFFSET = INT_OFFSET + INT_LENGTH;
        private static final int ARRAY_LENGTH = 20;
        private static final int BYTES = ARRAY_OFFSET + ARRAY_LENGTH;

        @Override
        protected StringBuilder append(StringBuilder builder) {
            return builder;
        }

        @Override
        public int encodedLength() {
            return BYTES;
        }

        public byte[] array() {
            return nativeByteArray(ARRAY_OFFSET, ARRAY_LENGTH, new byte[ARRAY_LENGTH]);
        }

        public ArrayFlyweight array(byte[] value) {
            nativeByteArray(value, ARRAY_OFFSET, ARRAY_LENGTH);
            return this;
        }
    }

    @Test
    public void byteArray() {
        var pool = new BlockPool.Builder<>(Arena.ofShared(), ArrayFlyweight.class).blocksPerSegment(24).build();
        var block = pool.allocate().array("12345678901234567890".getBytes());
        byte[] array = block.array();
        assertEquals("12345678901234567890", new String(array));
    }

    @Test
    public void equalsCompare() {
        var pool = new BlockPool.Builder<>(Arena.ofShared(), TestFlyweight.class).blocksPerSegment(16).build();
        var block1 = pool.allocate().int64(123);
        var block2 = pool.allocate().int64(123);

        assertEquals(block1, block1);
        assertNotEquals(block1, block2);
        assertNotEquals(block2, block1);
        assertTrue(block1.compare(block2));

        block2.int64(124);
        assertFalse(block1.compare(block2));
        assertFalse(block2.compare(block1));
    }
}
