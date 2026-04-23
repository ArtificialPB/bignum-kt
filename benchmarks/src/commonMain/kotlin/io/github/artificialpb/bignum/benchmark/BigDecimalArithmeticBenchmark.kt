package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.div
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
open class BigDecimalArithmeticBenchmark : BigDecimalProfiledBenchmarkState() {
    @Benchmark
    fun addMethod(): BigDecimal = fixture.left.add(fixture.right)

    @Benchmark
    fun addEqualScaleMethod(): BigDecimal = fixture.equalScaleLeft.add(fixture.equalScaleRight)

    @Benchmark
    fun addLargeScaleGapMethod(): BigDecimal = fixture.largeScaleGapLeft.add(fixture.largeScaleGapRight)

    @Benchmark
    fun addMathContextMethod(): BigDecimal = fixture.left.add(fixture.right, fixture.mathContext)

    @Benchmark
    fun plusOperator(): BigDecimal = fixture.left + fixture.right

    @Benchmark
    fun subtractMethod(): BigDecimal = fixture.left.subtract(fixture.right)

    @Benchmark
    fun subtractEqualScaleMethod(): BigDecimal = fixture.equalScaleLeft.subtract(fixture.equalScaleRight)

    @Benchmark
    fun subtractLargeScaleGapMethod(): BigDecimal = fixture.largeScaleGapLeft.subtract(fixture.largeScaleGapRight)

    @Benchmark
    fun subtractMathContextMethod(): BigDecimal = fixture.left.subtract(fixture.right, fixture.mathContext)

    @Benchmark
    fun minusOperator(): BigDecimal = fixture.left - fixture.right

    @Benchmark
    fun multiplyMethod(): BigDecimal = fixture.left.multiply(fixture.right)

    @Benchmark
    fun multiplySingleLimbLeftMethod(): BigDecimal = fixture.singleLimbOperand.multiply(fixture.multiLimbOperand)

    @Benchmark
    fun multiplySingleLimbRightMethod(): BigDecimal = fixture.multiLimbOperand.multiply(fixture.singleLimbOperand)

    @Benchmark
    fun multiplyMathContextMethod(): BigDecimal = fixture.left.multiply(fixture.right, fixture.mathContext)

    @Benchmark
    fun timesOperator(): BigDecimal = fixture.left * fixture.right

    @Benchmark
    fun divideMethod(): BigDecimal = fixture.dividend.divide(fixture.divisor)

    @Benchmark
    fun divideZeroDividendMethod(): BigDecimal = fixture.zeroDividend.divide(fixture.divisor)

    @Benchmark
    fun divideGenericExactMethod(): BigDecimal = fixture.genericExactDividend.divide(fixture.genericExactDivisor)

    @Benchmark
    fun divideRoundingModeMethod(): BigDecimal = fixture.roundedDivideDividend.divide(fixture.roundedDivideDivisor, fixture.roundingMode)

    @Benchmark
    fun divideScaleRoundingModeMethod(): BigDecimal = fixture.roundedDivideDividend.divide(fixture.roundedDivideDivisor, fixture.scaleTarget, fixture.roundingMode)

    @Benchmark
    fun divideMathContextMethod(): BigDecimal = fixture.roundedDivideDividend.divide(fixture.roundedDivideDivisor, fixture.mathContext)

    @Benchmark
    fun divOperator(): BigDecimal = fixture.dividend / fixture.divisor

    @Benchmark
    fun remainderMethod(): BigDecimal = fixture.dividend.remainder(fixture.divisor)

    @Benchmark
    fun remainderOperator(): BigDecimal = fixture.dividend % fixture.divisor

    @Benchmark
    fun remainderMathContextMethod(): BigDecimal = fixture.positiveScaleGenericDividend.remainder(fixture.positiveScaleGenericDivisor, fixture.integralMathContext)

    @Benchmark
    fun divideAndRemainderMethod(): Array<BigDecimal> = fixture.dividend.divideAndRemainder(fixture.divisor)

    @Benchmark
    fun divideAndRemainderNegativeScaleSmallDivisorMethod(): Array<BigDecimal> = fixture.negativeScaleSmallDividend.divideAndRemainder(fixture.negativeScaleSmallDivisor)

    @Benchmark
    fun divideAndRemainderPositiveScaleGenericDivisorMethod(): Array<BigDecimal> = fixture.positiveScaleGenericDividend.divideAndRemainder(fixture.positiveScaleGenericDivisor)

    @Benchmark
    fun divideAndRemainderNegativeScaleGenericDivisorMethod(): Array<BigDecimal> = fixture.negativeScaleGenericDividend.divideAndRemainder(fixture.negativeScaleGenericDivisor)

    @Benchmark
    fun divideAndRemainderMathContextMethod(): Array<BigDecimal> = fixture.positiveScaleGenericDividend.divideAndRemainder(
        fixture.positiveScaleGenericDivisor,
        fixture.integralMathContext,
    )

    @Benchmark
    fun divideToIntegralValueMathContextMethod(): BigDecimal = fixture.positiveScaleGenericDividend.divideToIntegralValue(
        fixture.positiveScaleGenericDivisor,
        fixture.integralMathContext,
    )

    @Benchmark
    fun powMethod(): BigDecimal = fixture.powBase.pow(fixture.powExponent)

    @Benchmark
    fun powZeroMethod(): BigDecimal = fixture.powBase.pow(0)

    @Benchmark
    fun powIdentityMethod(): BigDecimal = fixture.powBase.pow(1)

    @Benchmark
    fun powMathContextMethod(): BigDecimal = fixture.powBase.pow(fixture.powExponent, fixture.mathContext)

    @Benchmark
    fun sqrtMathContextMethod(): BigDecimal = fixture.sqrtInput.sqrt(fixture.mathContext)

    @Benchmark
    fun absMethod(): BigDecimal = fixture.negativeLeft.abs()

    @Benchmark
    fun absMathContextMethod(): BigDecimal = fixture.negativeLeft.abs(fixture.mathContext)

    @Benchmark
    fun absPositiveMethod(): BigDecimal = fixture.left.abs()

    @Benchmark
    fun negateMethod(): BigDecimal = fixture.left.negate()

    @Benchmark
    fun negateMathContextMethod(): BigDecimal = fixture.left.negate(fixture.mathContext)

    @Benchmark
    fun unaryMinusOperator(): BigDecimal = -fixture.left

    @Benchmark
    fun plusMethod(): BigDecimal = fixture.left.plus()

    @Benchmark
    fun plusMathContextMethod(): BigDecimal = fixture.left.plus(fixture.mathContext)

    @Benchmark
    fun roundMathContextMethod(): BigDecimal = fixture.left.round(fixture.mathContext)

    @Benchmark
    fun ulpMethod(): BigDecimal = fixture.left.ulp()
}
