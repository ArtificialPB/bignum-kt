package io.github.artificialpb.bignum.benchmark

import com.ionspin.kotlin.bignum.integer.BigInteger as IonspinBigInteger
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
open class IonspinBigDecimalConversionBenchmark : IonspinBigDecimalProfiledBenchmarkState() {
    @Benchmark
    fun toStringMethod(): String = fixture.left.toString()

    @Benchmark
    fun toPlainStringMethod(): String = fixture.left.toPlainString()

    @Benchmark
    fun toStringExpandedMethod(): String = fixture.left.toStringExpanded()

    @Benchmark
    fun toBigIntegerMethod(): IonspinBigInteger = fixture.left.toBigInteger()

    @Benchmark
    fun toIntMethod(): Int = fixture.left.intValue(false)

    @Benchmark
    fun toLongMethod(): Long = fixture.left.longValue(false)

    @Benchmark
    fun toFloatMethod(): Float = fixture.left.floatValue(false)

    @Benchmark
    fun toDoubleMethod(): Double = fixture.left.doubleValue(false)
}
