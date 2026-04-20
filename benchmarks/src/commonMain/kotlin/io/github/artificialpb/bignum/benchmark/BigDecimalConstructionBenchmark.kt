package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.bigDecimalOf
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
open class BigDecimalConstructionBenchmark : BigDecimalProfiledBenchmarkState() {
    @Benchmark
    fun constructorString(): BigDecimal = BigDecimal(fixture.constructorString)

    @Benchmark
    fun constructorBigInteger(): BigDecimal = BigDecimal(fixture.constructorBigInteger)

    @Benchmark
    fun constructorBigIntegerScale(): BigDecimal = BigDecimal(
        fixture.constructorBigInteger,
        fixture.constructorScale,
    )

    @Benchmark
    fun factoryString(): BigDecimal = bigDecimalOf(fixture.factoryString)

    @Benchmark
    fun factoryInt(): BigDecimal = bigDecimalOf(fixture.factoryInt)

    @Benchmark
    fun factoryLong(): BigDecimal = bigDecimalOf(fixture.factoryLong)

    @Benchmark
    fun factoryBigInteger(): BigDecimal = bigDecimalOf(fixture.factoryBigInteger)

    @Benchmark
    fun factoryBigIntegerScale(): BigDecimal = bigDecimalOf(
        fixture.factoryBigInteger,
        fixture.factoryBigIntegerScale,
    )
}
