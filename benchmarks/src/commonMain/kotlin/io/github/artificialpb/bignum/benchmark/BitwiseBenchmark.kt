package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigInteger
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
open class BitwiseBenchmark : BitwiseProfiledBenchmarkState() {
    @Benchmark
    fun andMethod(): BigInteger = bitwiseFixture.left.and(bitwiseFixture.right)

    @Benchmark
    fun orMethod(): BigInteger = bitwiseFixture.left.or(bitwiseFixture.right)

    @Benchmark
    fun xorMethod(): BigInteger = bitwiseFixture.left.xor(bitwiseFixture.right)

    @Benchmark
    fun notMethod(): BigInteger = bitwiseFixture.left.not()

    @Benchmark
    fun andNotMethod(): BigInteger = bitwiseFixture.left.andNot(bitwiseFixture.right)

    @Benchmark
    fun shiftLeftMethod(): BigInteger = bitwiseFixture.left.shiftLeft(fixture.shiftAmount)

    @Benchmark
    fun shiftRightMethod(): BigInteger = bitwiseFixture.left.shiftRight(fixture.shiftAmount / 2)

    @Benchmark
    fun testBitMethod(): Boolean = bitwiseFixture.testBitInput.testBit(fixture.bitIndex)

    @Benchmark
    fun setBitMethod(): BigInteger = bitwiseFixture.setBitInput.setBit(fixture.bitIndex)

    @Benchmark
    fun clearBitMethod(): BigInteger = bitwiseFixture.clearBitInput.clearBit(fixture.bitIndex)

    @Benchmark
    fun flipBitMethod(): BigInteger = bitwiseFixture.flipBitInput.flipBit(fixture.bitIndex)

    @Benchmark
    fun getLowestSetBitMethod(): Int = bitwiseFixture.lowestSetBitInput.getLowestSetBit()

    @Benchmark
    fun bitLengthMethod(): Int = bitwiseFixture.left.bitLength()

    @Benchmark
    fun bitCountMethod(): Int = bitwiseFixture.left.bitCount()
}

abstract class BitwiseProfiledBenchmarkState : ProfiledBenchmarkState() {
    @Param("positive", "negative")
    var operandSign: String = "positive"

    protected val bitwiseFixture: BitwiseBenchmarkFixture
        get() = when (operandSign) {
            "positive" -> fixture.positiveBitwise
            "negative" -> fixture.negativeBitwise
            else -> error("Unknown bitwise operand sign: $operandSign")
        }
}
