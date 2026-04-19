package io.github.artificialpb.bignum.benchmark

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
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
open class IonspinConstructionBenchmark : IonspinProfiledBenchmarkState() {
    @Benchmark
    fun parseString(): BigInteger = BigInteger.parseString(fixture.constructorString, 10)

    @Benchmark
    fun parseStringRadix(): BigInteger = BigInteger.parseString(fixture.constructorRadixString, fixture.constructorRadix)

    @Benchmark
    fun fromByteArray(): BigInteger = BigInteger.fromByteArray(fixture.byteArrayInput, Sign.POSITIVE)

    @Benchmark
    fun fromInt(): BigInteger = BigInteger.fromInt(fixture.factoryInt)

    @Benchmark
    fun fromLong(): BigInteger = BigInteger.fromLong(fixture.factoryLong)
}
