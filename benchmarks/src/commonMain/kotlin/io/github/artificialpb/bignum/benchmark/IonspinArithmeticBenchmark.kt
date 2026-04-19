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
open class IonspinArithmeticBenchmark : IonspinProfiledBenchmarkState() {
    @Benchmark
    fun addMethod(): BigInteger = fixture.left.add(fixture.right)

    @Benchmark
    fun subtractMethod(): BigInteger = fixture.left.subtract(fixture.right)

    @Benchmark
    fun multiplyMethod(): BigInteger = fixture.left.multiply(fixture.right)

    @Benchmark
    fun divideMethod(): BigInteger = fixture.dividend.divide(fixture.divisor)

    @Benchmark
    fun remainderMethod(): BigInteger = fixture.dividend.remainder(fixture.divisor)

    @Benchmark
    fun absMethod(): BigInteger = fixture.negativeLeft.abs()

    @Benchmark
    fun powMethod(): BigInteger = fixture.powBase.pow(fixture.powExponent)

    @Benchmark
    fun modMethod(): BigInteger = fixture.negativeLeft.abs().mod(fixture.modulus)

    @Benchmark
    fun modInverseMethod(): BigInteger = fixture.inverseBase.modInverse(fixture.modulus)

    @Benchmark
    fun gcdMethod(): BigInteger = fixture.left.gcd(fixture.right)

    @Benchmark
    fun divideAndRemainderMethod(): Pair<BigInteger, BigInteger> = fixture.dividend.divideAndRemainder(fixture.divisor)

    @Benchmark
    fun unaryMinusMethod(): BigInteger = fixture.left.negate()

    @Benchmark
    fun incrementMethod(): BigInteger = fixture.left.inc()

    @Benchmark
    fun decrementMethod(): BigInteger = fixture.left.dec()
}
