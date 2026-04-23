package io.github.artificialpb.bignum.benchmark

import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import com.ionspin.kotlin.bignum.decimal.BigDecimal as IonspinBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger as IonspinBigInteger

data class IonspinBigDecimalBenchmarkFixture(
    val constructorString: String,
    val constructorBigInteger: IonspinBigInteger,
    val constructorExponent: Long,
    val constructorScale: Long,
    val factoryString: String,
    val factoryInt: Int,
    val factoryLong: Long,
    val factoryBigInteger: IonspinBigInteger,
    val factoryBigIntegerExponent: Long,
    val left: IonspinBigDecimal,
    val scaledLeft: IonspinBigDecimal,
    val right: IonspinBigDecimal,
    val negativeLeft: IonspinBigDecimal,
    val zeroDividend: IonspinBigDecimal,
    val equalLeft: IonspinBigDecimal,
    val compareTarget: IonspinBigDecimal,
    val equalScaleLeft: IonspinBigDecimal,
    val equalScaleRight: IonspinBigDecimal,
    val largeScaleGapLeft: IonspinBigDecimal,
    val largeScaleGapRight: IonspinBigDecimal,
    val singleLimbOperand: IonspinBigDecimal,
    val multiLimbOperand: IonspinBigDecimal,
    val dividend: IonspinBigDecimal,
    val divisor: IonspinBigDecimal,
    val genericExactDividend: IonspinBigDecimal,
    val genericExactDivisor: IonspinBigDecimal,
    val powBase: IonspinBigDecimal,
    val powExponent: Int,
    val scaleTarget: Long,
    val roundedScaleTarget: Long,
    val scaleMoveAmount: Long,
)

internal object IonspinBigDecimalBenchmarkFixtures {
    private val ONE = IonspinBigInteger.ONE
    private val THREE = IonspinBigInteger.parseString("3", 10)
    private val SEVEN = IonspinBigInteger.parseString("7", 10)
    private val NINE = IonspinBigInteger.parseString("9", 10)
    private val TEN_TO_TWENTY = IonspinBigInteger.parseString("10", 10).pow(20)

    fun create(profileName: String): IonspinBigDecimalBenchmarkFixture {
        val profile = benchmarkProfiles[profileName]
            ?: error("Unknown benchmark profile: $profileName")

        val constructorScale = 6L
        val factoryScale = 4L
        val constructorString = decimalOperand(profile.operandA, constructorScale.toInt())
        val factoryString = decimalOperand(profile.operandB, factoryScale.toInt())

        val constructorBigInteger = IonspinBigInteger.parseString(profile.operandA, 10)
        val factoryBigInteger = IonspinBigInteger.parseString(profile.operandB, 10)
        val constructorExponent = constructorBigInteger.numberOfDecimalDigits() - constructorScale - 1
        val factoryBigIntegerExponent = factoryBigInteger.numberOfDecimalDigits() - factoryScale - 1

        val left = IonspinBigDecimal.parseString(constructorString)
        val scaledLeft = left.scale(constructorScale)
        val right = IonspinBigDecimal.parseString(factoryString)
        val negativeLeft = left.negate()
        val zeroDividend = IonspinBigDecimal.ZERO
        val equalLeft = IonspinBigDecimal.parseString(constructorString)
        val compareTarget = left.add(IonspinBigDecimal.ONE)

        val equalScaleLeft = scaledLeft
        val equalScaleRight = right.scale(constructorScale)
        val largeScaleGapLeft = scaledLeft
        val largeScaleGapRight = right.scale(30)

        val singleLimbOperand = IonspinBigDecimal.fromInt(9)
        val multiLimbOperand = IonspinBigDecimal.fromBigInteger(
            constructorBigInteger.abs().multiply(TEN_TO_TWENTY).add(SEVEN),
        )

        val divisor = IonspinBigDecimal.fromInt(125)
        val dividend = left.moveDecimalPoint(3)
        val genericExactDividend = IonspinBigDecimal.fromBigInteger(
            constructorBigInteger.abs().add(ONE).multiply(THREE),
        )
        val genericExactDivisor = IonspinBigDecimal.fromBigInteger(THREE)
        val powBase = IonspinBigDecimal.parseString(decimalOperand(profile.powBase, 3))
        val scaleTarget = (constructorScale + 2).coerceAtMost(18)
        val roundedScaleTarget = (scaleTarget - 3).coerceAtLeast(0)
        val scaleMoveAmount = (profile.shiftAmount / 2).coerceAtLeast(1).toLong()

        return IonspinBigDecimalBenchmarkFixture(
            constructorString = constructorString,
            constructorBigInteger = constructorBigInteger,
            constructorExponent = constructorExponent,
            constructorScale = constructorScale,
            factoryString = factoryString,
            factoryInt = profile.intValue,
            factoryLong = profile.longValue,
            factoryBigInteger = factoryBigInteger,
            factoryBigIntegerExponent = factoryBigIntegerExponent,
            left = left,
            scaledLeft = scaledLeft,
            right = right,
            negativeLeft = negativeLeft,
            zeroDividend = zeroDividend,
            equalLeft = equalLeft,
            compareTarget = compareTarget,
            equalScaleLeft = equalScaleLeft,
            equalScaleRight = equalScaleRight,
            largeScaleGapLeft = largeScaleGapLeft,
            largeScaleGapRight = largeScaleGapRight,
            singleLimbOperand = singleLimbOperand,
            multiLimbOperand = multiLimbOperand,
            dividend = dividend,
            divisor = divisor,
            genericExactDividend = genericExactDividend,
            genericExactDivisor = genericExactDivisor,
            powBase = powBase,
            powExponent = profile.powExponent,
            scaleTarget = scaleTarget,
            roundedScaleTarget = roundedScaleTarget,
            scaleMoveAmount = scaleMoveAmount,
        )
    }

    private fun decimalOperand(digits: String, scale: Int): String {
        val wholeDigits = digits.length - scale
        return if (wholeDigits > 0) {
            digits.substring(0, wholeDigits) + "." + digits.substring(wholeDigits)
        } else {
            "0." + "0".repeat(-wholeDigits) + digits
        }
    }
}

@State(Scope.Benchmark)
abstract class IonspinBigDecimalProfiledBenchmarkState {
    @Param("small", "medium", "large")
    var profile: String = "small"

    protected lateinit var fixture: IonspinBigDecimalBenchmarkFixture

    @Setup
    fun setupFixture() {
        fixture = IonspinBigDecimalBenchmarkFixtures.create(profile)
    }
}
