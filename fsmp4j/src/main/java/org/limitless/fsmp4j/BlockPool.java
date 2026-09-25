package org.limitless.fsmp4j;

import java.lang.foreign.*;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * An implementation of the allocator described in the paper:
 * Fast Efficient Fixed-Size Memory Pool
 * @param <T> flyweight type
 *
 * Blocks are allocated in segments and when a segment is full another is allocated.
 * <pre>{@code
 * try (Arena arena = Arena.ofConfined()) {
 *     BlockPool<MyBlock> pool = BlockPool.builder(arena, MyBlock::new).build();
 *     MyBlock block = pool.allocate();
 *     pool.free(block);
 * }
 * }</pre>
 * Closing the pool closes its arena, so close either the pool or the arena, not both.
 */
public class BlockPool<T extends BlockFlyweight> implements AutoCloseable {

    public static final int INVALID_INDEX = -1;

    public static final int SEGMENT_CAPACITY = 64;

    /** Blocks per segment when {@link Builder#blocksPerSegment(int)} is not called */
    public static final int DEFAULT_BLOCKS_PER_SEGMENT = 1024;

    private final int blockLength;
    private final int blocksPerSegment;

    private Arena arena;
    private final Supplier<T> factory;
    private MemorySegment[] memorySegments;
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
     * @param factory          creates new flyweights
     * @param blockLength        the block size (power of 2)
     * @param blocksPerSegment the number of blocks per segment
     * @param preAllocSegments initial number of segments
     */
    private BlockPool(final Arena memoryArena,
                      final Supplier<T> factory,
                      final int blockLength,
                      final int blocksPerSegment,
                      final int preAllocSegments) {
        this.arena = memoryArena;
        this.factory = factory;
        this.blockLength = blockLength;
        this.blocksPerSegment = blocksPerSegment;

        final long segmentSize = (long) this.blocksPerSegment * this.blockLength;
        segmentCount = preAllocSegments;
        segmentPosition = 0;
        memorySegments = new MemorySegment[Math.max(SEGMENT_CAPACITY, preAllocSegments)];
        for (int position = 0; position < this.segmentCount; ++position) {
            memorySegments[position] = arena.allocate(segmentSize, Long.BYTES);
        }
        freeBlockCount = this.blocksPerSegment;
        freeBlock = new FreeBlock();
    }

    /**
     * Create a pool builder using a factory for the flyweight, e.g. {@code BlockPool.builder(arena, MyBlock::new)}
     * @param memoryArena memory arena
     * @param factory     creates a new flyweight instance on each call
     * @return builder
     * @param <N> flyweight type
     */
    public static <N extends BlockFlyweight> Builder<N> builder(final Arena memoryArena, final Supplier<N> factory) {
        return new Builder<>(memoryArena, null, factory);
    }

    /**
     * The allocated number of bytes in this memory pool.
     * @return bytes
     */
    public long allocatedBytes() {
        return (long) segmentCount * blocksPerSegment * blockLength;
    }

    /**
     * The number of blocks currently allocated (not freed).
     * @return blocks in use
     */
    public long blocksInUse() {
        return (long) (segmentPosition + 1) * blocksPerSegment - freeBlockCount;
    }

    /** Largest supported {@link BlockFlyweight#encodedLength()}, so that the aligned block length fits an int */
    public static final int MAX_ENCODED_LENGTH = Integer.MAX_VALUE & -Long.BYTES;

    /**
     * The size of a block in the pool: the encoded length padded to hold a free list entry and aligned to 8 bytes.
     * @param encodedLength flyweight encoded length
     * @return block length
     */
    static int blockLength(final int encodedLength) {
        return ByteUtils.align(Math.max(FreeBlock.BYTES, encodedLength), Long.BYTES);
    }

    /**
     * The size of a block in bytes, including alignment padding.
     * @return block length
     */
    public int blockLength() {
        return blockLength;
    }

    /**
     * Allocate a new flyweight object and data from the pool.
     * Use {@link #allocate(BlockFlyweight)} with a reused flyweight to avoid creating garbage.
     * @return the wrapped block
     * @throws IllegalStateException free list corruption or flyweight instantiation failure
     */
    public T allocate() {
        return allocate(factory.get());
    }

