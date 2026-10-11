package org.limitless.fsmp4j.examples;

import org.limitless.fsmp4j.BlockFlyweight;

/**
 * An order stored off-heap in a {@link org.limitless.fsmp4j.BlockPool}.
 */
public class Order extends BlockFlyweight {

    public static final int SYMBOL_LENGTH = 8;

    private static final int ID_OFFSET = 0;
    private static final int PRICE_OFFSET = ID_OFFSET + Long.BYTES;
    private static final int QUANTITY_OFFSET = PRICE_OFFSET + Double.BYTES;
    private static final int BUY_OFFSET = QUANTITY_OFFSET + Integer.BYTES;
    private static final int SYMBOL_OFFSET = BUY_OFFSET + 1;
    private static final int BYTES = SYMBOL_OFFSET + SYMBOL_LENGTH;

    @Override
    public int encodedLength() {
        return BYTES;
    }

    public long id() {
        return nativeLong(ID_OFFSET);
    }

    public Order id(long value) {
        nativeLong(ID_OFFSET, value);
        return this;
    }

    public double price() {
        return nativeDouble(PRICE_OFFSET);
    }

    public Order price(double value) {
        nativeDouble(PRICE_OFFSET, value);
        return this;
    }

    public int quantity() {
        return nativeInt(QUANTITY_OFFSET);
    }

    public Order quantity(int value) {
        nativeInt(QUANTITY_OFFSET, value);
        return this;
    }

    public boolean buy() {
        return nativeBoolean(BUY_OFFSET);
    }

    public Order buy(boolean value) {
        nativeBoolean(BUY_OFFSET, value);
        return this;
    }

    public String symbol() {
        return nativeString(SYMBOL_OFFSET);
    }

    /**
     * @param value at most {@code SYMBOL_LENGTH - 1} bytes, the last byte holds the terminator
     */
    public Order symbol(String value) {
        nativeString(value, SYMBOL_OFFSET, SYMBOL_LENGTH);
        return this;
    }

    @Override
    protected StringBuilder append(StringBuilder builder) {
        return builder.append("Order{id=").append(id())
            .append(", ").append(buy() ? "BUY " : "SELL ").append(quantity())
            .append(' ').append(symbol())
            .append(" @ ").append(price()).append('}');
    }
}
