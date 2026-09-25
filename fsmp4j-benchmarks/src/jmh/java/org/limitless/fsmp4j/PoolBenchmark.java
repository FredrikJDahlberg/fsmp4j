package org.limitless.fsmp4j;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.foreign.Arena;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Steady-state cost of single pool operations on a fixed set of live blocks, next to the
 * equivalent operation on heap objects.
 * <ul>
 *   <li>{@code liveBlocks} 1K fits in the CPU caches, 1M (16 MB) does not.</li>
 *   <li>{@code access} visits the blocks in allocation order or in a shuffled order.</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class PoolBenchmark {

    @Param({"1024", "1048576"})
    public int liveBlocks;

    @Param({"sequential", "random"})
    public String access;

    private Arena arena;
    private BlockPool<Block> pool;
    private Block block;
    private long[] addresses;
    private HeapBlock[] heapBlocks;
    private int mask;
    private int position;

    @Setup(Level.Trial)
    public void setup() {
        if (Integer.bitCount(liveBlocks) != 1) {
            throw new IllegalArgumentException("liveBlocks must be a power of two: " + liveBlocks);
        }
        arena = Arena.ofConfined();
        pool = BlockPool.builder(arena, Block::new).blocksPerSegment(64 * 1024).build();
        block = new Block();
        addresses = new long[liveBlocks];
        heapBlocks = new HeapBlock[liveBlocks];
        for (int i = 0; i < liveBlocks; ++i) {
            addresses[i] = pool.allocate(block).int64(i).address();
            heapBlocks[i] = new HeapBlock().int64(i);
        }
        switch (access) {
            case "sequential" -> { }
            case "random" -> shuffle(new Random(42));
            default -> throw new IllegalArgumentException("unknown access: " + access);
        }
        mask = liveBlocks - 1;
        position = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        arena.close();
    }

    private void shuffle(final Random random) {
        for (int i = liveBlocks - 1; i > 0; --i) {
            final int j = random.nextInt(i + 1);
            final long address = addresses[i];
            addresses[i] = addresses[j];
            addresses[j] = address;
            final HeapBlock heapBlock = heapBlocks[i];
            heapBlocks[i] = heapBlocks[j];
            heapBlocks[j] = heapBlock;
        }
    }

    private int next() {
        position = (position + 1) & mask;
        return position;
    }

    /** Read a field of a block through its address */
    @Benchmark
    public long get() {
        return pool.get(addresses[next()], block).int64();
    }

    /** Read a field of a heap object through its reference */
    @Benchmark
    public long heapGet() {
        return heapBlocks[next()].int64();
    }

    /** Read-modify-write a field of a block through its address */
    @Benchmark
    public long update() {
        pool.get(addresses[next()], block);
        final long value = block.int64() + 1;
        block.int64(value);
        return value;
    }

    /** Read-modify-write a field of a heap object */
    @Benchmark
    public long heapUpdate() {
        final HeapBlock heapBlock = heapBlocks[next()];
        final long value = heapBlock.int64() + 1;
        heapBlock.int64(value);
        return value;
    }

    /** Replace a live block: free it and allocate a new one, reusing the flyweight */
    @Benchmark
    public long freeAllocate() {
        final int index = next();
        pool.free(addresses[index]);
        final long address = pool.allocate(block).int64(index).address();
        addresses[index] = address;
        return address;
    }

    /** Replace a live block, creating a new flyweight for each allocation */
    @Benchmark
    public Block freeAllocateNewFlyweight() {
        final int index = next();
        pool.free(addresses[index]);
        final Block allocated = pool.allocate().int64(index);
        addresses[index] = allocated.address();
        return allocated;
    }

    /** Replace a live heap object, leaving the old one to the garbage collector */
    @Benchmark
    public HeapBlock heapReplace() {
        final int index = next();
        final HeapBlock heapBlock = new HeapBlock().int64(index);
        heapBlocks[index] = heapBlock;
        return heapBlock;
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(PoolBenchmark.class.getSimpleName()).build()).run();
    }
}