    /**
     * Allocate a block from the free list (
     * @param block a flyweight
     * @return the wrapped block
     * @throws IllegalArgumentException null block or block size differs from the pool's
     * @throws IllegalStateException free list corruption
     */
    public T allocate(final T block) {
        checkBlock(block);
        if (initiatedFreeBlocks < blocksPerSegment) {
            final long offset = (long) initiatedFreeBlocks * blockLength;
            ++initiatedFreeBlocks;
            freeBlock.wrap(memorySegments[segmentPosition], offset).set(segmentPosition, initiatedFreeBlocks);
        }

        // allocate free block
        final int segmentIndex = freeSegmentPosition;
        final int blockIndex = freeBlockPosition;
        final MemorySegment segment = memorySegments[segmentIndex];
        final long offset = (long) blockIndex * blockLength;
        final long next = freeBlock.wrap(segment, offset).next();  // new free block
        freeBlock.clear();
        freeBlockPosition = ByteUtils.lowBits(next);
        freeSegmentPosition = ByteUtils.highBits(next);
        block.wrap(segment, segmentIndex, blockIndex, offset);
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

        final int segmentIndex = ByteUtils.highBits(address) - 1;
        final int blockIndex = ByteUtils.lowBits(address);
        checkSegmentAndIndex(segmentIndex, blockIndex);
        freeBlock(memorySegments[segmentIndex], segmentIndex, blockIndex);
    }

    /**
     * Free the block
     * @param block a wrapped object
     * @throws IllegalArgumentException null block or block size differs from the pool's
     * @throws IllegalStateException block has invalid memory address
     */
    public void free(final T block) {
        checkBlock(block);

        final MemorySegment segment = block.memorySegment();
        if (segment == null) {
            throw new IllegalStateException("null memory segment");
        }
        final int segmentIndex = block.segment();
        final int blockIndex = block.block();
        checkSegmentAndIndex(segmentIndex, blockIndex);
        if (segment != memorySegments[segmentIndex]) {
            throw new IllegalStateException("block does not belong to this pool");
        }
        freeBlock(segment, segmentIndex, blockIndex);
        block.clear();
    }

    private void freeBlock(final MemorySegment segment, final int segmentIndex, final int blockIndex) {
        final long offset = (long) blockIndex * blockLength;
        if (isFreeListEntry(freeBlock.wrap(segment, offset).next())) {
            throw new IllegalStateException("double free");
        }
        freeBlock.set(freeSegmentPosition, freeBlockPosition);
        freeSegmentPosition = segmentIndex;
        freeBlockPosition = blockIndex;
        ++freeBlockCount;
    }

    /**
     * Wrap the block at address in a new flyweight object.
     * Use {@link #get(long, BlockFlyweight)} with a reused flyweight to avoid creating garbage.
     * @param address the segment and index for the object
     * @return a wrapped flyweight
     * @throws IllegalStateException invalid indices
     */
    public T get(final long address) {
        return get(address, factory.get());
    }

    /**
     * Wrap the block
     * @param address the segment and index for the block
     * @param block the wrapped block
     * @return the wrapped block
     * @throws IllegalArgumentException null block or block size differs from the pool's
     * @throws IllegalStateException invalid indices
     */
    public T get(final long address, final T block) {
        checkBlock(block);

        final int segmentIndex = ByteUtils.highBits(address) - 1;
        final int blockIndex = ByteUtils.lowBits(address);
        checkSegmentAndIndex(segmentIndex, blockIndex);
        block.wrap(memorySegments[segmentIndex], segmentIndex, blockIndex, (long) blockIndex * blockLength);
        return block;
    }

    /**
     * Close the associated memory arena. Global and automatic arenas cannot be closed and are left open.
     */
    @Override
    public void close() {
        if (arena != null) {
            try {
                arena.close();
            } catch (UnsupportedOperationException error) {
                // global and automatic arenas are released by the JVM
            }
            arena = null;
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "BlockPool{ size = %d, blocks = %,d, segments = %d, bytes = %,d }",
            blockLength, blocksPerSegment * segmentCount, segmentCount, allocatedBytes());
    }

