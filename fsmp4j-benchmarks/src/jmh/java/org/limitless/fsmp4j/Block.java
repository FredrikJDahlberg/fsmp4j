package org.limitless.fsmp4j;

public class Block extends BlockFlyweight {

    private static final int LONG_OFFSET = 0;
    private static final int LONG_LENGTH = Long.BYTES;
    private static final int BYTES = LONG_OFFSET + LONG_LENGTH;

    @Override
    public int encodedLength() {
        return BYTES;
    }

    @Override
    protected StringBuilder append(StringBuilder builder) {
        return builder.append("{Test, int64=").append(int64()).append("}");
    }

    public long int64() {
        return nativeLong(LONG_OFFSET);
    }

    public BlockFlyweight int64(long value) {
        nativeLong(LONG_OFFSET, value);
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

