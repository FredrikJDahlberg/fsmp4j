package org.limitless.fsmp4j;


import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Base class for pooled blocks. Subclasses define a layout by implementing {@link #encodedLength()} and
 * exposing typed accessors built on the protected {@code native*()} methods. Fields may be packed at any
 * offset; no alignment is required.
 */
public abstract class BlockFlyweight implements Flyweight {

    private static final ValueLayout.OfChar CHAR = ValueLayout.JAVA_CHAR_UNALIGNED;
    private static final ValueLayout.OfShort SHORT = ValueLayout.JAVA_SHORT_UNALIGNED;
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED;
    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG_UNALIGNED;
    private static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED;
    private static final ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE_UNALIGNED;

    private MemorySegment segment;
    private int blockIndex;
    private int segmentIndex;
    private long blockOffset;   // byte offset of the block in the segment

    public BlockFlyweight() {
        blockIndex = BlockPool.INVALID_INDEX;
        segmentIndex = BlockPool.INVALID_INDEX;
    }

    /**
     * Initiate the flyweight
     * @param segment memory segmen
     * @param segmentIndex index of segment
     * @param blockIndex index of block
     */
    @Override
    public void wrap(final MemorySegment segment, final int segmentIndex, final int blockIndex) {
        this.segment = segment;
        this.blockIndex = blockIndex;
        this.segmentIndex = segmentIndex;
        this.blockOffset = (long) blockIndex * BlockPool.blockLength(encodedLength());
    }

    /**
     * Initiate the flyweight with the block offset already computed by the pool
     * @param segment memory segment
     * @param segmentIndex index of segment
     * @param blockIndex index of block
     * @param blockOffset byte offset of the block in the segment
     */
    void wrap(final MemorySegment segment, final int segmentIndex, final int blockIndex, final long blockOffset) {
        this.segment = segment;
        this.blockIndex = blockIndex;
        this.segmentIndex = segmentIndex;
        this.blockOffset = blockOffset;
    }

    /**
     * Flyweight block index
     * @return block index
     */
    @Override
    public int block() {
        return blockIndex;
    }

    /**
     * Flyweight segment index
     * @return segment index
     */
    @Override
    public int segment() {
        return segmentIndex;
    }

    /**
     * Block memory segment
     * @return memory segment
     */
    @Override
    public MemorySegment memorySegment() {
        return segment;
    }

    /**
     * Block address is the segment and block index, not the actual segment address.
     * @return block address
     */
    @Override
    public long address() {
        return ByteUtils.pack(segmentIndex + 1, blockIndex);
    }

    /**
     * Whether the flyweight currently points at a block. It is unwrapped when new and after being freed.
     * @return true if wrapped
     */
    public boolean isWrapped() {
        return blockIndex != BlockPool.INVALID_INDEX;
    }

    /**
     * Block hashCode
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(blockIndex, segmentIndex);
    }

    /**
     * Block equals method
     * @param object flyweight
     * @return equality
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        final BlockFlyweight that = (BlockFlyweight) object;
        return blockIndex == that.blockIndex && segmentIndex == that.segmentIndex && Objects.equals(segment, that.segment);
    }

    /**
     * Clear flyweight
     */
    public void clear() {
        segment = null;
        segmentIndex = BlockPool.INVALID_INDEX;
        blockIndex = BlockPool.INVALID_INDEX;
        blockOffset = 0;
    }

    /**
     * Set byte value
     * @param offset in flyweight
     * @param value byte
     */
    protected void nativeByte(final int offset, final byte value) {
        segment.set(ValueLayout.JAVA_BYTE, fieldOffset(offset), value);
    }

    /**
     * Get byte value
     * @param offset in flyweight
     * @return value
     */
    protected byte nativeByte(final int offset) {
        return segment.get(ValueLayout.JAVA_BYTE, fieldOffset(offset));
    }

    /**
     * Set short value
     * @param offset in flyweight
     * @param value short
     */
    protected void nativeShort(final int offset, final short value) {
        segment.set(SHORT, fieldOffset(offset), value);
    }

    /**
     * Get short value
     * @param offset in flyweight
     * @return value
     */
    protected short nativeShort(final int offset) {
        return segment.get(SHORT, fieldOffset(offset));
    }

    /**
     * Set int value
     * @param offset in flyweight
     * @param value integer
     */
    protected void nativeInt(final int offset, final int value) {
        segment.set(INT, fieldOffset(offset), value);
    }

    /**
     * Get int value
     * @param offset in flyweight
     * @return value
     */
    protected int nativeInt(final int offset) {
        return segment.get(INT, fieldOffset(offset));
    }

    /**
     * Set long value
     * @param offset in flyweight
     * @param value long
     */
    protected void nativeLong(final int offset, final long value) {
        segment.set(LONG, fieldOffset(offset), value);
    }

    /**
     * Get long value
     * @param offset in flyweight
     * @return value
     */
    protected long nativeLong(final int offset) {
        return segment.get(LONG, fieldOffset(offset));
    }

    /**
     * Set char value
     * @param offset in flyweight
     * @param value char
     */
    protected void nativeChar(final int offset, final char value) {
        segment.set(CHAR, fieldOffset(offset), value);
    }

    /**
     * Get char value
     * @param offset in flyweight
     * @return value
     */
    protected char nativeChar(final int offset) {
        return segment.get(CHAR, fieldOffset(offset));
    }

    /**
     * Set boolean value, stored as a single byte
     * @param offset in flyweight
     * @param value boolean
     */
    protected void nativeBoolean(final int offset, final boolean value) {
        segment.set(ValueLayout.JAVA_BYTE, fieldOffset(offset), value ? (byte) 1 : (byte) 0);
    }

    /**
     * Get boolean value, stored as a single byte
     * @param offset in flyweight
     * @return value
     */
    protected boolean nativeBoolean(final int offset) {
        return segment.get(ValueLayout.JAVA_BYTE, fieldOffset(offset)) != 0;
    }

    /**
     * Set float value
     * @param offset in flyweight
     * @param value float
     */
    protected void nativeFloat(final int offset, final float value) {
        segment.set(FLOAT, fieldOffset(offset), value);
    }

    /**
     * Get float value
     * @param offset in flyweight
     * @return value
     */
    protected float nativeFloat(final int offset) {
        return segment.get(FLOAT, fieldOffset(offset));
    }

    /**
     * Set double value
     * @param offset in flyweight
     * @param value double
     */
    protected void nativeDouble(final int offset, final double value) {
        segment.set(DOUBLE, fieldOffset(offset), value);
    }

    /**
     * Get double value
     * @param offset in flyweight
     * @return value
     */
    protected double nativeDouble(final int offset) {
        return segment.get(DOUBLE, fieldOffset(offset));
    }

    /**
     * Calculate offset of the field
     * @param offset in flyweight
     * @return field segment offset
     */
    protected long fieldOffset(final int offset) {
        if (blockIndex == BlockPool.INVALID_INDEX) {
            throw new IllegalStateException("flyweight is not wrapped");
        }
        return blockOffset + offset;
    }

    /**
     * Calculate offset of a field, checking that it lies within the block
     * @param offset in flyweight
     * @param length of the field
     * @return field segment offset
     * @throws IndexOutOfBoundsException the field is outside the block
     */
    protected long fieldOffset(final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, encodedLength());
        return fieldOffset(offset);
    }

    /**
     * Get a byte array
     * @param offset flyweight string offset
     * @param length flyweight length
     * @param bytes destination byte array
     * @return destination byte array
     * @throws IllegalArgumentException null value
     */
    protected byte[] nativeByteArray(final int offset, final int length, final byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("null argument");
        }
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, fieldOffset(offset, length), bytes, 0, length);
        return bytes;
    }

    /**
     * Get a byte array
     * @param offset in flyweight
     * @param length of string
     * @param dstOffset in buffer
     * @param bytes buffer
     * @return buffer
     */
    protected byte[] nativeByteArray(final int offset, final int length, final int dstOffset, final byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("null argument");
        }
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, fieldOffset(offset, length), bytes, dstOffset, length);
        return bytes;
    }

    /**
     * Set the byte array
     * @param bytes source
     * @param offset flyweight string offset
     * @param length array length
     * @throws IllegalArgumentException null value
     */
    protected void nativeByteArray(final byte[] bytes, final int offset, final int length) {
        if (bytes == null) {
            throw new IllegalArgumentException("null argument");
        }
        MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, fieldOffset(offset, length), length);
    }

    /**
     * Set the byte array
     * @param bytes source
     * @param position source position
     * @param offset flyweight string offset
     * @param length array length
     * @throws IllegalArgumentException null value
     */
    protected void nativeByteArray(final int position, final byte[] bytes, final int offset, final int length) {
        if (bytes == null) {
            throw new IllegalArgumentException("null argument");
        }
        MemorySegment.copy(bytes, position, segment, ValueLayout.JAVA_BYTE, fieldOffset(offset, length), length);
    }

    /**
     * Get string value
     * @param offset flyweight offset
     * @return string value
     * @throws IndexOutOfBoundsException the string is not terminated within the block
     */
    protected String nativeString(final int offset) {
        final int length = encodedLength() - offset;
        return segment.asSlice(fieldOffset(offset, length), length).getString(0);
    }

    /**
     * Set string value
     * @param value string
     * @param offset flyweight offset
     * @param length maximal length
     * @throws IllegalArgumentException too long string
     * @throws IndexOutOfBoundsException the field is outside the block
     */
    protected void nativeString(final String value, final int offset, final int length) {
        final long position = fieldOffset(offset, length);
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= length) {
            throw new IllegalArgumentException("string is too long");
        }
        segment.setString(position, value);
    }

    /**
     * Append a byte array to a string builder
     * @param offset  flyweight offset
     * @param length  length
     * @param builder string builder
     * @return string builder
     */
    protected StringBuilder append(final int offset, final int length, final StringBuilder builder) {
        final long position = fieldOffset(offset, length);
        for (int index = 0; index < length; ++index) {
            char value = (char) segment.get(ValueLayout.JAVA_BYTE, index + position);
            builder.append(value);
        }
        return builder;
    }

    /**
     * Compare flyweights
     * @param object other flyweight
     * @return equality
     */
    public boolean compare(BlockFlyweight object) {
        final int blockSize = encodedLength();
        final long srcOffset = fieldOffset(0);
        final long dstOffset = object.fieldOffset(0);
        return MemorySegment.mismatch(segment, srcOffset, srcOffset + blockSize,
            object.segment, dstOffset, dstOffset + blockSize) == -1;
    }

    /**
     * Flyweight string builder helper, used by {@link #toString()}. Override to include the block's fields.
     * @param builder string builder
     * @return builder
     */
    protected StringBuilder append(final StringBuilder builder) {
        return builder.append(getClass().getSimpleName())
            .append("{segment=").append(segmentIndex)
            .append(", block=").append(blockIndex).append('}');
    }

    @Override
    public String toString() {
        if (!isWrapped()) {
            return getClass().getSimpleName() + "{unwrapped}";
        }
        if (!segment.scope().isAlive()) {
            return getClass().getSimpleName() + "{closed}";
        }
        return append(new StringBuilder(64)).toString();
    }
}
