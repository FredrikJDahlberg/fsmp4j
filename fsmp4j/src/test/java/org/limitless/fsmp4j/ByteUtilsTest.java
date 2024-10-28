package org.limitless.fsmp4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteUtilsTest {

    @Test
    public void alignment() {
        assertEquals(0, ByteUtils.align(0, Byte.BYTES));
        assertEquals(3, ByteUtils.align(3, Byte.BYTES));
        assertEquals(4, ByteUtils.align(4, Byte.BYTES));

        assertEquals(0, ByteUtils.align(0, Short.BYTES));
        assertEquals(4, ByteUtils.align(3, Short.BYTES));
        assertEquals(4, ByteUtils.align(4, Short.BYTES));

        assertEquals(0, ByteUtils.align(0, Integer.BYTES));
        assertEquals(4, ByteUtils.align(4, Integer.BYTES));
        assertEquals(8, ByteUtils.align(8, Integer.BYTES));

        assertEquals(0, ByteUtils.align(0, Long.BYTES));
        assertEquals(8, ByteUtils.align(3, Long.BYTES));
        assertEquals(8, ByteUtils.align(8, Long.BYTES));
        assertEquals(16, ByteUtils.align(12, Long.BYTES));

        assertEquals(0L, ByteUtils.align(0L, Long.BYTES));
        assertEquals(8L, ByteUtils.align(3L, Long.BYTES));
        assertEquals(8L, ByteUtils.align(8L, Long.BYTES));
        assertEquals(16L, ByteUtils.align(12L, Long.BYTES));
    }
}
