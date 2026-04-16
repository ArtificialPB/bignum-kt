package io.github.artificialpb.bignum.benchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
open class IonspinConversionBenchmark : IonspinProfiledBenchmarkState() {
    @Benchmark
    fun toByteArrayMethod(): ByteArray = fixture.left.toByteArray()

    @Benchmark
    fun toIntMethod(): Int = fixture.left.intValue(false)

    @Benchmark
    fun toLongMethod(): Long = fixture.left.longValue(false)

    @Benchmark
    fun toDoubleMethod(): Double = fixture.left.doubleValue(false)

    @Benchmark
    fun toStringMethod(): String = fixture.left.toString(10)

    @Benchmark
    fun toStringRadixMethod(): String = fixture.left.toString(16)

    @Benchmark
    fun signumMethod(): Int = fixture.negativeLeft.signum()
}
