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
open class IonspinBigDecimalConstructionBenchmark : IonspinBigDecimalProfiledBenchmarkState() {
    @Benchmark
    fun constructorString(): IonspinBigDecimal = IonspinBigDecimal.parseString(fixture.constructorString)

    @Benchmark
    fun constructorBigInteger(): IonspinBigDecimal = IonspinBigDecimal.fromBigInteger(fixture.constructorBigInteger)

    @Benchmark
    fun constructorBigIntegerScale(): IonspinBigDecimal = IonspinBigDecimal.fromBigIntegerWithExponent(
        fixture.constructorBigInteger,
        fixture.constructorExponent,
    )

    @Benchmark
    fun factoryString(): IonspinBigDecimal = IonspinBigDecimal.parseString(fixture.factoryString)

    @Benchmark
    fun factoryInt(): IonspinBigDecimal = IonspinBigDecimal.fromInt(fixture.factoryInt)

    @Benchmark
    fun factoryLong(): IonspinBigDecimal = IonspinBigDecimal.fromLong(fixture.factoryLong)

    @Benchmark
    fun factoryBigInteger(): IonspinBigDecimal = IonspinBigDecimal.fromBigInteger(fixture.factoryBigInteger)

    @Benchmark
    fun factoryBigIntegerScale(): IonspinBigDecimal = IonspinBigDecimal.fromBigIntegerWithExponent(
        fixture.factoryBigInteger,
        fixture.factoryBigIntegerExponent,
    )
}
