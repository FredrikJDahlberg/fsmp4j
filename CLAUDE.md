# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

Requires Java 23.

```bash
# Full clean build and test
./gradlew

# Run tests only
./gradlew :fsmp4j:test

# Run a single test class
./gradlew :fsmp4j:test --tests "org.limitless.fsmp4j.BlockPoolTest"

# Run JMH benchmarks (all, or a regex subset)
./gradlew jmh
./gradlew jmh -Pbenchmarks='PoolBenchmark.get'
```

## Project Structure

Three subprojects:
- `fsmp4j/` — the library (published as `org.limitless:fsmp4j:1.0.1`)
- `fsmp4j-benchmarks/` — JMH benchmarks that depend on the library
- `fsmp4j-examples/` — runnable examples (`./gradlew :fsmp4j-examples:run`)

## Architecture

This is a Java implementation of the [Fast Efficient Fixed-Size Memory Pool](https://arxiv.org/pdf/2210.16471) paper, using the Java Foreign Function & Memory API (`java.lang.foreign`).

**Core abstractions:**

- `BlockPool<T>` — the pool itself. Memory is divided into fixed-size *segments*, each holding `blocksPerSegment` blocks. Segments start at capacity 64 (`SEGMENT_CAPACITY`) and grow by doubling. The free list is an intrusive linked list embedded directly in unallocated blocks via the internal `FreeBlock` struct: one 8-byte word holding the next free (segment, block) packed like an address and XORed with a magic constant. A block is considered free (double-free detection) when that word decodes to a valid (segment, block) pair; `allocate` clears it to 0. Minimum block size is therefore 8 bytes.

- `BlockFlyweight` (abstract) — a zero-copy cursor over off-heap memory. A flyweight holds a `MemorySegment` reference plus `segmentIndex` + `blockIndex`. All field access goes through typed `native*()` methods which compute the absolute offset as `blockIndex * encodedLength() + fieldOffset`. Subclasses define their layout by implementing `encodedLength()` and field accessors.

- `Flyweight` (interface) — the contract: `wrap()`, `block()`, `segment()`, `encodedLength()`, `address()`, `memorySegment()`.

- `ByteUtils` — packs/unpacks a (segment, block) pair into a single `long` address: high 32 bits = `segmentIndex + 1`, low 32 bits = `blockIndex`.

**Creating a custom block type:**

Extend `BlockFlyweight`, declare field offsets as constants, implement `encodedLength()` returning total byte size, and expose typed accessors via the inherited `native*()` methods. The block size is automatically aligned to `Long.BYTES` by the builder. Field access uses unaligned layouts, so fields may be packed at any offset. Overriding `append(StringBuilder)` is optional (it drives `toString()`).

**Pool lifecycle:**

```java
try (Arena arena = Arena.ofConfined()) {
    BlockPool<MyBlock> pool = BlockPool.builder(arena, MyBlock::new)
        .blocksPerSegment(1024)   // optional, default DEFAULT_BLOCKS_PER_SEGMENT
        .allocatedSegments(4)     // optional, default 1
        .build();
    MyBlock block = pool.allocate();
    long addr = block.address();
    pool.free(block);
}
```

`BlockPool` is `AutoCloseable`; `close()` closes the `Arena` (ignored for global/auto arenas), so close either the pool or the arena, not both. The factory must return a new instance per call (checked in `build()`). `new BlockPool.Builder<>(arena, MyBlock.class)` still works via reflection on the no-arg constructor.
