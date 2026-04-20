package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigDecimal
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
open class BigDecimalComparisonBenchmark : BigDecimalProfiledBenchmarkState() {
    @Benchmark
    fun compareToMethod(): Int = fixture.left.compareTo(fixture.compareTarget)

    @Benchmark
    fun equalsMethod(): Boolean = fixture.left.equals(fixture.equalLeft)

    @Benchmark
    fun hashCodeMethod(): Int = fixture.left.hashCode()

    @Benchmark
    fun minMethod(): BigDecimal = fixture.left.min(fixture.compareTarget)

    @Benchmark
    fun maxMethod(): BigDecimal = fixture.left.max(fixture.compareTarget)
}
