package org.limitless.fsmp4j;

public final class ByteUtils {

    public static long pack(final int high, final int low) {
        return ((long) high) << 32 | low;
    }

    public static int highBits(final long value) {
        return (int) (value >>> 32);
    }

    public static int lowBits(final long value) {
        return (int) value;
    }

    public static int align(final int value, final int alignment) {
        return (value + (alignment - 1)) & -alignment;
    }

    public static long align(final long value, final long alignment) {
        return (value + (alignment - 1)) & -alignment;
    }
}
