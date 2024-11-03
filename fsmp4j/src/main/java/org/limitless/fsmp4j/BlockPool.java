package org.limitless.fsmp4j;

import java.lang.foreign.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * An implementation of the allocator described in the paper:
 * Fast Efficient Fixed-Size Memory Pool
 * @param <T> flyweight type
 *
 * Blocks are allocated in segments and when a segment is full another is allocated.
 *
 */
public class BlockPool<T extends BlockFlyweight> {

    public static final int INVALID_INDEX = -1;

    public static final int SEGMENT_CAPACITY = 64;

    private final int blockLength;
    private final int blocksPerSegment;

    private Arena arena;
    private final Constructor<T> constructor;
    private MemorySegment[] memorySegments;
    private final T workBlock;
    private final FreeBlock freeBlock;

    private int initiatedFreeBlocks;   // number of initiated blocks in the current segment
    private int segmentPosition;   // index of the current used segment
    private int segmentCount;      // allocated segments

    private int freeBlockCount;
    private int freeBlockPosition;
    private int freeSegmentPosition;

    /**
     * Constructor
     * @param memoryArena      the memory arena
     * @param constructor      constructor for the flyweight
     * @param blockLength        the block size (power of 2)
     * @param blocksPerSegment the number of blocks per segment
     * @param preAllocSegments initial number of segments
     */
    private BlockPool(final Arena memoryArena,
                      final Constructor<T> constructor,
                      final int blockLength,
                      final int blocksPerSegment,
                      final int preAllocSegments) {
        this.arena = memoryArena;
        this.constructor = constructor;
        this.blockLength = blockLength;
        this.blocksPerSegment = blocksPerSegment;

        final long segmentSize = (long) this.blocksPerSegment * this.blockLength;
        segmentCount = preAllocSegments;
        segmentPosition = 0;
        memorySegments = new MemorySegment[SEGMENT_CAPACITY];
        for (int position = 0; position < this.segmentCount; ++position) {
            memorySegments[position] = arena.allocate(segmentSize, Long.BYTES);
        }
        freeBlockCount = this.blocksPerSegment;
        freeBlock = new FreeBlock();
        workBlock = newInstance();
    }

    /**
     * The allocated number of bytes in this memory pool.
     * @return bytes
     */
    public long allocatedBytes() {
        return (long) segmentCount * blocksPerSegment * blockLength;
    }

    /**
     * Allocate a new flyweight object and data from the pool
     * @return the wrapped block or null
     * @throws IllegalStateException free list corruption
     */
    public T allocate() {
        try {
            return allocate(constructor.newInstance());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | IllegalArgumentException error) {
            return null;
        }
    }

    /**
     * Allocate a block from the free list (
     * @param block a flyweight
     * @return the wrapped block
     * @throws IllegalArgumentException null argument
     * @throws IllegalStateException free list corruption
     */
    public T allocate(final T block) {
        if (block == null) {
            throw new IllegalArgumentException("null block");
        }
        if (initiatedFreeBlocks < blocksPerSegment) {
            final long offset = (long) initiatedFreeBlocks * blockLength;
            ++initiatedFreeBlocks;
            freeBlock.wrap(memorySegments[segmentPosition], offset).set(segmentPosition, initiatedFreeBlocks);
        }

        // allocate free block
        final MemorySegment segment = memorySegments[freeSegmentPosition];
        block.wrap(segment, freeSegmentPosition, freeBlockPosition);
        block.nativeInt(FreeBlock.COOKIE_OFFSET, 0);

        final long offset = (long) freeBlockPosition * blockLength;
        freeBlock.wrap(segment, offset);  // new free block
        freeBlockPosition = freeBlock.blockIndex();
        freeSegmentPosition = freeBlock.blockSegment();
        if (--freeBlockCount == 0) {
            allocateSegment();
        }
        return block;
    }

    /**
     * Free the block with address
     * @param address block address
     * @throws IllegalArgumentException invalid address
     */
    public void free(final long address) {
        if (address <= 0) {
            throw new IllegalArgumentException("invalid address");
        }

        get(address, workBlock);
        if (workBlock.memorySegment() == null) {
            throw new IllegalStateException("null free segment");
        }
        freeBlock(workBlock.memorySegment(), workBlock);
    }

    /**
     * Free the block
     * @param block a wrapped object
     * @throws IllegalArgumentException invalid block
     * @throws IllegalStateException block has invalid memory address
     */
    public void free(final T block) {
        if (block == null) {
            throw new IllegalArgumentException("null block");
        }

        final MemorySegment segment = block.memorySegment();
        if (segment == null) {
            throw new IllegalStateException("null memory segment");
        }
        freeBlock(segment, block);
    }

    private void freeBlock(final MemorySegment segment, final T block) {
        final int segmentIndex = block.segment();
        if (segment != memorySegments[segmentIndex]) {
            throw new IllegalStateException("block does not belong to this pool");
        }

        final int blockIndex = block.block();
        final long offset = (long) blockIndex * blockLength;
        freeBlock.wrap(segment, offset).set(freeSegmentPosition, freeBlockPosition);
        freeSegmentPosition = segmentIndex;
        freeBlockPosition = blockIndex;
        ++freeBlockCount;
        block.clear();
    }

    /**
     * Allocate a flyweight object.
     * @param address the segment and index for the object
     * @return a wrapped flyweight
     * @throws IllegalArgumentException invalid address
     * @throws IllegalStateException invalid indices
     */
    public T get(final long address) {
        return get(address, allocate());
    }

