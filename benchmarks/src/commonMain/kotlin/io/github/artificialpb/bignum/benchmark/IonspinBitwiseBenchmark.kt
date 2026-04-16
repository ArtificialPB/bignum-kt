package io.github.artificialpb.bignum.benchmark

import com.ionspin.kotlin.bignum.integer.BigInteger
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
open class IonspinBitwiseBenchmark : IonspinProfiledBenchmarkState() {
    @Benchmark
    fun andMethod(): BigInteger = fixture.bitwiseLeft.and(fixture.bitwiseRight)

    @Benchmark
    fun orMethod(): BigInteger = fixture.bitwiseLeft.or(fixture.bitwiseRight)

    @Benchmark
    fun xorMethod(): BigInteger = fixture.bitwiseLeft.xor(fixture.bitwiseRight)

    @Benchmark
    fun notMethod(): BigInteger = fixture.bitwiseLeft.not()

    @Benchmark
    fun shiftLeftMethod(): BigInteger = fixture.bitwiseLeft.shl(fixture.shiftAmount)

    @Benchmark
    fun shiftRightMethod(): BigInteger = fixture.bitwiseLeft.shr(fixture.shiftAmount / 2)

    @Benchmark
    fun bitLengthMethod(): Int = fixture.bitwiseLeft.bitLength()
}
