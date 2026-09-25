package org.limitless.fsmp4j;

/**
 * On-heap equivalent of {@link Block}, the baseline the pool is compared against.
 */
public final class HeapBlock {

    private long int64;

    public long int64() {
        return int64;
    }

    public HeapBlock int64(long value) {
        int64 = value;
        return this;
    }
}
