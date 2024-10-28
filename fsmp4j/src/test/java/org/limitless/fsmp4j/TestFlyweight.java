package org.limitless.fsmp4j;

public class TestFlyweight extends BlockFlyweight {

    private static final int LONG_OFFSET = 0;
    private static final int LONG_LENGTH = Long.BYTES;
    private static final int INT_OFFSET = LONG_OFFSET + LONG_LENGTH;
    private static final int INT_LENGTH = Integer.BYTES;
    private static final int SHORT_OFFSET = INT_OFFSET + INT_LENGTH;
    private static final int SHORT_LENGTH = Short.BYTES;
    private static final int BYTE_OFFSET = SHORT_OFFSET + SHORT_LENGTH;
    private static final int BYTE_LENGTH = Byte.BYTES;
    private static final int ARRAY_OFFSET  = BYTE_OFFSET + BYTE_LENGTH;
    private static final int ARRAY_LENGTH = 5;
    private static final int STRING_OFFSET = ARRAY_OFFSET + ARRAY_LENGTH;
    private static final int STRING_LENGTH = 12;

    private static final int BYTES = STRING_OFFSET + STRING_LENGTH;

    @Override
    public int encodedLength() {
        return BYTES;
    }

    @Override
    protected StringBuilder append(StringBuilder builder) {
        builder
            .append("{Test, int64=").append(int64())
            .append(", int32=").append(int32())
            .append(", int16=").append(int16())
            .append(", int8=").append(int8())
            .append(", bytes=");
        return append(ARRAY_OFFSET, ARRAY_LENGTH, builder)
            .append(", string=").append(string()).append("}");
    }

    public long int64() {
        return nativeLong(LONG_OFFSET);
    }

    public TestFlyweight int64(long value) {
        nativeLong(LONG_OFFSET, value);
        return this;
    }

    public int int32() {
        return nativeInt(INT_OFFSET);
    }

    public TestFlyweight int32(int value) {
        nativeInt(INT_OFFSET, value);
        return this;
    }

    public int int16() {
        return nativeShort(SHORT_OFFSET);
    }

    public TestFlyweight int16(short value) {
        nativeShort(SHORT_OFFSET, value);
        return this;
    }

    public byte int8() {
        return nativeByte(BYTE_OFFSET);
    }

    public TestFlyweight int8(byte value) {
        nativeByte(BYTE_OFFSET, value);
        return this;
    }

    public byte[] bytes() {
        byte[] value = new byte[ARRAY_LENGTH];
        nativeByteArray(ARRAY_OFFSET, ARRAY_LENGTH, value);
        return value;
    }

    public TestFlyweight bytes(byte[] value) {
        nativeByteArray(value, ARRAY_OFFSET, value.length);
        return this;
    }

    public String string() {
        return nativeString(STRING_OFFSET);
    }

    public TestFlyweight string(String value) {
        nativeString(value, STRING_OFFSET, STRING_LENGTH);
        return this;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        return super.equals(object);
    }
}
