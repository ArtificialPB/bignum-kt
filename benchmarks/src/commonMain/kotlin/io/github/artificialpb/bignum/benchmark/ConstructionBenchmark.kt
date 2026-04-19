package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
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
open class ConstructionBenchmark : ProfiledBenchmarkState() {
    @Benchmark
    fun constructorString(): BigInteger = BigInteger(fixture.constructorString)

    @Benchmark
    fun constructorStringRadix(): BigInteger = BigInteger(fixture.constructorRadixString, fixture.constructorRadix)

    @Benchmark
    fun constructorBytes(): BigInteger = BigInteger(fixture.byteArrayInput)

    @Benchmark
    fun constructorBytesSlice(): BigInteger = BigInteger(
        fixture.slicedByteArrayInput,
        fixture.sliceOffset,
        fixture.sliceLength,
    )

    @Benchmark
    fun factoryString(): BigInteger = bigIntegerOf(fixture.factoryString)

    @Benchmark
    fun factoryInt(): BigInteger = bigIntegerOf(fixture.factoryInt)

    @Benchmark
    fun factoryLong(): BigInteger = bigIntegerOf(fixture.factoryLong)
}
