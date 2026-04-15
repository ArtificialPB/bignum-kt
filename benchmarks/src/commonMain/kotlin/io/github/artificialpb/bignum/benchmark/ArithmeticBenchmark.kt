package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.dec
import io.github.artificialpb.bignum.div
import io.github.artificialpb.bignum.inc
import io.github.artificialpb.bignum.lcm
import io.github.artificialpb.bignum.minus
import io.github.artificialpb.bignum.plus
import io.github.artificialpb.bignum.rem
import io.github.artificialpb.bignum.times
import io.github.artificialpb.bignum.unaryMinus
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
open class ArithmeticBenchmark : ProfiledBenchmarkState() {
    @Benchmark
    fun addMethod(): BigInteger = fixture.left.add(fixture.right)

    @Benchmark
    fun plusOperator(): BigInteger = fixture.left + fixture.right

    @Benchmark
    fun subtractMethod(): BigInteger = fixture.left.subtract(fixture.right)

    @Benchmark
    fun minusOperator(): BigInteger = fixture.left - fixture.right

    @Benchmark
    fun multiplyMethod(): BigInteger = fixture.left.multiply(fixture.right)

    @Benchmark
    fun timesOperator(): BigInteger = fixture.left * fixture.right

    @Benchmark
    fun divideMethod(): BigInteger = fixture.dividend.divide(fixture.divisor)

    @Benchmark
    fun divOperator(): BigInteger = fixture.dividend / fixture.divisor

    @Benchmark
    fun remainderOperator(): BigInteger = fixture.dividend % fixture.divisor

    @Benchmark
    fun absMethod(): BigInteger = fixture.negativeLeft.abs()

    @Benchmark
    fun powMethod(): BigInteger = fixture.powBase.pow(fixture.powExponent)

    @Benchmark
    fun modMethod(): BigInteger = fixture.negativeLeft.mod(fixture.modulus)

    @Benchmark
    fun modPowMethod(): BigInteger =
        fixture.left.modPow(fixture.modExponent, fixture.modulus)

    @Benchmark
    fun modInverseMethod(): BigInteger =
        fixture.inverseBase.modInverse(fixture.modulus)

    @Benchmark
    fun gcdMethod(): BigInteger = fixture.left.gcd(fixture.right)

    @Benchmark
    fun divideAndRemainderMethod(): Array<BigInteger> =
        fixture.dividend.divideAndRemainder(fixture.divisor)

    @Benchmark
    fun unaryMinusOperator(): BigInteger = -fixture.left

    @Benchmark
    fun incrementOperator(): BigInteger = fixture.left.inc()

    @Benchmark
    fun decrementOperator(): BigInteger = fixture.left.dec()

    @Benchmark
    fun lcmMethod(): BigInteger = fixture.left.lcm(fixture.right)
}