    /**
     * Allocates a new segment.
     */
    private void allocateSegment() {
        if (++segmentPosition >= segmentCount) {
            if (segmentCount >= memorySegments.length) {
                memorySegments = Arrays.copyOf(memorySegments, memorySegments.length << 1);
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
     * Check that the flyweight lays out blocks with the same stride as the pool
     * @param block flyweight
     * @throws IllegalArgumentException null block or block size differs from the pool's
     */
    private void checkBlock(final T block) {
        if (block == null) {
            throw new IllegalArgumentException("null block");
        }
        if (blockLength(block.encodedLength()) != blockLength) {
            throw new IllegalArgumentException("block size " + block.encodedLength() +
                " does not fit the pool's block length " + blockLength);
        }
    }

    /**
     * Whether a decoded free list entry points at a block in this pool, i.e. the block holding it is free
     * @param next decoded entry
     * @return true if valid
     */
    private boolean isFreeListEntry(final long next) {
        final int segmentIndex = ByteUtils.highBits(next);
        final int blockIndex = ByteUtils.lowBits(next);
        return segmentIndex >= 0 && segmentIndex < segmentCount && blockIndex >= 0 && blockIndex <= blocksPerSegment;
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
        private final Supplier<N> factory;
        private int preAllocSegments;
        private int blocksPerSegment;

        /**
         * Native block pool builder
         * @param memoryArena   memory arena
         * @param clazz         native class
         * @see BlockPool#builder(Arena, Supplier)
         */
        public Builder(final Arena memoryArena, final Class<N> clazz) {
            this(memoryArena, clazz, null);
        }

        private Builder(final Arena memoryArena, final Class<N> clazz, final Supplier<N> factory) {
            this.memoryArena = memoryArena;
            this.clazz = clazz;
            this.factory = factory;
            preAllocSegments = 1;
            blocksPerSegment = DEFAULT_BLOCKS_PER_SEGMENT;
        }

        /**
         * Number of blocks in each segment, defaults to {@value #DEFAULT_BLOCKS_PER_SEGMENT}
         * @param blocks blocks per segment
         * @return builder
         */
        public Builder<N> blocksPerSegment(final int blocks) {
            this.blocksPerSegment = blocks;
            return this;
        }

        /**
         * Number of segments allocated up front, defaults to 1
         * @param segments pre-allocated segments
         * @return builder
         */
        public Builder<N> allocatedSegments(final int segments) {
            this.preAllocSegments = segments;
            return this;
        }

        /**
         * Builds a memory pool
         * @return Constructed BlockPool of type N
         * @throws IllegalArgumentException invalid arguments or flyweight cannot be instantiated
         * @throws IllegalStateException the arena is closed
         * @throws OutOfMemoryError failed memory allocation
         */
        public BlockPool<N> build()  {
            if (memoryArena == null) {
                throw new IllegalArgumentException("null memory arena");
            }
            if (clazz == null && factory == null) {
                throw new IllegalArgumentException("null flyweight class or factory");
            }
            if (blocksPerSegment <= 0) {
                throw new IllegalArgumentException("blocks per segment must be positive: " + blocksPerSegment);
            }
            if (preAllocSegments <= 0) {
                throw new IllegalArgumentException("allocated segments must be positive: " + preAllocSegments);
            }

            final Supplier<N> newBlock = factory != null ? factory : reflectiveFactory(clazz);
            final N prototype;
            final N other;
            try {
                prototype = newBlock.get();
                other = newBlock.get();
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("flyweight instantiation failed", error);
            }
            if (prototype == null || other == null) {
                throw new IllegalArgumentException("flyweight factory returned null");
            }
            if (prototype == other) {
                throw new IllegalArgumentException("flyweight factory must return a new instance on each call");
            }
            final int encodedLength = prototype.encodedLength();
            if (encodedLength <= 0) {
                throw new IllegalArgumentException("encodedLength must be positive: " + encodedLength);
            }
            if (encodedLength > MAX_ENCODED_LENGTH) {
                throw new IllegalArgumentException("encodedLength is too large: " + encodedLength);
            }

            final int blockLength = blockLength(encodedLength);
            return new BlockPool<>(memoryArena, newBlock, blockLength, blocksPerSegment, preAllocSegments);
        }
    }

    private static <N> Supplier<N> reflectiveFactory(final Class<N> clazz) {
        final Constructor<N> constructor;
        try {
            constructor = clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException error) {
            throw new IllegalArgumentException(clazz.getName() + " needs a no-argument constructor", error);
        }
        return () -> {
            try {
                return constructor.newInstance();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("cannot instantiate " + clazz.getName(), error);
            }
        };
    }

    // Free list entry stored in the first 8 bytes of a free block. Reading it once tells both where the next
    // free block is and whether the block is free, which keeps free and allocate to one read and one write.
    private static final class FreeBlock {
        // next free block, segment in the high bits and block in the low, xor MAGIC so that
        // user data is unlikely to decode as a valid free list entry
        public static final int NEXT_OFFSET = 0;
        public static final int BYTES = Long.BYTES;

        // random, so that well-known constants stored in a block do not decode as a free list entry
        private static final long MAGIC = 0xf49bafd7105a8d35L;

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
            memorySegment.set(ValueLayout.JAVA_LONG_UNALIGNED, offset + NEXT_OFFSET, ByteUtils.pack(segment, block) ^ MAGIC);
        }

        public long next() {
            return memorySegment.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + NEXT_OFFSET) ^ MAGIC;
        }

        public void clear() {
            memorySegment.set(ValueLayout.JAVA_LONG_UNALIGNED, offset + NEXT_OFFSET, 0L);
        }
    }
}
