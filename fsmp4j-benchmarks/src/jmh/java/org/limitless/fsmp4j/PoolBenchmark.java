package org.limitless.fsmp4j;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.foreign.Arena;
import java.util.concurrent.TimeUnit;

@Fork(value = 5)
@Warmup(iterations = 5, batchSize = 10_000_000)
@Measurement(iterations = 5, batchSize = 10_000_000)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
public class PoolBenchmark {

    private static final int COUNT = 10_000_000;

    @State(Scope.Benchmark)
    public static class PoolState {
        public BlockPool<Block> pool;
        public Block block;

        @Setup(Level.Iteration)
        public void setup() {
            pool = new BlockPool.Builder<>(Arena.ofShared(), Block.class).blocksPerSegment(1024 * 1024).build();
            block = new Block();
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            pool.close();
        }
    }

    @State(Scope.Benchmark)
    public static class AddressState extends PoolState {
        public long[] addresses = new long[COUNT];
        public int count;

        @Setup(Level.Iteration)  // optional annotation, use to set up a state object
        public void setup() {
            super.setup();
            count = 0;
            for (int i = 0; i < addresses.length; ++i) {
                addresses[i] = pool.allocate(block).address();
            }
        }

        @TearDown(Level.Iteration) // optional annotation, use to clean up resources
        public void tearDown() {
            count = 0;
            pool.close();
        }
    }

    @Benchmark
    public void baseline() {
        // baseline
    }

    @Benchmark
    public void allocateNewBlock(PoolState state, Blackhole bh) {
        bh.consume(state.pool.allocate());
    }

    @Benchmark
    public void allocatedWithBlock(PoolState state, Blackhole bh) {
        bh.consume(state.pool.allocate(state.block));
    }

    @Benchmark
    public void getBlock(AddressState state, Blackhole bh) {
        bh.consume(state.pool.get(state.addresses[state.count++], state.block));
    }

    @Benchmark
    public void updateBlock(AddressState state, Blackhole bh) {
        state.pool.get(state.addresses[state.count++], state.block);
        state.block.int64(state.block.int64() + 1);
        bh.consume(state.block);
    }

    @Benchmark
    public void freeBlock(AddressState state, Blackhole bh) {
        long address = state.addresses[state.count++];
        state.pool.free(address);
        bh.consume(address);
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(PoolBenchmark.class.getSimpleName()).build()).run();
    }
}
