@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.artificialpb.bignum

import io.github.artificialpb.bignum.tommath.MP_EQ
import io.github.artificialpb.bignum.tommath.MP_GT
import io.github.artificialpb.bignum.tommath.MP_LT
import io.github.artificialpb.bignum.tommath.mp_cmp_mag
import io.github.artificialpb.bignum.tommath.mp_div
import io.github.artificialpb.bignum.tommath.mp_div_d
import io.github.artificialpb.bignum.tommath.mp_expt_u32
import io.github.artificialpb.bignum.tommath.mp_mul
import io.github.artificialpb.bignum.tommath.mp_mul_2
import io.github.artificialpb.bignum.tommath.mp_mul_d
import kotlinx.cinterop.*

actual enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY,
}

actual class MathContext {
    private val precisionValue: Int
    private val roundingModeValue: RoundingMode

    actual constructor(precision: Int) : this(precision, RoundingMode.HALF_UP)

    actual constructor(precision: Int, roundingMode: RoundingMode) {
        if (precision < 0) {
            throw IllegalArgumentException("Digits < 0")
        }
        this.precisionValue = precision
        this.roundingModeValue = roundingMode
    }

    actual constructor(value: String) {
        if (!value.startsWith("precision=")) {
            throw IllegalArgumentException("bad string format")
        }
        val fence = value.indexOf(' ')
        if (fence < 0 || !value.startsWith("roundingMode=", fence + 1)) {
            throw IllegalArgumentException("bad string format")
        }
        val parsedPrecision = value.substring("precision=".length, fence).toIntOrNull()
            ?: throw IllegalArgumentException("bad string format")
        if (parsedPrecision < 0) {
            throw IllegalArgumentException("Digits < 0")
        }
        val roundingModeName = value.substring(fence + " roundingMode=".length)
        val parsedRoundingMode = try {
            RoundingMode.valueOf(roundingModeName)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("bad string format")
        }
        precisionValue = parsedPrecision
        roundingModeValue = parsedRoundingMode
    }

    actual fun getPrecision(): Int = precisionValue

    actual fun getRoundingMode(): RoundingMode = roundingModeValue

    override fun equals(other: Any?): Boolean = other is MathContext && precision == other.precision && roundingMode == other.roundingMode

    override fun hashCode(): Int = 31 * precision + roundingMode.hashCode()

    override fun toString(): String = "precision=$precision roundingMode=$roundingMode"
}

