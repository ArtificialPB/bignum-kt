package io.github.artificialpb.bignum.benchmark

import com.ionspin.kotlin.bignum.decimal.BigDecimal as IonspinBigDecimal
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
open class IonspinBigDecimalArithmeticBenchmark : IonspinBigDecimalProfiledBenchmarkState() {
    @Benchmark
    fun addMethod(): IonspinBigDecimal = fixture.left.add(fixture.right)

    @Benchmark
    fun addEqualScaleMethod(): IonspinBigDecimal = fixture.equalScaleLeft.add(fixture.equalScaleRight)

    @Benchmark
    fun addLargeScaleGapMethod(): IonspinBigDecimal = fixture.largeScaleGapLeft.add(fixture.largeScaleGapRight)

    @Benchmark
    fun plusOperator(): IonspinBigDecimal = fixture.left + fixture.right

    @Benchmark
    fun subtractMethod(): IonspinBigDecimal = fixture.left.subtract(fixture.right)

    @Benchmark
    fun subtractEqualScaleMethod(): IonspinBigDecimal = fixture.equalScaleLeft.subtract(fixture.equalScaleRight)

    @Benchmark
    fun subtractLargeScaleGapMethod(): IonspinBigDecimal = fixture.largeScaleGapLeft.subtract(fixture.largeScaleGapRight)

    @Benchmark
    fun minusOperator(): IonspinBigDecimal = fixture.left - fixture.right

    @Benchmark
    fun multiplyMethod(): IonspinBigDecimal = fixture.left.multiply(fixture.right)

    @Benchmark
    fun multiplySingleLimbLeftMethod(): IonspinBigDecimal = fixture.singleLimbOperand.multiply(fixture.multiLimbOperand)

    @Benchmark
    fun multiplySingleLimbRightMethod(): IonspinBigDecimal = fixture.multiLimbOperand.multiply(fixture.singleLimbOperand)

    @Benchmark
    fun timesOperator(): IonspinBigDecimal = fixture.left * fixture.right

    @Benchmark
    fun divideMethod(): IonspinBigDecimal = fixture.dividend.divide(fixture.divisor)

    @Benchmark
    fun divideZeroDividendMethod(): IonspinBigDecimal = fixture.zeroDividend.divide(fixture.divisor)

    @Benchmark
    fun divideGenericExactMethod(): IonspinBigDecimal = fixture.genericExactDividend.divide(fixture.genericExactDivisor)

    @Benchmark
    fun divOperator(): IonspinBigDecimal = fixture.dividend / fixture.divisor

    @Benchmark
    fun remainderMethod(): IonspinBigDecimal = fixture.dividend.remainder(fixture.divisor)

    @Benchmark
    fun remainderOperator(): IonspinBigDecimal = fixture.dividend % fixture.divisor

    @Benchmark
    fun divideAndRemainderMethod(): Pair<IonspinBigDecimal, IonspinBigDecimal> =
        fixture.dividend.divideAndRemainder(fixture.divisor)

    @Benchmark
    fun powMethod(): IonspinBigDecimal = fixture.powBase.pow(fixture.powExponent)

    @Benchmark
    fun powZeroMethod(): IonspinBigDecimal = fixture.powBase.pow(0)

    @Benchmark
    fun powIdentityMethod(): IonspinBigDecimal = fixture.powBase.pow(1)

    @Benchmark
    fun absMethod(): IonspinBigDecimal = fixture.negativeLeft.abs()

    @Benchmark
    fun absPositiveMethod(): IonspinBigDecimal = fixture.left.abs()

    @Benchmark
    fun negateMethod(): IonspinBigDecimal = fixture.left.negate()

    @Benchmark
    fun unaryMinusOperator(): IonspinBigDecimal = -fixture.left
}
