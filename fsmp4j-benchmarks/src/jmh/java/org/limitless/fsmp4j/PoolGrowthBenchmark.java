package org.limitless.fsmp4j;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.foreign.Arena;
import java.util.concurrent.TimeUnit;

/**
 * Cost per block of filling an empty pool with {@link #COUNT} blocks, including allocating and zeroing
 * new segments, next to allocating the same number of heap objects.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@OperationsPerInvocation(PoolGrowthBenchmark.COUNT)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(2)
@State(Scope.Thread)
public class PoolGrowthBenchmark {

    static final int COUNT = 1_000_000;

    @Param({"1024", "65536"})
    public int blocksPerSegment;

    private Arena arena;
    private BlockPool<Block> pool;
    private Block block;
    private HeapBlock[] heapBlocks;

    @Setup(Level.Iteration)
    public void setup() {
        arena = Arena.ofConfined();
        pool = BlockPool.builder(arena, Block::new).blocksPerSegment(blocksPerSegment).build();
        block = new Block();
        heapBlocks = new HeapBlock[COUNT];
    }

    @TearDown(Level.Iteration)
    public void tearDown() {
        arena.close();
    }

    /** Fill the pool, reusing one flyweight */
    @Benchmark
    public long allocate() {
        long address = 0;
        for (int i = 0; i < COUNT; ++i) {
            address = pool.allocate(block).int64(i).address();
        }
        return address;
    }

    /** Fill the pool, creating a new flyweight for each block */
    @Benchmark
    public Block allocateNewFlyweight() {
        Block allocated = null;
        for (int i = 0; i < COUNT; ++i) {
            allocated = pool.allocate().int64(i);
        }
        return allocated;
    }

    /** Allocate the same number of heap objects and keep them reachable */
    @Benchmark
    public HeapBlock[] heapAllocate() {
        final HeapBlock[] blocks = heapBlocks;
        for (int i = 0; i < COUNT; ++i) {
            blocks[i] = new HeapBlock().int64(i);
        }
        return blocks;
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(PoolGrowthBenchmark.class.getSimpleName()).build()).run();
    }
}
