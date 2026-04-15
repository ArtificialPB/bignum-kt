package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigInteger
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
open class BitwiseBenchmark : ProfiledBenchmarkState() {
    @Benchmark
    fun andMethod(): BigInteger = fixture.bitwiseLeft.and(fixture.bitwiseRight)

    @Benchmark
    fun orMethod(): BigInteger = fixture.bitwiseLeft.or(fixture.bitwiseRight)

    @Benchmark
    fun xorMethod(): BigInteger = fixture.bitwiseLeft.xor(fixture.bitwiseRight)

    @Benchmark
    fun notMethod(): BigInteger = fixture.bitwiseLeft.not()

    @Benchmark
    fun andNotMethod(): BigInteger = fixture.bitwiseLeft.andNot(fixture.bitwiseRight)

    @Benchmark
    fun shiftLeftMethod(): BigInteger = fixture.bitwiseLeft.shiftLeft(fixture.shiftAmount)

    @Benchmark
    fun shiftRightMethod(): BigInteger = fixture.bitwiseLeft.shiftRight(fixture.shiftAmount / 2)

    @Benchmark
    fun testBitMethod(): Boolean = fixture.testBitInput.testBit(fixture.bitIndex)

    @Benchmark
    fun setBitMethod(): BigInteger = fixture.setBitInput.setBit(fixture.bitIndex)

    @Benchmark
    fun clearBitMethod(): BigInteger = fixture.clearBitInput.clearBit(fixture.bitIndex)

    @Benchmark
    fun flipBitMethod(): BigInteger = fixture.flipBitInput.flipBit(fixture.bitIndex)

    @Benchmark
    fun getLowestSetBitMethod(): Int = fixture.lowestSetBitInput.getLowestSetBit()

    @Benchmark
    fun bitLengthMethod(): Int = fixture.bitwiseLeft.bitLength()

    @Benchmark
    fun bitCountMethod(): Int = fixture.bitwiseLeft.bitCount()
}
