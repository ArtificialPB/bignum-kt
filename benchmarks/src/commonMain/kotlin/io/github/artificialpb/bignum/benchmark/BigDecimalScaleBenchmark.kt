package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigDecimal
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
open class BigDecimalScaleBenchmark : BigDecimalProfiledBenchmarkState() {
    @Benchmark
    fun scaleMethod(): Int = fixture.left.scale()

    @Benchmark
    fun precisionMethod(): Int = fixture.left.precision()

    @Benchmark
    fun signumMethod(): Int = fixture.negativeLeft.signum()

    @Benchmark
    fun unscaledValueMethod() = fixture.left.unscaledValue()

    @Benchmark
    fun setScaleExactMethod(): BigDecimal = fixture.left.setScale(fixture.scaleTarget)

    @Benchmark
    fun setScaleRoundedMethod(): BigDecimal = fixture.left.setScale(fixture.scaleTarget - 3, fixture.roundingMode)

    @Benchmark
    fun movePointLeftMethod(): BigDecimal = fixture.left.movePointLeft(fixture.scaleMoveAmount)

    @Benchmark
    fun movePointRightMethod(): BigDecimal = fixture.left.movePointRight(fixture.scaleMoveAmount)

    @Benchmark
    fun scaleByPowerOfTenMethod(): BigDecimal = fixture.left.scaleByPowerOfTen(fixture.scaleMoveAmount)

    @Benchmark
    fun stripTrailingZerosMethod(): BigDecimal = fixture.dividend.stripTrailingZeros()
}