    /**
     * Wrap the block
     * @param address the segment and index for the block
     * @param block the wrapped block
     * @return the wrapped block
     * @throws IllegalArgumentException invalid address or block
     * @throws IllegalStateException invalid indices
     */
    public T get(final long address, final T block) {
        if (block == null) {
            throw new IllegalArgumentException("null block");
        }

        final int segmentIndex = ByteUtils.highBits(address) - 1;
        final int blockIndex = ByteUtils.lowBits(address);
        block.wrap(memorySegments[segmentIndex], segmentIndex, blockIndex);
        return block;
    }

    /**
     * Close the associated memory arena
     */
    public void close() {
        if (arena != null) {
            arena.close();
            arena = null;
        }
    }

    @Override
    public String toString() {
        return String.format("BlockPool{ size = %d, blocks = %,d, segments = %d, bytes = %,d }",
            blockLength, blocksPerSegment * segmentCount, segmentCount, allocatedBytes());
    }

    /**
     * Allocates a new segment.
     */
    private void allocateSegment() {
        if (++segmentPosition >= segmentCount) {
            if (segmentCount >= memorySegments.length) {
                int newCapacity = memorySegments.length + (memorySegments.length >>> 1);
                memorySegments = Arrays.copyOf(memorySegments, newCapacity);
            }

            final long segmentSize = (long) blocksPerSegment * blockLength;
            memorySegments[segmentCount] = arena.allocate(segmentSize, Long.BYTES);
            ++segmentCount;
        }
        initiatedFreeBlocks = 0;
        freeBlockPosition = 0;
        freeSegmentPosition = segmentPosition;
        freeBlockCount += blocksPerSegment;
    }

    /**
     * Check segment and block indices
     * @param segmentIndex segment index
     * @param blockIndex block index
     * @throws IllegalStateException invalid indices
     */
    private void checkSegmentAndIndex(final int segmentIndex, final int blockIndex) {
        if (segmentIndex < 0 || segmentIndex > segmentPosition) {
            throw new IllegalStateException("block has invalid address");
        }

        final int limit;
        if (segmentIndex == segmentPosition) {
            limit = initiatedFreeBlocks;
        } else {
            limit = blocksPerSegment;
        }
        if (blockIndex >= limit) {
            throw new IllegalStateException("block has invalid address");
        }
    }

    /**
     * Native block pool builder
     * @param <N> flyweight class
     */
    public static final class Builder<N extends BlockFlyweight> {
        private final Arena memoryArena;
        private final Class<N> clazz;
        private int preAllocSegments;
        private int blocksPerSegment;

        /**
         * Native block pool builder
         * @param memoryArena   memory arena
         * @param clazz         native class
         */
        public Builder(final Arena memoryArena, final Class<N> clazz) {
            this.memoryArena = memoryArena;
            this.clazz = clazz;
            preAllocSegments = 1;
        }

        public Builder<N> blocksPerSegment(final int blocks) {
            this.blocksPerSegment = blocks;
            return this;
        }

        public Builder<N> allocatedSegments(final int segments) {
            this.preAllocSegments = segments;
            return this;
        }

        /**
         * Builds a memory pool
         * @return Constructed NativeBlockPool of type N
         * @throws IllegalArgumentException null memory session or flyweight class
         * @throws IllegalStateException failed memory allocation
         */
        public BlockPool<N> build()  {
            if (memoryArena == null || clazz == null) {
                throw new IllegalArgumentException("null memory session or flyweight class");
            }

            final Constructor<N> constructor;
            int blockLength;
            try {
                constructor = clazz.getDeclaredConstructor();
                blockLength = constructor.newInstance().encodedLength();
            } catch (ReflectiveOperationException | RuntimeException error) {
                throw new IllegalArgumentException("flyweight instantiation");
            }

            blockLength = ByteUtils.align(Math.max(FreeBlock.BYTES, blockLength), Long.BYTES);
            if (this.blocksPerSegment <= 0) {
                throw new IllegalArgumentException("invalid allocated segments or blocks");
            }

            final var pool = new BlockPool<>(memoryArena, constructor, blockLength, blocksPerSegment, preAllocSegments);
            if (pool.memorySegments[0] == null) {
                throw new IllegalStateException("segment allocation failed");
            }
            return pool;
        }
    }

    private T newInstance() {
        T newBlock;
        try {
            newBlock = constructor.newInstance();
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            newBlock = null;
        }
        return newBlock;
    }

    // handling of object less than 8
    private static final class FreeBlock {
        public static final int INDEX_OFFSET = 0;
        public static final int INDEX_LENGTH = Integer.BYTES;
        public static final int SEGMENT_OFFSET = INDEX_OFFSET + INDEX_LENGTH;
        public static final int SEGMENT_LENGTH = Integer.BYTES;
        public static final int COOKIE_OFFSET = SEGMENT_OFFSET + SEGMENT_LENGTH;
        public static final int COOKIE_LENGTH = Integer.BYTES;
        public static final int BYTES = COOKIE_OFFSET + COOKIE_LENGTH;

        public static final int COOKIE = 0xdeadbeef;

        private MemorySegment memorySegment;
        private long offset;

        public FreeBlock() {
        }

        public FreeBlock wrap(final MemorySegment memorySegment, final long offset) {
            this.memorySegment = memorySegment;
            this.offset = offset;
            return this;
        }

        public void set(final int segment, final int  block) {
            memorySegment.set(ValueLayout.JAVA_INT, offset + SEGMENT_OFFSET, segment);
            memorySegment.set(ValueLayout.JAVA_INT, offset + INDEX_OFFSET, block);
            memorySegment.set(ValueLayout.JAVA_INT, offset + COOKIE_OFFSET, COOKIE);
        }

        public int blockSegment() {
            return memorySegment.get(ValueLayout.JAVA_INT, offset + SEGMENT_OFFSET);
        }

        public int blockIndex() {
            return memorySegment.get(ValueLayout.JAVA_INT, offset + INDEX_OFFSET);
        }
    }
}
