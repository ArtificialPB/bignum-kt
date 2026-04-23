package io.github.artificialpb.bignum.benchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import com.ionspin.kotlin.bignum.decimal.BigDecimal as IonspinBigDecimal

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
open class IonspinBigDecimalComparisonBenchmark : IonspinBigDecimalProfiledBenchmarkState() {
    @Benchmark
    fun compareToMethod(): Int = fixture.left.compareTo(fixture.compareTarget)

    @Benchmark
    fun equalsMethod(): Boolean = fixture.left.equals(fixture.equalLeft)

    @Benchmark
    fun hashCodeMethod(): Int = fixture.left.hashCode()

    @Benchmark
    fun minMethod(): IonspinBigDecimal = if (fixture.left.compareTo(fixture.compareTarget) <= 0) fixture.left else fixture.compareTarget

    @Benchmark
    fun maxMethod(): IonspinBigDecimal = if (fixture.left.compareTo(fixture.compareTarget) >= 0) fixture.left else fixture.compareTarget
}
