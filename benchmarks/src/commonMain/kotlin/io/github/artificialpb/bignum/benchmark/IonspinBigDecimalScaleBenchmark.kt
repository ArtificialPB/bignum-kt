package io.github.artificialpb.bignum.benchmark

import com.ionspin.kotlin.bignum.decimal.BigDecimal as IonspinBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger as IonspinBigInteger
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
open class IonspinBigDecimalScaleBenchmark : IonspinBigDecimalProfiledBenchmarkState() {
    @Benchmark
    fun scaleProperty(): Long = fixture.scaledLeft.scale

    @Benchmark
    fun precisionProperty(): Long = fixture.left.precision

    @Benchmark
    fun signumMethod(): Int = fixture.negativeLeft.signum()

    @Benchmark
    fun significandProperty(): IonspinBigInteger = fixture.left.significand

    @Benchmark
    fun numberOfDecimalDigitsMethod(): Long = fixture.left.numberOfDecimalDigits()

    @Benchmark
    fun setScaleExactMethod(): IonspinBigDecimal = fixture.scaledLeft.scale(fixture.scaleTarget)

    @Benchmark
    fun setScaleRoundedMethod(): IonspinBigDecimal = fixture.scaledLeft.scale(fixture.roundedScaleTarget)

    @Benchmark
    fun movePointLeftMethod(): IonspinBigDecimal = fixture.left.moveDecimalPoint(-fixture.scaleMoveAmount)

    @Benchmark
    fun movePointRightMethod(): IonspinBigDecimal = fixture.left.moveDecimalPoint(fixture.scaleMoveAmount)
}