actual class BigDecimal private constructor() : Comparable<BigDecimal> {
    private var unscaled: BigInteger = ZERO
    private var scaleValue: Int = 0
    private var precisionCache: Int = 0
    private var stringCache: String? = null
    private var plainStringCache: String? = null
    private var engineeringStringCache: String? = null
    private var bigIntegerCache: BigInteger? = null

    actual constructor(value: String) : this() {
        parseString(value) { unscaled, scale ->
            initialize(unscaled, scale, trusted = true)
        }
    }

    actual constructor(value: BigInteger) : this(value, 0)

    actual constructor(value: BigInteger, scale: Int) : this() {
        initialize(value, scale, trusted = false)
    }

    actual constructor(value: Int) : this() {
        initialize(bigIntegerOf(value), 0, trusted = true)
    }

    actual constructor(value: Long) : this() {
        initialize(bigIntegerOf(value), 0, trusted = true)
    }

    internal constructor(unscaled: BigInteger, scale: Int, trusted: Boolean) : this() {
        initialize(unscaled, scale, trusted)
    }

    private fun initialize(unscaled: BigInteger, scale: Int, trusted: Boolean) {
        this.unscaled = unscaled
        this.scaleValue = scale
        this.precisionCache = if (unscaled.signum() == 0) 1 else 0
    }

    actual fun add(other: BigDecimal): BigDecimal {
        val targetScale = maxOf(scaleValue, other.scaleValue)
        val left = rescaleUnscaled(unscaled, scaleValue, targetScale)
        val right = rescaleUnscaled(other.unscaled, other.scaleValue, targetScale)
        return bigDecimalOfInternal(left + right, targetScale)
    }

    actual fun add(other: BigDecimal, mathContext: MathContext): BigDecimal = if (mathContext.precision == 0) add(other) else roundToMathContext(add(other), mathContext)

    actual fun subtract(other: BigDecimal): BigDecimal {
        val targetScale = maxOf(scaleValue, other.scaleValue)
        val left = rescaleUnscaled(unscaled, scaleValue, targetScale)
        val right = rescaleUnscaled(other.unscaled, other.scaleValue, targetScale)
        return bigDecimalOfInternal(left - right, targetScale)
    }

    actual fun subtract(other: BigDecimal, mathContext: MathContext): BigDecimal = if (mathContext.precision == 0) subtract(other) else roundToMathContext(subtract(other), mathContext)

    actual fun multiply(other: BigDecimal): BigDecimal {
        val scale = safeScaleAdd(scaleValue, other.scaleValue)
        val leftSign = signum()
        val rightSign = other.signum()
        if (leftSign == 0 || rightSign == 0) return bigDecimalOfInternal(ZERO, scale)

        val leftCompact = unscaled.magnitudeAsULongOrNull()
        val rightCompact = other.unscaled.magnitudeAsULongOrNull()
        if (leftCompact != null && rightCompact != null) {
            val product = multiplyCompactMagnitudes(leftCompact, rightCompact, leftSign * rightSign)
            return bigDecimalOfInternal(product, scale)
        }

        val leftDigit = unscaled.singleLimbMagnitudeOrNull()
        if (leftDigit != null) {
            val product = multiplyByUnsignedMagnitude(other.unscaled, leftDigit, leftSign)
            return bigDecimalOfInternal(product, scale)
        }

        val rightDigit = other.unscaled.singleLimbMagnitudeOrNull()
        if (rightDigit != null) {
            val product = multiplyByUnsignedMagnitude(unscaled, rightDigit, rightSign)
            return bigDecimalOfInternal(product, scale)
        }

        return bigDecimalOfInternal(unscaled * other.unscaled, scale)
    }

    actual fun multiply(other: BigDecimal, mathContext: MathContext): BigDecimal = if (mathContext.precision == 0) multiply(other) else roundToMathContext(multiply(other), mathContext)

    actual fun divide(other: BigDecimal): BigDecimal {
        requireNonZero(other)
        val preferredScale = safeScaleSubtract(scaleValue, other.scaleValue)
        if (signum() == 0) return bigDecimalOfInternal(ZERO, preferredScale)

        val dividend = unscaled.abs()
        val divisor = other.unscaled.abs()
        fastTerminatingDivide(dividend, divisor)?.let { fastDivision ->
            val quotient = if (signum() * other.signum() < 0) -fastDivision.quotient else fastDivision.quotient
            return bigDecimalOfInternal(
                quotient,
                safeScaleAdd(preferredScale, fastDivision.scaleAdjustment),
            )
        }
        divisor.divisionByDigitMagnitudeOrNull()?.let { divisorDigit ->
            val exactDivision = divideAndRemainderByDigit(dividend, divisorDigit, 1)
            if (exactDivision.remainder.signum() == 0) {
                val quotient = if (signum() * other.signum() < 0) -exactDivision.quotient else exactDivision.quotient
                return bigDecimalOfInternal(quotient, preferredScale)
            }
        }

        val gcd = dividend.gcd(divisor)
        val reducedDividend = dividend / gcd
        var reducedDivisor = divisor / gcd

        var twos = 0
        while ((reducedDivisor % TWO_BIG_INTEGER).signum() == 0) {
            reducedDivisor /= TWO_BIG_INTEGER
            twos++
        }

        var fives = 0
        while ((reducedDivisor % FIVE_BIG_INTEGER).signum() == 0) {
            reducedDivisor /= FIVE_BIG_INTEGER
            fives++
        }

        if (reducedDivisor != ONE) {
            throw ArithmeticException("Non-terminating decimal expansion; no exact representable decimal result.")
        }

        val scaleAdjustment = maxOf(twos, fives)
        var quotient = reducedDividend
        repeat(scaleAdjustment - twos) { quotient *= TWO_BIG_INTEGER }
        repeat(scaleAdjustment - fives) { quotient *= FIVE_BIG_INTEGER }
        if (signum() * other.signum() < 0) {
            quotient = -quotient
        }

        return bigDecimalOfInternal(quotient, safeScaleAdd(preferredScale, scaleAdjustment))
    }

    actual fun divide(other: BigDecimal, roundingMode: RoundingMode): BigDecimal = divide(other, scaleValue, roundingMode)

    actual fun divide(other: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal = divideToScale(other, scale, roundingMode)

    actual fun divide(other: BigDecimal, mathContext: MathContext): BigDecimal {
        if (mathContext.precision == 0) {
            return divide(other)
        }
        if (other.signum() == 0) {
            throw ArithmeticException("Division by zero")
        }
        val preferredScale = safeScaleSubtract(scaleValue, other.scaleValue)
        if (signum() == 0) {
            return bigDecimalOfInternal(ZERO, preferredScale)
        }

        val xscale = precision()
        var yscale = other.precision()
        if (compareMagnitudeNormalized(unscaled.abs(), xscale, other.unscaled.abs(), yscale) > 0) {
            yscale = safeScaleSubtract(yscale, 1)
        }

        val scale = safeScaleAdd(
            safeScaleSubtract(safeScaleAdd(preferredScale, yscale), xscale),
            mathContext.precision,
        )
        val quotient = if (safeScaleAdd(mathContext.precision, yscale) > xscale) {
            val raise = safeScaleSubtract(safeScaleAdd(mathContext.precision, yscale), xscale)
            divideAndRoundToScale(multiplyByPowerOfTen(unscaled, raise), other.unscaled, scale, mathContext.roundingMode, preferredScale)
        } else {
            val newScale = safeScaleSubtract(xscale, mathContext.precision)
            val raise = safeScaleSubtract(newScale, yscale)
            divideAndRoundToScale(unscaled, multiplyByPowerOfTen(other.unscaled, raise), scale, mathContext.roundingMode, preferredScale)
        }
        return roundToMathContext(quotient, mathContext)
    }

    actual fun remainder(other: BigDecimal): BigDecimal = divideAndRemainder(other)[1]

    actual fun remainder(other: BigDecimal, mathContext: MathContext): BigDecimal = divideAndRemainder(other, mathContext)[1]

    actual fun divideAndRemainder(other: BigDecimal): Array<BigDecimal> {
        requireNonZero(other)
        if (signum() == 0) {
            return arrayOf(
                bigDecimalOfInternal(ZERO, safeScaleSubtract(scaleValue, other.scaleValue)),
                bigDecimalOfInternal(ZERO, scaleValue),
            )
        }
        val preferredScale = safeScaleSubtract(scaleValue, other.scaleValue)
        if (preferredScale < 0 && (other.unscaled == ONE || other.unscaled == MINUS_ONE)) {
            val quotientUnscaled = if (other.unscaled.signum() < 0) -unscaled else unscaled
            return arrayOf(
                bigDecimalOfInternal(quotientUnscaled, preferredScale),
                bigDecimalOfInternal(ZERO, scaleValue),
            )
        }
        if (preferredScale < 0 && unscaled.abs() >= other.unscaled.abs()) {
            val exactQuotient = divideExactQuotientOrNull(unscaled, other.unscaled)
            if (exactQuotient != null) {
                return arrayOf(
                    bigDecimalOfInternal(exactQuotient, preferredScale),
                    bigDecimalOfInternal(ZERO, scaleValue),
                )
            }
        }
        return if (preferredScale >= 0) {
            var quotientAlreadyScaled = false
            val division = if (preferredScale > 0) {
                powerOfTenDigitOrNull(preferredScale)?.let { scaleFactor ->
                    scaledDigitMagnitudeOrNull(other.unscaled, scaleFactor)?.let { divisorDigit ->
                        quotientAlreadyScaled = true
                        divideAndRemainderByDigitWithScaledQuotient(unscaled, divisorDigit, other.unscaled.signum(), scaleFactor)
                    }
                }
            } else {
                null
            } ?: run {
                val scaledDivisor = multiplyByPowerOfTen(other.unscaled, preferredScale)
                scaledDivisor.divisionByDigitMagnitudeOrNull()?.let { divisorDigit ->
                    divideAndRemainderByDigit(unscaled, divisorDigit, scaledDivisor.signum())
                } ?: run {
                    val result = unscaled.divideAndRemainder(scaledDivisor)
                    SmallDigitDivision(result[0], result[1])
                }
            }
            val quotient = if (quotientAlreadyScaled) {
                bigDecimalOfInternal(division.quotient, preferredScale)
            } else {
                integerToBigDecimal(division.quotient, preferredScale)
            }
            val remainder = bigDecimalOfInternal(division.remainder, scaleValue)
            arrayOf(quotient, remainder)
        } else {
            val scaledDividend = multiplyByPowerOfTen(unscaled, -preferredScale)
            val division = other.unscaled.divisionByDigitMagnitudeOrNull()?.let { divisorDigit ->
                divideAndRemainderByDigit(scaledDividend, divisorDigit, other.unscaled.signum())
            } ?: run {
                val result = scaledDividend.divideAndRemainder(other.unscaled)
                SmallDigitDivision(result[0], result[1])
            }
            val quotient = integerToBigDecimal(division.quotient, preferredScale)
            val productScale = safeScaleAdd(quotient.scale(), other.scaleValue)
            val scaleReduction = -quotient.scale()
            val remainderUnscaled = when {
                division.remainder.signum() == 0 || scaleReduction == 0 -> division.remainder
                else -> {
                    val reduced = divideByPowerOfTen(division.remainder, scaleReduction)
                    reduced.quotient
                }
            }
            val remainder = bigDecimalOfInternal(remainderUnscaled, productScale)
            arrayOf(quotient, remainder)
        }
    }

    actual fun divideAndRemainder(other: BigDecimal, mathContext: MathContext): Array<BigDecimal> {
        if (mathContext.precision == 0) {
            return divideAndRemainder(other)
        }
        val quotient = divideToIntegralValue(other, mathContext)
        val remainder = subtract(quotient.multiply(other))
        return arrayOf(quotient, remainder)
    }

    actual fun divideToIntegralValue(other: BigDecimal, mathContext: MathContext): BigDecimal {
        if (mathContext.precision == 0 || compareMagnitude(other) < 0) {
            return divideToIntegralValue(other)
        }

        val preferredScale = safeScaleSubtract(scaleValue, other.scaleValue)
        var result = divide(other, MathContext(mathContext.precision, RoundingMode.DOWN))

        when {
            result.scale() < 0 -> {
                val product = result.multiply(other)
                if (subtract(product).compareMagnitude(other) >= 0) {
                    throw ArithmeticException("Division impossible")
                }
            }
            result.scale() > 0 -> {
                result = result.setScale(0, RoundingMode.DOWN)
            }
        }

        if (preferredScale > result.scale()) {
            val precisionDiff = mathContext.precision - result.precision()
            if (precisionDiff > 0) {
                return result.setScale(result.scale() + minOf(precisionDiff, preferredScale - result.scale()))
            }
        }
        return stripZerosToMatchScale(result.unscaledValue(), result.scale(), preferredScale)
    }

    actual fun pow(exponent: Int): BigDecimal {
        if (exponent < 0) throw ArithmeticException("Invalid operation")
        if (exponent == 0) return ONE_DECIMAL
        if (exponent == 1) return this
        return bigDecimalOfInternal(unscaled.pow(exponent), safeScaleMultiply(scaleValue, exponent))
    }

    actual fun pow(exponent: Int, mathContext: MathContext): BigDecimal {
        if (mathContext.precision == 0) {
            return pow(exponent)
        }
        if (exponent < -999_999_999 || exponent > 999_999_999) {
            throw ArithmeticException("Invalid operation")
        }
        if (exponent == 0) {
            return ONE_DECIMAL
        }

        val magnitude = kotlin.math.abs(exponent)
        val exponentDigits = magnitude.toString().length
        if (exponentDigits > mathContext.precision) {
            throw ArithmeticException("Invalid operation")
        }
        val workMathContext = MathContext(mathContext.precision + exponentDigits + 1, mathContext.roundingMode)

        var result = ONE_DECIMAL
        var shiftedMagnitude = magnitude
        var seenBit = false
        for (index in 1..31) {
            shiftedMagnitude += shiftedMagnitude
            if (shiftedMagnitude < 0) {
                seenBit = true
                result = result.multiply(this, workMathContext)
            }
            if (index == 31) {
                break
            }
            if (seenBit) {
                result = result.multiply(result, workMathContext)
            }
        }
        if (exponent < 0) {
            result = ONE_DECIMAL.divide(result, workMathContext)
        }
        return roundToMathContext(result, mathContext)
    }

    actual fun sqrt(mathContext: MathContext): BigDecimal {
        val sign = signum()
        when (sign) {
            -1 -> throw ArithmeticException("Attempted square root of negative BigDecimal")
            0 -> return bigDecimalOfInternal(ZERO, scaleValue / 2)
        }

        val preferredScale = scaleValue / 2
        val zeroWithPreferredScale = bigDecimalOfInternal(ZERO, preferredScale)
        val stripped = stripTrailingZeros()
        val strippedScale = stripped.scale()
        if (stripped.isPowerOfTen() && strippedScale % 2 == 0) {
            var result = bigDecimalOfInternal(ONE, strippedScale / 2)
            if (result.scale() != preferredScale) {
                result = result.add(zeroWithPreferredScale, mathContext)
            }
            return result
        }

        val scale = safeScaleSubtract(safeScaleAdd(stripped.scale(), 1), stripped.precision())
        val scaleAdjust = if (scale % 2 == 0) scale else safeScaleSubtract(scale, 1)
        val working = stripped.scaleByPowerOfTen(scaleAdjust)

        var guess = BigDecimal(kotlin.math.sqrt(working.toDouble()).toString())
        var guessPrecision = 15
        val originalPrecision = mathContext.precision
        val targetPrecision = when {
            originalPrecision == 0 -> stripped.precision() / 2 + 1
            mathContext.roundingMode in HALF_ROUNDING_MODES -> minOf(Int.MAX_VALUE - 2, originalPrecision * 2)
            else -> originalPrecision
        }

        var approx = guess
        val workingPrecision = working.precision()
        do {
            val tempPrecision = maxOf(maxOf(guessPrecision, targetPrecision + 2), workingPrecision)
            val tempMathContext = MathContext(tempPrecision, RoundingMode.HALF_EVEN)
            approx = ONE_HALF_DECIMAL.multiply(
                approx.add(working.divide(approx, tempMathContext), tempMathContext),
            )
            guessPrecision = if (guessPrecision > Int.MAX_VALUE / 2) Int.MAX_VALUE else guessPrecision * 2
        } while (guessPrecision < targetPrecision + 2)

        var result = if (mathContext.roundingMode == RoundingMode.UNNECESSARY || originalPrecision == 0) {
            val tempRounding = if (mathContext.roundingMode == RoundingMode.UNNECESSARY) {
                RoundingMode.DOWN
            } else {
                mathContext.roundingMode
            }
            val tempMathContext = MathContext(targetPrecision, tempRounding)
            val exactResult = approx.scaleByPowerOfTen(-scaleAdjust / 2).round(tempMathContext)
            if (subtract(exactResult.square()).compareTo(ZERO_DECIMAL) != 0) {
                throw ArithmeticException("Computed square root not exact.")
            }
            exactResult
        } else {
            val rounded = approx.scaleByPowerOfTen(-scaleAdjust / 2).round(mathContext)
            when (mathContext.roundingMode) {
                RoundingMode.DOWN, RoundingMode.FLOOR -> {
                    if (rounded.square().compareTo(this) > 0) {
                        var ulp = rounded.ulp()
                        if (approx.compareTo(ONE_DECIMAL) == 0) {
                            ulp = ulp.multiply(ONE_TENTH_DECIMAL)
                        }
                        rounded.subtract(ulp)
                    } else {
                        rounded
                    }
                }
                RoundingMode.UP, RoundingMode.CEILING -> {
                    if (rounded.square().compareTo(this) < 0) {
                        rounded.add(rounded.ulp())
                    } else {
                        rounded
                    }
                }
                else -> rounded
            }
        }

        if (result.scale() != preferredScale) {
            result = result.stripTrailingZeros().add(
                zeroWithPreferredScale,
                MathContext(originalPrecision, RoundingMode.UNNECESSARY),
            )
        }
        return result
    }

    actual fun abs(): BigDecimal = if (signum() >= 0) this else negate()

    actual fun abs(mathContext: MathContext): BigDecimal = if (signum() >= 0) plus(mathContext) else negate(mathContext)

    actual fun negate(): BigDecimal = bigDecimalOfInternal(-unscaled, scaleValue)

    actual fun negate(mathContext: MathContext): BigDecimal = negate().plus(mathContext)

    actual fun plus(): BigDecimal = this

    actual fun plus(mathContext: MathContext): BigDecimal = if (mathContext.precision == 0) this else roundToMathContext(this, mathContext)

    actual fun round(mathContext: MathContext): BigDecimal = plus(mathContext)

    actual fun signum(): Int = unscaled.signum()

    actual fun scale(): Int = scaleValue

    actual fun precision(): Int {
        val cached = precisionCache
        if (cached != 0) return cached

        val computed = decimalDigitLength(unscaled)
        precisionCache = computed
        return computed
    }

    actual fun unscaledValue(): BigInteger = unscaled

    actual fun setScale(newScale: Int): BigDecimal = setScale(newScale, RoundingMode.UNNECESSARY)

    actual fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal {
        if (newScale == scaleValue) return this
        if (signum() == 0) return bigDecimalOfInternal(ZERO, newScale)
        if (newScale == Int.MIN_VALUE) throw ArithmeticException("Overflow")
        return when {
            newScale > scaleValue -> {
                val scaleIncrease = safeScaleSubtract(newScale, scaleValue)
                bigDecimalOfInternal(multiplyByPowerOfTen(unscaled, scaleIncrease), newScale)
            }
            else -> {
                val scaleDecrease = safeScaleSubtract(scaleValue, newScale)
                val division = divideByPowerOfTen(unscaled, scaleDecrease)
                val quotient = division.quotient
                val remainder = division.remainder
                if (remainder.signum() == 0) {
                    bigDecimalOfInternal(quotient, newScale)
                } else {
                    val adjusted = roundQuotientByPowerOfTen(quotient, remainder, division, roundingMode)
                    bigDecimalOfInternal(adjusted, newScale)
                }
            }
        }
    }

    actual fun movePointLeft(n: Int): BigDecimal {
        if (n == 0) return this
        if (signum() == 0) {
            return bigDecimalOfInternal(ZERO, clampZeroScale(saturatingScaleAdd(scaleValue, n)))
        }

        val candidateScale = scaleValue.toLong() + n.toLong()
        return when {
            candidateScale > Int.MAX_VALUE.toLong() -> throw ArithmeticException("Overflow")
            candidateScale >= 0L -> bigDecimalOfInternal(unscaled, candidateScale.toInt())
            -candidateScale > Int.MAX_VALUE.toLong() -> throw ArithmeticException("Overflow")
            else -> bigDecimalOfInternal(multiplyByPowerOfTen(unscaled, (-candidateScale).toInt()), 0)
        }
    }

    actual fun movePointRight(n: Int): BigDecimal {
        if (n == 0) return this
        if (signum() == 0) {
            return bigDecimalOfInternal(ZERO, clampZeroScale(saturatingScaleSubtract(scaleValue, n)))
        }

        val candidateScale = scaleValue.toLong() - n.toLong()
        return when {
            candidateScale > Int.MAX_VALUE.toLong() -> throw ArithmeticException("Overflow")
            candidateScale >= 0L -> bigDecimalOfInternal(unscaled, candidateScale.toInt())
            -candidateScale > Int.MAX_VALUE.toLong() -> throw ArithmeticException("Overflow")
            else -> bigDecimalOfInternal(multiplyByPowerOfTen(unscaled, (-candidateScale).toInt()), 0)
        }
    }

    actual fun scaleByPowerOfTen(n: Int): BigDecimal = bigDecimalOfInternal(
        unscaled,
        if (signum() == 0) saturatingScaleSubtract(scaleValue, n) else safeScaleSubtract(scaleValue, n),
    )

    actual fun stripTrailingZeros(): BigDecimal {
        if (signum() == 0) return ZERO_DECIMAL
        var currentUnscaled = unscaled
        var currentScale = scaleValue
        while (currentUnscaled.size > 1 || currentUnscaled.limbs[0] >= 10UL) {
            if ((currentUnscaled.limbs[0] and 1UL) != 0UL) break
            val division = divideAndRemainderByDigit(currentUnscaled, 10UL, 1)
            if (division.remainder.signum() != 0) break
            currentUnscaled = division.quotient
            currentScale = safeScaleSubtract(currentScale, 1)
        }
        return bigDecimalOfInternal(currentUnscaled, currentScale)
    }

    actual fun min(other: BigDecimal): BigDecimal = if (compareTo(other) <= 0) this else other

    actual fun max(other: BigDecimal): BigDecimal = if (compareTo(other) >= 0) this else other

    actual fun toEngineeringString(): String {
        engineeringStringCache?.let { return it }

        val rendered = if (signum() == 0) {
            if (scaleValue == 0) {
                "0"
            } else if (scaleValue in 1..6) {
                toPlainString()
            } else if (scaleValue > 6) {
                // Positive scale beyond plain threshold: "0E-9", "0.0E-9", "0.00E-6", etc.
                val exponent = -(scaleValue.toLong() / 3L * 3L)
                val fractionZeros = scaleValue % 3
                val coefficient = if (fractionZeros == 0) "0" else "0." + "0".repeat(fractionZeros)
                coefficient + exponentSuffix(exponent)
            } else {
                // Negative scale: "0E+3", "0.0E+3", "0.00E+6", etc.
                val exponent = ((-scaleValue.toLong()) + 2L) / 3L * 3L
                val fractionZeros = (exponent + scaleValue.toLong()).toInt()
                val coefficient = if (fractionZeros == 0) "0" else "0." + "0".repeat(fractionZeros)
                coefficient + exponentSuffix(exponent)
            }
        } else {
            val digits = unscaled.abs().toString()
            val adjustedExponent = -scaleValue.toLong() + digits.length.toLong() - 1L
            val signPrefix = if (signum() < 0) "-" else ""
            if (scaleValue >= 0 && adjustedExponent >= -6L) {
                plainString(signPrefix, digits, scaleValue)
            } else {
                var exponent = adjustedExponent
                var digitsBeforePoint = (exponent % 3L).toInt() + 1
                if (digitsBeforePoint <= 0) digitsBeforePoint += 3
                exponent -= (digitsBeforePoint - 1).toLong()

                val coefficient = when {
                    digits.length <= digitsBeforePoint -> digits + "0".repeat(digitsBeforePoint - digits.length)
                    digitsBeforePoint == digits.length -> digits
                    else -> digits.substring(0, digitsBeforePoint) + "." + digits.substring(digitsBeforePoint)
                }
                if (exponent == 0L) signPrefix + coefficient else signPrefix + coefficient + exponentSuffix(exponent)
            }
        }
        engineeringStringCache = rendered
        return rendered
    }

    actual fun toPlainString(): String {
        plainStringCache?.let { return it }

        val rendered = if (unscaled.signum() == 0) {
            "0" + if (scaleValue > 0) "." + "0".repeat(scaleValue) else ""
        } else {
            val digits = unscaled.abs().toString()
            val signPrefix = if (signum() < 0) "-" else ""
            plainString(signPrefix, digits, scaleValue)
        }
        plainStringCache = rendered
        return rendered
    }

    actual fun toBigInteger(): BigInteger {
        bigIntegerCache?.let { return it }

        val converted = when {
            scaleValue <= 0 -> multiplyByPowerOfTen(unscaled, -scaleValue)
            else -> divideByPowerOfTen(unscaled, scaleValue).quotient
        }
        bigIntegerCache = converted
        return converted
    }

    actual fun toBigIntegerExact(): BigInteger = setScale(0, RoundingMode.UNNECESSARY).toBigInteger()

    actual fun ulp(): BigDecimal = bigDecimalOfInternal(ONE, scaleValue)

    actual override fun compareTo(other: BigDecimal): Int {
        if (this === other) return 0
        if (scaleValue == other.scaleValue) return unscaled.compareTo(other.unscaled)

        val leftSign = signum()
        val rightSign = other.signum()
        if (leftSign != rightSign) return leftSign.compareTo(rightSign)
        if (leftSign == 0) return 0

        val commonScale = maxOf(scaleValue, other.scaleValue)
        val left = rescaleUnscaled(unscaled, scaleValue, commonScale)
        val right = rescaleUnscaled(other.unscaled, other.scaleValue, commonScale)
        return left.compareTo(right)
    }

    actual override fun toString(): String {
        stringCache?.let { return it }

        val rendered = if (signum() == 0 && scaleValue < 0) {
            "0${exponentSuffix(-scaleValue.toLong())}"
        } else {
            val digits = if (signum() == 0) "0" else unscaled.abs().toString()
            val adjustedExponent = -scaleValue.toLong() + digits.length.toLong() - 1L
            val signPrefix = if (signum() < 0) "-" else ""
            if (scaleValue >= 0 && adjustedExponent >= -6L) {
                plainString(signPrefix, digits, scaleValue)
            } else {
                val coefficient = if (digits.length == 1) digits else digits[0] + "." + digits.substring(1)
                signPrefix + coefficient + exponentSuffix(adjustedExponent)
            }
        }
        stringCache = rendered
        return rendered
    }

    actual override fun equals(other: Any?): Boolean = other is BigDecimal && scaleValue == other.scaleValue && unscaled == other.unscaled

    actual override fun hashCode(): Int = 31 * unscaled.hashCode() + scaleValue

    private fun compareMagnitude(other: BigDecimal): Int = abs().compareTo(other.abs())

    private fun square(): BigDecimal = multiply(this)

    private fun isPowerOfTen(): Boolean = signum() > 0 && unscaled == ONE

    private fun divideToScale(other: BigDecimal, targetScale: Int, roundingMode: RoundingMode): BigDecimal {
        requireNonZero(other)
        val scaleShift = safeScaleSubtract(targetScale, safeScaleSubtract(scaleValue, other.scaleValue))
        return if (scaleShift >= 0) {
            divideAndRoundToScale(
                multiplyByPowerOfTen(unscaled, scaleShift),
                other.unscaled,
                targetScale,
                roundingMode,
                targetScale,
            )
        } else {
            divideAndRoundToScale(
                unscaled,
                multiplyByPowerOfTen(other.unscaled, -scaleShift),
                targetScale,
                roundingMode,
                targetScale,
            )
        }
    }

    private fun divideToIntegralValue(other: BigDecimal): BigDecimal {
        val preferredScale = safeScaleSubtract(scaleValue, other.scaleValue)
        val integerPart = when {
            preferredScale >= 0 -> {
                val scaledDivisor = multiplyByPowerOfTen(other.unscaled, preferredScale)
                unscaled / scaledDivisor
            }
            else -> {
                val scaledDividend = multiplyByPowerOfTen(unscaled, -preferredScale)
                scaledDividend / other.unscaled
            }
        }
        return integerToBigDecimal(integerPart, preferredScale)
    }

    private fun integerToBigDecimal(integerPart: BigInteger, preferredScale: Int): BigDecimal {
        if (integerPart.signum() == 0) {
            return bigDecimalOfInternal(ZERO, preferredScale)
        }
        if (preferredScale >= 0) {
            return bigDecimalOfInternal(multiplyByPowerOfTen(integerPart, preferredScale), preferredScale)
        }

        var scale = 0
        var unscaledValue = integerPart
        while (scale > preferredScale && (unscaledValue % TEN_BIG_INTEGER).signum() == 0) {
            unscaledValue /= TEN_BIG_INTEGER
            scale = safeScaleSubtract(scale, 1)
        }
        return bigDecimalOfInternal(unscaledValue, scale)
    }
}

actual fun bigDecimalOf(value: String): BigDecimal = BigDecimal(value)

actual fun bigDecimalOf(value: BigInteger): BigDecimal = BigDecimal(value)

actual fun bigDecimalOf(value: BigInteger, scale: Int): BigDecimal = BigDecimal(value, scale)

actual fun bigDecimalOf(value: Long): BigDecimal = when (value) {
    0L -> ZERO_DECIMAL
    1L -> ONE_DECIMAL
    10L -> TEN_DECIMAL
    else -> BigDecimal(value)
}

actual fun bigDecimalOf(value: Int): BigDecimal = when (value) {
    0 -> ZERO_DECIMAL
    1 -> ONE_DECIMAL
    10 -> TEN_DECIMAL
    else -> BigDecimal(value)
}

private data class PowerOfTenDivision(
    val quotient: BigInteger,
    val remainder: BigInteger,
    val compareHalf: Int,
)

private data class TerminatingDivision(
    val quotient: BigInteger,
    val scaleAdjustment: Int,
)

private data class SmallFactorReduction(
    val reduced: BigInteger,
    val count: Int,
)

private data class SmallDigitDivision(
    val quotient: BigInteger,
    val remainder: BigInteger,
)

private val TWO_BIG_INTEGER = bigIntegerOf(2)
private val FIVE_BIG_INTEGER = bigIntegerOf(5)
private val TEN_BIG_INTEGER = bigIntegerOf(10)
private const val SMALL_TEN_POWER_CACHE_LIMIT = 32
private const val LAZY_TEN_POWER_CACHE_LIMIT = 512
private val SMALL_TEN_POWERS = buildPowerCache(TEN_BIG_INTEGER, SMALL_TEN_POWER_CACHE_LIMIT)
private val LAZY_TEN_POWERS = arrayOfNulls<BigInteger>(LAZY_TEN_POWER_CACHE_LIMIT + 1).also { cache ->
    for (index in SMALL_TEN_POWERS.indices) {
        cache[index] = SMALL_TEN_POWERS[index]
    }
}
private const val SMALL_TEN_DIGIT_POWER_LIMIT = 18
private val SMALL_TEN_DIGITS = buildULongPowerCache(10UL, SMALL_TEN_DIGIT_POWER_LIMIT)
private val SMALL_FIVE_POWERS = buildPowerCache(FIVE_BIG_INTEGER, 20)
private val UNSIGNED_ULONG_UPPER_LIMB_EXCLUSIVE = 1UL shl (64 - CANONICAL_LIMB_BITS)
private const val COMPACT_WORD_BITS = 64
private const val COMPACT_WORD_MASK = 0xFFFF_FFFFUL
private const val COMPACT_REMAINDER_BITS = COMPACT_WORD_BITS - CANONICAL_LIMB_BITS
private const val COMPACT_HIGH_LOW_BITS = CANONICAL_LIMB_BITS - COMPACT_REMAINDER_BITS
private val COMPACT_HIGH_LOW_MASK = (1UL shl COMPACT_HIGH_LOW_BITS) - 1UL
private const val SINGLE_LIMB_HALF_BITS = 30
private const val SINGLE_LIMB_HALF_MASK = 0x3FFF_FFFFUL
private const val BIG_DIGIT_LENGTH_LOG10_2_NUMERATOR = 646_456_993L
private val ZERO_DECIMAL = BigDecimal(ZERO, 0, true)
private val ONE_DECIMAL = BigDecimal(ONE, 0, true)
private val TEN_DECIMAL = BigDecimal(TEN_BIG_INTEGER, 0, true)
private val ONE_HALF_DECIMAL = BigDecimal("0.5")
private val ONE_TENTH_DECIMAL = BigDecimal("0.1")
private val HALF_ROUNDING_MODES = setOf(RoundingMode.HALF_UP, RoundingMode.HALF_DOWN, RoundingMode.HALF_EVEN)
private val INT_MIN_BIG_INTEGER = bigIntegerOf(Int.MIN_VALUE.toLong())
private val INT_MAX_BIG_INTEGER = bigIntegerOf(Int.MAX_VALUE.toLong())
private val LONG_MIN_BIG_INTEGER = bigIntegerOf(Long.MIN_VALUE)
private val LONG_MAX_BIG_INTEGER = bigIntegerOf(Long.MAX_VALUE)
private const val MAX_BIG_INTEGER_BIT_LENGTH = Int.MAX_VALUE.toLong()

private inline fun parseString(value: String, action: (unscaled: BigInteger, scale: Int) -> Unit) {
    if (parseSimpleDecimalOrNull(value, action)) return
    numberFormatRequire(value.isNotEmpty()) { "Character array is missing \"e\" notation exponential mark." }

    var index = 0
    var sign = ""
    if (value[index] == '+' || value[index] == '-') {
        sign = value[index].toString()
        index++
        numberFormatRequire(index < value.length) { "No digits found." }
    }

    val significand = buildString {
        var seenPoint = false
        var digits = 0
        var fractionDigits = 0
        while (index < value.length) {
            val ch = value[index]
            when {
                ch in '0'..'9' -> {
                    append(ch)
                    digits++
                    if (seenPoint) fractionDigits++
                }

                ch == '.' && !seenPoint -> seenPoint = true
                ch == 'e' || ch == 'E' -> break
                else -> throw NumberFormatException("Character $ch is neither a decimal digit number, decimal point, nor \"e\" notation exponential mark.")
            }
            index++
        }
        numberFormatRequire(digits > 0) { "No digits found." }
        if (index == value.length) {
            val unscaled = BigInteger(sign + toString())
            action(unscaled, fractionDigits)
            return
        }
    }

    numberFormatRequire(index < value.length && (value[index] == 'e' || value[index] == 'E')) {
        "Character array is missing \"e\" notation exponential mark."
    }
    index++
    numberFormatRequire(index < value.length) { "No exponent digits." }
    val exponentString = value.substring(index)
    numberFormatRequire(exponentString.isNotEmpty()) { "No exponent digits." }
    val exponent = parseExponent(exponentString)
    val unscaled = BigInteger(sign + significand)
    val fractionDigits = value.substringBeforeLast('e').substringBeforeLast('E').substringAfter('.', "").length
    val scale = fractionDigits.toLong() - exponent
    numberFormatRequire(scale in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Scale out of range." }
    action(unscaled, scale.toInt())
}

private inline fun parseSimpleDecimalOrNull(
    value: String,
    action: (unscaled: BigInteger, scale: Int) -> Unit,
): Boolean {
    if (value.isEmpty()) return false

    var index = 0
    var sign = 1
    if (value[index] == '+' || value[index] == '-') {
        if (value[index] == '-') {
            sign = -1
        }
        index++
        if (index == value.length) return false
    }

    var pointIndex = -1
    var digits = 0
    while (index < value.length) {
        when (val ch = value[index]) {
            in '0'..'9' -> digits++
            '.' -> if (pointIndex == -1) {
                pointIndex = index
            } else {
                return false
            }
            'e', 'E' -> return false
            else -> return false
        }
        index++
    }

    if (digits == 0) return false
    if (pointIndex == -1) {
        action(BigInteger(value), 0)
        return true
    }

    action(
        parseDecimalBigIntegerSkippingIndex(
            value = value,
            digitsStart = if (sign < 0 || value[0] == '+') 1 else 0,
            digitsEnd = value.length,
            skippedIndex = pointIndex,
            sign = sign,
            digitCount = digits,
        ),
        value.length - pointIndex - 1,
    )
    return true
}

private fun bigDecimalOfInternal(unscaled: BigInteger, scale: Int): BigDecimal = when {
    unscaled.signum() == 0 && scale == 0 -> ZERO_DECIMAL
    unscaled == ONE && scale == 0 -> ONE_DECIMAL
    unscaled == TEN_BIG_INTEGER && scale == 0 -> TEN_DECIMAL
    else -> BigDecimal(unscaled, scale, true)
}

private fun roundToMathContext(value: BigDecimal, mathContext: MathContext): BigDecimal {
    val precision = mathContext.precision
    if (precision == 0) return value

    var current = value
    var drop = current.precision() - precision
    while (drop > 0) {
        val newScale = safeScaleSubtract(current.scale(), drop)
        val division = divideByPowerOfTen(current.unscaledValue(), drop)
        val rounded = if (division.remainder.signum() == 0) {
            division.quotient
        } else {
            roundQuotientByPowerOfTen(division.quotient, division.remainder, division, mathContext.roundingMode)
        }
        current = bigDecimalOfInternal(rounded, newScale)
        drop = current.precision() - precision
    }
    return current
}

private fun divideAndRoundToScale(
    dividend: BigInteger,
    divisor: BigInteger,
    scale: Int,
    roundingMode: RoundingMode,
    preferredScale: Int,
): BigDecimal {
    require(divisor.signum() != 0) { "Division by zero" }
    val division = dividend.divideAndRemainder(divisor)
    val isExact = division[1].signum() == 0
    val quotient = if (isExact) {
        division[0]
    } else {
        roundQuotient(division[0], division[1], divisor, roundingMode)
    }
    return if (!isExact || preferredScale == scale) {
        bigDecimalOfInternal(quotient, scale)
    } else {
        stripZerosToMatchScale(quotient, scale, preferredScale)
    }
}

private fun stripZerosToMatchScale(unscaled: BigInteger, scale: Int, preferredScale: Int): BigDecimal {
    var currentUnscaled = unscaled
    var currentScale = scale
    while (currentUnscaled.abs() >= TEN_BIG_INTEGER && currentScale > preferredScale) {
        if ((currentUnscaled.abs().limbs[0] and 1UL) != 0UL) break
        val division = divideAndRemainderByDigit(currentUnscaled, 10UL, 1)
        if (division.remainder.signum() != 0) break
        currentUnscaled = division.quotient
        currentScale = safeScaleSubtract(currentScale, 1)
    }
    return bigDecimalOfInternal(currentUnscaled, currentScale)
}

private fun compareMagnitudeNormalized(
    left: BigInteger,
    leftScale: Int,
    right: BigInteger,
    rightScale: Int,
): Int = when {
    leftScale == rightScale -> left.compareTo(right)
    leftScale < rightScale -> multiplyByPowerOfTen(left, rightScale - leftScale).compareTo(right)
    else -> left.compareTo(multiplyByPowerOfTen(right, leftScale - rightScale))
}

private fun rescaleUnscaled(value: BigInteger, fromScale: Int, toScale: Int): BigInteger = if (fromScale == toScale) value else multiplyByPowerOfTen(value, toScale - fromScale)

private fun multiplyByPowerOfTen(value: BigInteger, power: Int): BigInteger {
    require(power >= 0) { "Negative power: $power" }
    if (power == 0 || value.signum() == 0) return value
    ensurePowerOfTenBitLength(value, power)
    powerOfTenDigitOrNull(power)?.let { factor ->
        return multiplyByUnsignedMagnitude(value, factor, 1)
    }
    cachedTenPowerOrNull(power)?.let { factor ->
        return value * factor
    }
    return TEN_BIG_INTEGER.withBorrowedHandle { tenHandle ->
        value.withBorrowedHandle { valueHandle ->
            val addedBits = powerOfTenBitLengthUpperBound(power)
            val factor = allocMp(estimatedCanonicalLimbs(addedBits))
            checkMp(mp_expt_u32(tenHandle, power.toUInt(), factor), factor)
            val result = allocMp(estimatedCanonicalLimbs(value.bitLength().toLong() + addedBits))
            checkMp(mp_mul(valueHandle, factor, result), factor, result)
            freeMp(factor)
            BigInteger(result)
        }
    }
}

private fun fastTerminatingDivide(dividend: BigInteger, divisor: BigInteger): TerminatingDivision? {
    val divisorTwos = divisor.getLowestSetBit().coerceAtLeast(0)
    val dividendTwos = minOf(dividend.getLowestSetBit().coerceAtLeast(0), divisorTwos)
    var reducedDividend = if (dividendTwos == 0) dividend else dividend.shiftRight(dividendTwos)
    val reducedDivisor = if (divisorTwos == 0) divisor else divisor.shiftRight(divisorTwos)
    val twos = divisorTwos - dividendTwos

    val divisorFives = stripSingleLimbFiveFactor(reducedDivisor)
    if (divisorFives.reduced != ONE) return null

    val dividendFives = stripSingleLimbFiveFactor(reducedDividend, divisorFives.count)
    reducedDividend = dividendFives.reduced
    val fives = divisorFives.count - dividendFives.count

    val scaleAdjustment = maxOf(twos, fives)
    var quotient = reducedDividend
    val twosNeeded = scaleAdjustment - twos
    if (twosNeeded > 0) {
        quotient = quotient.shiftLeft(twosNeeded)
    }
    val fivesNeeded = scaleAdjustment - fives
    if (fivesNeeded > 0) {
        quotient *= if (fivesNeeded <= SMALL_FIVE_POWERS.lastIndex) {
            SMALL_FIVE_POWERS[fivesNeeded]
        } else {
            FIVE_BIG_INTEGER.pow(fivesNeeded)
        }
    }
    return TerminatingDivision(quotient, scaleAdjustment)
}

private fun stripSingleLimbFiveFactor(value: BigInteger, maxCount: Int = Int.MAX_VALUE): SmallFactorReduction {
    if (maxCount <= 0 || value.signum() == 0) return SmallFactorReduction(value, 0)

    val limb = value.singleLimbMagnitudeOrNull() ?: return stripSmallFactor(value, 5UL, maxCount)
    var reduced = limb
    var count = 0
    while (count < maxCount) {
        if (reduced % 5UL != 0UL) break
        reduced /= 5UL
        count++
    }
    return if (count == 0) {
        SmallFactorReduction(value, 0)
    } else {
        SmallFactorReduction(bigIntegerOfUnsignedMagnitude(reduced), count)
    }
}

private fun stripSmallFactor(value: BigInteger, factor: ULong, maxCount: Int = Int.MAX_VALUE): SmallFactorReduction {
    if (maxCount <= 0 || value.signum() == 0) return SmallFactorReduction(value, 0)

    return value.withBorrowedHandle { handle ->
        var current = handle
        var ownedCurrent: kotlinx.cinterop.CPointer<io.github.artificialpb.bignum.tommath.mp_int>? = null
        var count = 0

        memScoped {
            val remainder = alloc<ULongVar>()
            while (count < maxCount) {
                val quotient = allocMp(maxOf(current.pointed.used, 1))
                val err = mp_div_d(current, factor, quotient, remainder.ptr)
                if (err != io.github.artificialpb.bignum.tommath.MP_OKAY) {
                    ownedCurrent?.let { freeMp(it) }
                    checkMp(err, quotient)
                }
                if (remainder.value != 0UL) {
                    freeMp(quotient)
                    break
                }

                ownedCurrent?.let { freeMp(it) }
                current = quotient
                ownedCurrent = quotient
                count++
            }
        }

        val reduced = ownedCurrent ?: return@withBorrowedHandle SmallFactorReduction(value, 0)
        SmallFactorReduction(BigInteger(reduced), count)
    }
}

private fun BigInteger.singleLimbMagnitudeOrNull(): ULong? = if (size == 1) limbs[0] else null

private fun multiplyByUnsignedMagnitude(
    value: BigInteger,
    digit: ULong,
    digitSign: Int,
): BigInteger {
    require(digitSign != 0)
    require(digit != 0UL)
    if (value.signum() == 0) return ZERO
    if (digit == 1UL) {
        return if (digitSign > 0) value else -value
    }

    val digitLow = digit and SINGLE_LIMB_HALF_MASK
    val digitHigh = digit shr SINGLE_LIMB_HALF_BITS
    val result = ULongArray(value.size + 1)
    var carry = 0UL
    for (index in 0 until value.size) {
        val limb = value.limbs[index]
        val limbLow = limb and SINGLE_LIMB_HALF_MASK
        val limbHigh = limb shr SINGLE_LIMB_HALF_BITS
        val p0 = limbLow * digitLow
        val mid = limbHigh * digitLow + limbLow * digitHigh
        val p3 = limbHigh * digitHigh
        val lowSum = p0 + ((mid and SINGLE_LIMB_HALF_MASK) shl SINGLE_LIMB_HALF_BITS)
        val productLow = lowSum and CANONICAL_LIMB_MASK
        val productHigh = p3 + (lowSum shr CANONICAL_LIMB_BITS) + (mid shr SINGLE_LIMB_HALF_BITS)
        val sum = productLow + carry
        result[index] = sum and CANONICAL_LIMB_MASK
        carry = productHigh + (sum shr CANONICAL_LIMB_BITS)
    }
    if (carry != 0UL) {
        result[value.size] = carry
    }
    return bigIntegerFromLimbs(
        value.signum() * digitSign,
        if (carry != 0UL) value.size + 1 else value.size,
        result,
    )
}

private fun multiplyCompactMagnitudes(left: ULong, right: ULong, sign: Int): BigInteger {
    require(sign != 0)
    if (left == 0UL || right == 0UL) return ZERO

    // Recreate the JDK-style compact multiply path with an explicit 128-bit product.
    val leftLow = left and COMPACT_WORD_MASK
    val leftHigh = left shr 32
    val rightLow = right and COMPACT_WORD_MASK
    val rightHigh = right shr 32

    val p0 = leftLow * rightLow
    val p1 = leftLow * rightHigh
    val p2 = leftHigh * rightLow
    val p3 = leftHigh * rightHigh

    val middle = (p0 shr 32) + (p1 and COMPACT_WORD_MASK) + (p2 and COMPACT_WORD_MASK)
    val low = (p0 and COMPACT_WORD_MASK) or ((middle and COMPACT_WORD_MASK) shl 32)
    val high = p3 + (p1 shr 32) + (p2 shr 32) + (middle shr 32)

    if (high == 0UL) {
        val magnitude = bigIntegerOfUnsignedMagnitude(low)
        return if (sign > 0) magnitude else -magnitude
    }

    val limbs = ulongArrayOf(
        low and CANONICAL_LIMB_MASK,
        ((low shr CANONICAL_LIMB_BITS) or ((high and COMPACT_HIGH_LOW_MASK) shl COMPACT_REMAINDER_BITS)) and CANONICAL_LIMB_MASK,
        high shr COMPACT_HIGH_LOW_BITS,
    )
    return bigIntegerFromLimbs(sign, limbs.size, limbs)
}

private fun BigInteger.magnitudeAsULongOrNull(): ULong? = when (size) {
    0 -> 0UL
    1 -> limbs[0]
    2 -> {
        val upper = limbs[1]
        if (upper >= UNSIGNED_ULONG_UPPER_LIMB_EXCLUSIVE) null else (upper shl CANONICAL_LIMB_BITS) or limbs[0]
    }

    else -> null
}

private fun BigInteger.divisionByDigitMagnitudeOrNull(): ULong? = when (size) {
    0 -> 0UL
    1 -> limbs[0]
    else -> null
}

private fun divideAndRemainderByDigit(value: BigInteger, divisor: ULong, divisorSign: Int): SmallDigitDivision {
    require(divisor != 0UL) { "Division by zero" }
    require(divisorSign != 0) { "Division by zero" }
    return value.withBorrowedHandle { handle ->
        val quotient = allocMp(maxOf(handle.pointed.used, 1))
        memScoped {
            val remainder = alloc<ULongVar>()
            checkMp(mp_div_d(handle, divisor, quotient, remainder.ptr), quotient)
            val quotientMagnitude = BigInteger(quotient)
            val signedQuotient = if (divisorSign < 0 && quotientMagnitude.signum() != 0) {
                -quotientMagnitude
            } else {
                quotientMagnitude
            }
            val remainderMagnitude = bigIntegerOfUnsignedMagnitude(remainder.value)
            val signedRemainder = if (value.signum() < 0 && remainderMagnitude.signum() != 0) {
                -remainderMagnitude
            } else {
                remainderMagnitude
            }
            SmallDigitDivision(signedQuotient, signedRemainder)
        }
    }
}

private fun divideExactQuotientOrNull(dividend: BigInteger, divisor: BigInteger): BigInteger? {
    require(divisor.signum() != 0) { "Division by zero" }
    if (dividend.signum() == 0) return ZERO

    divisor.divisionByDigitMagnitudeOrNull()?.let { divisorDigit ->
        val division = divideAndRemainderByDigit(dividend, divisorDigit, divisor.signum())
        return if (division.remainder.signum() == 0) division.quotient else null
    }

    if (dividend.size <= SCHOOLBOOK_DIV_THRESHOLD && divisor.size <= SCHOOLBOOK_DIV_THRESHOLD) {
        val dividendMagnitude = if (dividend.signum() < 0) dividend.abs() else dividend
        val divisorMagnitude = if (divisor.signum() < 0) divisor.abs() else divisor
        val quotientSign = dividend.signum() * divisor.signum()
        return divRemMagnitude(dividendMagnitude, divisorMagnitude) { quotient, remainder ->
            if (remainder.signum() != 0) {
                null
            } else if (quotientSign < 0 && quotient.signum() != 0) {
                -quotient
            } else {
                quotient
            }
        }
    }

    return withBorrowedHandles(dividend, divisor) { dividendHandle, divisorHandle ->
        val quotient = allocMp()
        val remainder = allocMp()
        checkMp(mp_div(dividendHandle, divisorHandle, quotient, remainder), quotient, remainder)
        if (remainder.pointed.used != 0) {
            freeMp(quotient)
            freeMp(remainder)
            return@withBorrowedHandles null
        }
        freeMp(remainder)
        BigInteger(quotient)
    }
}

private fun powerOfTenDigitOrNull(power: Int): ULong? = if (power in 0..SMALL_TEN_DIGIT_POWER_LIMIT) SMALL_TEN_DIGITS[power] else null

private fun scaledDigitMagnitudeOrNull(value: BigInteger, factor: ULong): ULong? {
    val digit = value.divisionByDigitMagnitudeOrNull() ?: return null
    if (digit == 0UL || digit > CANONICAL_LIMB_MASK / factor) return null
    return digit * factor
}

private fun divideAndRemainderByDigitWithScaledQuotient(
    value: BigInteger,
    divisor: ULong,
    divisorSign: Int,
    quotientScaleFactor: ULong,
): SmallDigitDivision {
    require(divisor != 0UL) { "Division by zero" }
    require(divisorSign != 0) { "Division by zero" }
    return value.withBorrowedHandle { handle ->
        val quotient = allocMp(maxOf(handle.pointed.used, 1))
        memScoped {
            val remainder = alloc<ULongVar>()
            checkMp(mp_div_d(handle, divisor, quotient, remainder.ptr), quotient)
            val signedQuotient = multiplyByUnsignedMagnitude(
                BigInteger(quotient),
                quotientScaleFactor,
                if (divisorSign < 0) -1 else 1,
            )
            val remainderMagnitude = bigIntegerOfUnsignedMagnitude(remainder.value)
            val signedRemainder = if (value.signum() < 0 && remainderMagnitude.signum() != 0) {
                -remainderMagnitude
            } else {
                remainderMagnitude
            }
            SmallDigitDivision(signedQuotient, signedRemainder)
        }
    }
}

private fun bigIntegerOfUnsignedMagnitude(value: ULong): BigInteger = when {
    value == 0UL -> ZERO
    value < CANONICAL_LIMB_BASE -> BigInteger(1, 1, ulongArrayOf(value))
    else -> {
        val lower = value and CANONICAL_LIMB_MASK
        val upper = value shr CANONICAL_LIMB_BITS
        BigInteger(1, 2, ulongArrayOf(lower, upper))
    }
}

private fun divideByPowerOfTen(value: BigInteger, power: Int): PowerOfTenDivision {
    require(power >= 0) { "Negative power: $power" }
    if (power == 0 || value.signum() == 0) {
        return PowerOfTenDivision(value, ZERO, -1)
    }
    if (power > 1 && value.bitLength().toLong() < powerOfTenBitLengthLowerBound(power - 1)) {
        return PowerOfTenDivision(ZERO, value, -1)
    }
    powerOfTenDigitOrNull(power)?.let { divisor ->
        return value.withBorrowedHandle { handle ->
            val quotient = allocMp(maxOf(handle.pointed.used, 1))
            memScoped {
                val remainder = alloc<ULongVar>()
                checkMp(mp_div_d(handle, divisor, quotient, remainder.ptr), quotient)
                val remainderMagnitude = bigIntegerOfUnsignedMagnitude(remainder.value)
                val signedRemainder = if (value.signum() < 0 && remainderMagnitude.signum() != 0) {
                    -remainderMagnitude
                } else {
                    remainderMagnitude
                }
                PowerOfTenDivision(
                    BigInteger(quotient),
                    signedRemainder,
                    compareDigitRemainderToHalfDivisor(remainder.value, divisor),
                )
            }
        }
    }
    cachedTenPowerOrNull(power)?.let { divisor ->
        return withBorrowedHandles(value, divisor) { valueHandle, divisorHandle ->
            val quotient = allocMp()
            val remainder = allocMp()
            checkMp(mp_div(valueHandle, divisorHandle, quotient, remainder), quotient, remainder)
            val compareHalf = compareRemainderToHalfDivisor(remainder, divisorHandle)
            PowerOfTenDivision(BigInteger(quotient), BigInteger(remainder), compareHalf)
        }
    }
    return TEN_BIG_INTEGER.withBorrowedHandle { tenHandle ->
        value.withBorrowedHandle { valueHandle ->
            val addedBits = powerOfTenBitLengthUpperBound(power)
            val divisor = allocMp(estimatedCanonicalLimbs(addedBits))
            checkMp(mp_expt_u32(tenHandle, power.toUInt(), divisor), divisor)
            val quotient = allocMp()
            val remainder = allocMp()
            checkMp(mp_div(valueHandle, divisor, quotient, remainder), divisor, quotient, remainder)
            val compareHalf = compareRemainderToHalfDivisor(remainder, divisor)
            freeMp(divisor)
            PowerOfTenDivision(BigInteger(quotient), BigInteger(remainder), compareHalf)
        }
    }
}

private fun roundQuotient(
    quotient: BigInteger,
    remainder: BigInteger,
    divisor: BigInteger,
    roundingMode: RoundingMode,
): BigInteger {
    val sign = quotient.signum().takeIf { it != 0 } ?: (remainder.signum() * divisor.signum())
    val absRemainder = remainder.abs()
    val absDivisor = divisor.abs()
    val twiceRemainder = absRemainder + absRemainder
    val compareHalf = twiceRemainder.compareTo(absDivisor)
    val increment = when (roundingMode) {
        RoundingMode.UP -> true
        RoundingMode.DOWN -> false
        RoundingMode.CEILING -> sign > 0
        RoundingMode.FLOOR -> sign < 0
        RoundingMode.HALF_UP -> compareHalf >= 0
        RoundingMode.HALF_DOWN -> compareHalf > 0
        RoundingMode.HALF_EVEN -> compareHalf > 0 || (compareHalf == 0 && (quotient.abs() % TWO_BIG_INTEGER) != ZERO)
        RoundingMode.UNNECESSARY -> throw ArithmeticException("Rounding necessary")
    }
    if (!increment) return quotient
    return quotient + if (sign < 0) MINUS_ONE else ONE
}

private fun roundQuotientByPowerOfTen(
    quotient: BigInteger,
    remainder: BigInteger,
    division: PowerOfTenDivision,
    roundingMode: RoundingMode,
): BigInteger {
    val sign = quotient.signum().takeIf { it != 0 } ?: remainder.signum()
    val increment = when (roundingMode) {
        RoundingMode.UP -> true
        RoundingMode.DOWN -> false
        RoundingMode.CEILING -> sign > 0
        RoundingMode.FLOOR -> sign < 0
        RoundingMode.HALF_UP -> division.compareHalf >= 0
        RoundingMode.HALF_DOWN -> division.compareHalf > 0
        RoundingMode.HALF_EVEN -> division.compareHalf > 0 || (division.compareHalf == 0 && (quotient.abs() % TWO_BIG_INTEGER) != ZERO)
        RoundingMode.UNNECESSARY -> throw ArithmeticException("Rounding necessary")
    }
    if (!increment) return quotient
    return quotient + if (sign < 0) MINUS_ONE else ONE
}

private fun exponentSuffix(exponent: Long): String = if (exponent >= 0L) "E+$exponent" else "E$exponent"

private fun decimalDigitLength(value: BigInteger): Int {
    if (value.signum() == 0) return 1

    val estimate = (((magnitudeBitLength(value).toLong() + 1L) * BIG_DIGIT_LENGTH_LOG10_2_NUMERATOR) ushr 31).toInt()
    val threshold = cachedTenPowerOrNull(estimate) ?: TEN_BIG_INTEGER.pow(estimate)
    return if (compareMagnitudes(value, threshold) < 0) estimate else estimate + 1
}

private fun cachedTenPowerOrNull(power: Int): BigInteger? {
    if (power !in 0..LAZY_TEN_POWER_CACHE_LIMIT) return null

    LAZY_TEN_POWERS[power]?.let { return it }

    var currentPower = power - 1
    while (currentPower >= 0) {
        val cached = LAZY_TEN_POWERS[currentPower]
        if (cached != null) {
            var current = cached
            for (nextPower in currentPower + 1..power) {
                current *= TEN_BIG_INTEGER
                LAZY_TEN_POWERS[nextPower] = current
            }
            return current
        }
        currentPower--
    }
    return null
}

private fun magnitudeBitLength(value: BigInteger): Int {
    if (value.size == 0) return 0
    val highLimbBits = ULong.SIZE_BITS - value.limbs[value.size - 1].countLeadingZeroBits()
    return (((value.size - 1).toLong() * CANONICAL_LIMB_BITS) + highLimbBits.toLong()).toInt()
}

private fun parseExponent(value: String): Long {
    var index = 0
    var sign = 1L
    when (value.firstOrNull()) {
        '+' -> index = 1
        '-' -> {
            sign = -1L
            index = 1
        }
    }
    numberFormatRequire(index < value.length) { "No exponent digits." }

    var exponent = 0L
    while (index < value.length) {
        val digit = value[index]
        numberFormatRequire(digit in '0'..'9') { "Not a digit." }
        if (exponent > (Long.MAX_VALUE - (digit - '0')) / 10L) {
            exponent = Long.MAX_VALUE
        } else {
            exponent = exponent * 10L + (digit - '0')
        }
        index++
    }

    return if (sign < 0L) -exponent else exponent
}

private fun requireNonZero(other: BigDecimal) {
    if (other.signum() == 0) throw ArithmeticException("Division by zero")
}

private fun safeScaleAdd(left: Int, right: Int): Int {
    val result = left.toLong() + right.toLong()
    if (result !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        throw ArithmeticException("Overflow")
    }
    return result.toInt()
}

private fun safeScaleSubtract(left: Int, right: Int): Int {
    val result = left.toLong() - right.toLong()
    if (result !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        throw ArithmeticException("Overflow")
    }
    return result.toInt()
}

private fun safeScaleMultiply(scale: Int, factor: Int): Int {
    val result = scale.toLong() * factor.toLong()
    if (result !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        throw ArithmeticException("Overflow")
    }
    return result.toInt()
}

private fun saturatingScaleAdd(left: Int, right: Int): Int = saturateToIntRange(left.toLong() + right.toLong())

private fun saturatingScaleSubtract(left: Int, right: Int): Int = saturateToIntRange(left.toLong() - right.toLong())

private fun saturateToIntRange(value: Long): Int = when {
    value < Int.MIN_VALUE.toLong() -> Int.MIN_VALUE
    value > Int.MAX_VALUE.toLong() -> Int.MAX_VALUE
    else -> value.toInt()
}

private fun clampZeroScale(scale: Int): Int = maxOf(scale, 0)

private fun plainString(signPrefix: String, digits: String, scale: Int): String = when {
    scale == 0 -> signPrefix + digits
    scale < 0 -> signPrefix + digits + "0".repeat(-scale)
    digits.length > scale -> {
        val point = digits.length - scale
        StringBuilder(digits).apply {
            insert(point, '.')
            if (signPrefix.isNotEmpty()) {
                insert(0, '-')
            }
        }.toString()
    }
    else -> signPrefix + "0." + "0".repeat(scale - digits.length) + digits
}

private fun buildPowerCache(base: BigInteger, maxPower: Int): Array<BigInteger> {
    val cache = arrayOfNulls<BigInteger>(maxPower + 1)
    var current = ONE
    cache[0] = current
    for (power in 1..maxPower) {
        current = current * base
        cache[power] = current
    }
    @Suppress("UNCHECKED_CAST")
    return cache as Array<BigInteger>
}

private fun buildULongPowerCache(base: ULong, maxPower: Int): ULongArray {
    val cache = ULongArray(maxPower + 1)
    var current = 1UL
    cache[0] = current
    for (power in 1..maxPower) {
        current *= base
        cache[power] = current
    }
    return cache
}

private fun ensurePowerOfTenBitLength(value: BigInteger, power: Int) {
    if (value.bitLength().toLong() + powerOfTenBitLengthUpperBound(power) > MAX_BIG_INTEGER_BIT_LENGTH) {
        throw ArithmeticException("Overflow")
    }
}

private fun powerOfTenBitLengthUpperBound(power: Int): Long = (power.toLong() * 332_193L + 99_999L) / 100_000L

private fun powerOfTenBitLengthLowerBound(power: Int): Long = (power.toLong() * 332_192L) / 100_000L + 1L

private fun estimatedCanonicalLimbs(bitLength: Long): Int = maxOf(1L, (bitLength + CANONICAL_LIMB_BITS - 1L) / CANONICAL_LIMB_BITS).toInt()

private fun compareRemainderToHalfDivisor(
    remainderHandle: kotlinx.cinterop.CPointer<io.github.artificialpb.bignum.tommath.mp_int>,
    divisorHandle: kotlinx.cinterop.CPointer<io.github.artificialpb.bignum.tommath.mp_int>,
): Int {
    val doubledRemainder = allocMp(maxOf(remainderHandle.pointed.used + 1, 1))
    checkMp(mp_mul_2(remainderHandle, doubledRemainder), doubledRemainder)
    val comparison = mp_cmp_mag(doubledRemainder, divisorHandle)
    freeMp(doubledRemainder)
    return when (comparison) {
        MP_LT -> -1
        MP_EQ -> 0
        MP_GT -> 1
        else -> error("Unexpected comparison result: $comparison")
    }
}

private fun compareDigitRemainderToHalfDivisor(remainder: ULong, divisor: ULong): Int {
    val doubledRemainder = remainder * 2UL
    return when {
        doubledRemainder < divisor -> -1
        doubledRemainder > divisor -> 1
        else -> 0
    }
}

private inline fun numberFormatRequire(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) throw NumberFormatException(lazyMessage())
}

actual fun BigDecimal.toInt(): Int = toBigInteger().toInt()

actual fun BigDecimal.toLong(): Long = toBigInteger().toLong()

actual fun BigDecimal.toFloat(): Float = toString().toFloat()

actual fun BigDecimal.toDouble(): Double = toString().toDouble()

actual fun BigDecimal.toIntExact(): Int {
    val integer = toBigIntegerExact()
    if (integer < INT_MIN_BIG_INTEGER || integer > INT_MAX_BIG_INTEGER) {
        throw ArithmeticException("Overflow")
    }
    return integer.toInt()
}

actual fun BigDecimal.toLongExact(): Long {
    val integer = toBigIntegerExact()
    if (integer < LONG_MIN_BIG_INTEGER || integer > LONG_MAX_BIG_INTEGER) {
        throw ArithmeticException("Overflow")
    }
    return integer.toLong()
}
