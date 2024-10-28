package org.limitless.fsmp4j;


import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

public abstract class BlockFlyweight implements Flyweight {

    private MemorySegment segment;
    private int blockIndex;
    private int segmentIndex;

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
    }

    /**
     * Flyweight block index
     * @return block index
     */
    @Override
    public int index() {
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
        return blockIndex == that.blockIndex && segmentIndex == that.segmentIndex && segment.equals(that.segment);
    }

    /**
     * Clear flyweight
     */
    public void clear() {
        segment = null;
        segmentIndex = BlockPool.INVALID_INDEX;
        blockIndex = BlockPool.INVALID_INDEX;
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
    public void nativeShort(final int offset, final short value) {
        segment.set(ValueLayout.JAVA_SHORT, fieldOffset(offset), value);
    }

    /**
     * Get byte value
     * @param offset in flyweight
     * @return value
     */
    protected short nativeShort(final int offset) {
        return segment.get(ValueLayout.JAVA_SHORT, fieldOffset(offset));
    }

    /**
     * Set int value
     * @param offset in flyweight
     * @param value integer
     */
    protected void nativeInt(final int offset, final int value) {
        segment.set(ValueLayout.JAVA_INT, fieldOffset(offset), value);
    }

    /**
     * Get int value
     * @param offset in flyweight
     * @return value
     */
    protected int nativeInt(final int offset) {
        return segment.get(ValueLayout.JAVA_INT, fieldOffset(offset));
    }

    /**
     * Set long value
     * @param offset in flyweight
     * @param value long
     */
    protected void nativeLong(final int offset, final long value) {
        segment.set(ValueLayout.JAVA_LONG, fieldOffset(offset), value);
    }

    /**
     * Get long value
     * @param offset in flyweight
     * @return value
     */
    protected long nativeLong(final int offset) {
        return segment.get(ValueLayout.JAVA_LONG, fieldOffset(offset));
    }

    /**
     * Calculate offset of the field
     * @param offset in flyweight
     * @return field segment offset
     */
    private long fieldOffset(final int offset) {
        return (long) blockIndex * encodedLength() + offset;
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
        final long position = (long) blockIndex * encodedLength() + offset;
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, position, bytes, 0, length);
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
        final long position = (long) blockIndex * encodedLength() + offset;
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, position, bytes, dstOffset, length);
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
        MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, fieldOffset(offset), length);
    }

    /**
     * Get string value
     * @param offset flyweight offset
     * @return string value
     */
    protected String nativeString(final int offset) {
        return segment.getString(fieldOffset(offset));
    }

    /**
     * Set string value
     * @param value string
     * @param offset flyweight offset
     * @param length maximal length
     * @throws IllegalArgumentException too long string
     */
    protected void nativeString(final String value, final int offset, final int length) {
        if (value.length() >= length)
        {
            throw new IllegalArgumentException("string is to long");
        }
        segment.setString(fieldOffset(offset), value);
    }

    /**
     * Append a byte array to a string builder
     * @param offset  flyweight offset
     * @param length  length
     * @param builder string builder
     * @return string builder
     */
    protected StringBuilder append(final int offset, final int length, final StringBuilder builder) {
        final long position = fieldOffset(offset);
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
     * Flyweight string builder helper
     * @param builder string builder
     * @return builder
     */
    protected abstract StringBuilder append(final StringBuilder builder);
}
