package org.limitless.fsmp4j;

import java.lang.foreign.MemorySegment;

public interface Flyweight {

    void wrap(final MemorySegment memoryAddress, final int segment, final int block);

    int block();

    int segment();

    int encodedLength();

    long address();

    MemorySegment memorySegment();
}
