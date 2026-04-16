package io.github.artificialpb.bignum.benchmark

import com.ionspin.kotlin.bignum.integer.BigInteger
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
open class IonspinBitwiseBenchmark : IonspinBitwiseProfiledBenchmarkState() {
    @Benchmark
    fun andMethod(): BigInteger = bitwiseFixture.left.and(bitwiseFixture.right)

    @Benchmark
    fun orMethod(): BigInteger = bitwiseFixture.left.or(bitwiseFixture.right)

    @Benchmark
    fun xorMethod(): BigInteger = bitwiseFixture.left.xor(bitwiseFixture.right)

    @Benchmark
    fun notMethod(): BigInteger = bitwiseFixture.left.not()

    @Benchmark
    fun shiftLeftMethod(): BigInteger = bitwiseFixture.left.shl(fixture.shiftAmount)

    @Benchmark
    fun shiftRightMethod(): BigInteger = bitwiseFixture.left.shr(fixture.shiftAmount / 2)

    @Benchmark
    fun bitLengthMethod(): Int = bitwiseFixture.left.bitLength()
}

abstract class IonspinBitwiseProfiledBenchmarkState : IonspinProfiledBenchmarkState() {
    @Param("positive", "negative")
    var operandSign: String = "positive"

    protected val bitwiseFixture: IonspinBitwiseFixture
        get() = when (operandSign) {
            "positive" -> fixture.positiveBitwise
            "negative" -> fixture.negativeBitwise
            else -> error("Unknown bitwise operand sign: $operandSign")
        }
}
