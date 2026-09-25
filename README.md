Fast Efficient Fixed-Size Memory Pool For Java
==============================================

[![CI](https://github.com/FredrikJDahlberg/fsmp4j/actions/workflows/ci.yml/badge.svg)](https://github.com/FredrikJDahlberg/fsmp4j/actions/workflows/ci.yml)
[![Publish](https://github.com/FredrikJDahlberg/fsmp4j/actions/workflows/publish.yml/badge.svg)](https://github.com/FredrikJDahlberg/fsmp4j/actions/workflows/publish.yml)
[![Java](https://img.shields.io/badge/Java-23-blue)](https://openjdk.org/projects/jdk/23/)
[![License](https://img.shields.io/github/license/FredrikJDahlberg/fsmp4j)](LICENSE)

fsmp4j implements a fast and efficient fixed-size memory pool using the Foreign Function and Memory API as described in the paper [Fast Efficient Fixed-Size Memory Pool](https://arxiv.org/pdf/2210.16471).

Usage
-----

### Dependency

fsmp4j is published to [GitHub Packages](https://github.com/FredrikJDahlberg/fsmp4j/packages):

```groovy
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/FredrikJDahlberg/fsmp4j')
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'org.limitless:fsmp4j:<version>'
}
```

### Define a block

A block is a flyweight over off-heap memory. Extend `BlockFlyweight`, lay out the fields as byte offsets,
return the total size from `encodedLength()` and expose accessors built on the protected `native*()` methods
(`nativeByte`, `nativeBoolean`, `nativeShort`, `nativeChar`, `nativeInt`, `nativeFloat`, `nativeLong`,
`nativeDouble`, `nativeByteArray` and `nativeString`). Fields can be packed at any offset.

```java
public class Order extends BlockFlyweight {
    private static final int ID_OFFSET = 0;
    private static final int PRICE_OFFSET = ID_OFFSET + Long.BYTES;
    private static final int QUANTITY_OFFSET = PRICE_OFFSET + Double.BYTES;
    private static final int BYTES = QUANTITY_OFFSET + Integer.BYTES;

    @Override
    public int encodedLength() {
        return BYTES;
    }

    public long id() { return nativeLong(ID_OFFSET); }
    public Order id(long value) { nativeLong(ID_OFFSET, value); return this; }

    public double price() { return nativeDouble(PRICE_OFFSET); }
    public Order price(double value) { nativeDouble(PRICE_OFFSET, value); return this; }

    public int quantity() { return nativeInt(QUANTITY_OFFSET); }
    public Order quantity(int value) { nativeInt(QUANTITY_OFFSET, value); return this; }
}
```

Override `append(StringBuilder)` to control what `toString()` prints.

### Use the pool

```java
try (Arena arena = Arena.ofConfined()) {
    BlockPool<Order> pool = BlockPool.builder(arena, Order::new)
        .blocksPerSegment(4096)   // optional, defaults to 1024
        .build();
    Order order = pool.allocate().id(1).price(99.5).quantity(10);

    long address = order.address();      // a compact handle, e.g. to store in a map
    Order same = pool.get(address);      // wrap the block again later

    pool.free(order);                    // or pool.free(address)
}
```

`allocate()` and `get(address)` create a new flyweight on each call. On hot paths, reuse one instead:

```java
Order cursor = new Order();
pool.allocate(cursor).id(2);
pool.get(address, cursor);
```

Closing the arena releases all blocks. `BlockPool` is also `AutoCloseable` and closing it closes its arena, so
close one or the other, not both. The factory passed to `builder` must return a new flyweight on each call.
A pool is not thread-safe.

Build
-----

### Java Build

Build the project with [Gradle](http://gradle.org/) using this [build.gradle](https://github.com/fredrikjdahlberg/fsmp4j/blob/main/build.gradle) file.

You require the following to build fsmp4j

* The Latest release of Java 23. fsmp4j is tested with Java 23.

Full clean and build:

    $ ./gradlew

Benchmarks
----------

### Jmh

Run all benchmarks (about 10 minutes):

    $ ./gradlew jmh

Run a subset by passing a regular expression:

    $ ./gradlew jmh -Pbenchmarks='PoolBenchmark.get'

Results are printed and saved to `fsmp4j-benchmarks/build/results/jmh/results.json`.

* `PoolBenchmark` measures single operations (`get`, `update`, `freeAllocate`) on a pool with a fixed number
  of live blocks, next to the same operation on heap objects (`heapGet`, `heapUpdate`, `heapReplace`).
  It runs with 1K blocks (fits in cache) and 1M blocks (does not), accessed sequentially or in random order.
* `PoolGrowthBenchmark` measures the cost per block of filling an empty pool with one million blocks, including
  allocating new segments, next to allocating one million heap objects.

License
-------

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full text, and
<https://www.apache.org/licenses/LICENSE-2.0> for the canonical copy. Copyright is recorded in
[NOTICE](NOTICE); §4d obliges anyone redistributing fsmp4j to carry that file forward. Both files
ship inside the jar under `META-INF/`.
