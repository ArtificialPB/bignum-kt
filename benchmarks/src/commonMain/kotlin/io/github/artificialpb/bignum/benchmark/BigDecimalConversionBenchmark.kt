package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.toDouble
import io.github.artificialpb.bignum.toFloat
import io.github.artificialpb.bignum.toInt
import io.github.artificialpb.bignum.toLong
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
open class BigDecimalConversionBenchmark : BigDecimalProfiledBenchmarkState() {
    @Benchmark
    fun toStringMethod(): String = fixture.left.toString()

    @Benchmark
    fun toEngineeringStringMethod(): String = fixture.left.toEngineeringString()

    @Benchmark
    fun toPlainStringMethod(): String = fixture.left.toPlainString()

    @Benchmark
    fun toBigIntegerMethod() = fixture.left.toBigInteger()

    @Benchmark
    fun toIntMethod(): Int = fixture.left.toInt()

    @Benchmark
    fun toLongMethod(): Long = fixture.left.toLong()

    @Benchmark
    fun toFloatMethod(): Float = fixture.left.toFloat()

    @Benchmark
    fun toDoubleMethod(): Double = fixture.left.toDouble()

    @Benchmark
    fun toBigIntegerExactMethod() = fixture.left.setScale(0).toBigIntegerExact()
}
