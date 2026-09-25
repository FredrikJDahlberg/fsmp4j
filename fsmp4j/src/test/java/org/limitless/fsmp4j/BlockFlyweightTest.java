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

    public static class PackedFlyweight extends BlockFlyweight {
        private static final int FLAG_OFFSET = 0;
        private static final int LONG_OFFSET = FLAG_OFFSET + Byte.BYTES;
        private static final int DOUBLE_OFFSET = LONG_OFFSET + Long.BYTES;
        private static final int FLOAT_OFFSET = DOUBLE_OFFSET + Double.BYTES;
        private static final int CHAR_OFFSET = FLOAT_OFFSET + Float.BYTES;
        private static final int BYTES = CHAR_OFFSET + Character.BYTES;

        @Override
        public int encodedLength() {
            return BYTES;
        }

        public boolean flag() {
            return nativeBoolean(FLAG_OFFSET);
        }

        public PackedFlyweight flag(boolean value) {
            nativeBoolean(FLAG_OFFSET, value);
            return this;
        }

        public long int64() {
            return nativeLong(LONG_OFFSET);
        }

        public PackedFlyweight int64(long value) {
            nativeLong(LONG_OFFSET, value);
            return this;
        }

        public double float64() {
            return nativeDouble(DOUBLE_OFFSET);
        }

        public PackedFlyweight float64(double value) {
            nativeDouble(DOUBLE_OFFSET, value);
            return this;
        }

        public float float32() {
            return nativeFloat(FLOAT_OFFSET);
        }

        public PackedFlyweight float32(float value) {
            nativeFloat(FLOAT_OFFSET, value);
            return this;
        }

        public char character() {
            return nativeChar(CHAR_OFFSET);
        }

        public PackedFlyweight character(char value) {
            nativeChar(CHAR_OFFSET, value);
            return this;
        }
    }

    @Test
    public void packedLayout() {
        try (var pool = BlockPool.builder(Arena.ofConfined(), PackedFlyweight::new).blocksPerSegment(16).build()) {
            pool.allocate();
            var block = pool.allocate().flag(true).int64(-7L).float64(1.5).float32(2.5f).character('x');
            assertTrue(block.flag());
            assertEquals(-7L, block.int64());
            assertEquals(1.5, block.float64());
            assertEquals(2.5f, block.float32());
            assertEquals('x', block.character());
        }
    }

    @Test
    public void defaultToString() {
        try (var pool = BlockPool.builder(Arena.ofConfined(), PackedFlyweight::new).blocksPerSegment(16).build()) {
            var block = pool.allocate();
            assertTrue(block.isWrapped());
            assertEquals("PackedFlyweight{segment=0, block=0}", block.toString());
            pool.free(block);
            assertFalse(block.isWrapped());
            assertEquals("PackedFlyweight{unwrapped}", block.toString());
            assertEquals(new PackedFlyweight(), new PackedFlyweight());
        }
    }

    @Test
    public void toStringAfterArenaClosed() {
        final TestFlyweight block;
        try (Arena arena = Arena.ofConfined()) {
            block = BlockPool.builder(arena, TestFlyweight::new).blocksPerSegment(16).build().allocate();
        }
        assertEquals("TestFlyweight{closed}", block.toString());
    }

    @Test
    public void byteArrayCannotOverflowBlock() {
        try (Arena arena = Arena.ofConfined()) {
            var pool = BlockPool.builder(arena, TestFlyweight::new).blocksPerSegment(16).build();
            var block = pool.allocate();
            var next = pool.allocate().int64(2);
            assertThrows(IndexOutOfBoundsException.class, () -> block.bytes(new byte[40]));
            assertThrows(IndexOutOfBoundsException.class, () -> block.bytesAt(0, new byte[2]));
            assertEquals(2, next.int64());
        }
    }

    public static class RawFlyweight extends BlockFlyweight {
        private static final int BYTES = 16;

        @Override
        public int encodedLength() {
            return BYTES;
        }

        public RawFlyweight raw(byte[] value) {
            nativeByteArray(value, 0, value.length);
            return this;
        }

        public String string(int offset) {
            return nativeString(offset);
        }

        public RawFlyweight string(int offset, int length, String value) {
            nativeString(value, offset, length);
            return this;
        }
    }

    @Test
    public void stringCannotOverflowBlock() {
        try (Arena arena = Arena.ofConfined()) {
            var pool = BlockPool.builder(arena, RawFlyweight::new).blocksPerSegment(16).build();
            var block = pool.allocate();
            var next = pool.allocate().raw(new byte[]{'x', 0});

            // no terminator in the block: reading must not continue into the next block
            block.raw("AAAAAAAAAAAAAAAA".getBytes());
            assertThrows(IndexOutOfBoundsException.class, () -> block.string(8));
            assertEquals("x", next.string(0));

            assertThrows(IndexOutOfBoundsException.class, () -> block.string(8, 12, "abc"));
            assertEquals("abc", block.string(8, 8, "abc").string(8));
        }
    }
}
