package io.github.artificialpb.bignum

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

/** Renders a binary64 value using the decimal selection and layout rules of `Double.toString` on the JVM. */
internal fun jvmDoubleToString(value: Double): String {
    if (!value.isFinite()) throw NumberFormatException("Infinite or NaN")
    if (value == 0.0) return if (value.toBits() < 0L) "-0.0" else "0.0"

    val negative = value.toBits() < 0L
    val magnitude = abs(value)
    val exact = magnitude.toExactBinaryRational()
    val decimalExponent = exact.decimalExponent(magnitude)
    magnitude.legacyIntegerDigitsOrNull(exact)?.let { (digits, unitExponent) ->
        return formatJvmDouble(negative, digits, unitExponent)
    }

    for (precision in MIN_DECIMAL_PRECISION..MAX_DECIMAL_PRECISION) {
        var unitExponent = decimalExponent - precision + 1
        var significand = exact.roundToDecimalGrid(unitExponent)
        var digits = significand.toString()

        if (digits.length > precision) {
            significand /= TEN
            unitExponent++
            digits = significand.toString()
        }

        val scientificExponent = unitExponent + digits.lastIndex
        val needsSecondDigit = scientificExponent !in PLAIN_MIN_EXPONENT..PLAIN_MAX_EXPONENT
        if ((!needsSecondDigit || digits.length >= MIN_SCIENTIFIC_DIGITS) &&
            decimalCandidateRoundTrips(digits, unitExponent, magnitude)
        ) {
            return formatJvmDouble(negative, digits, unitExponent)
        }
    }

    error("Unable to render finite Double ${value.toBits()}")
}

private data class BinaryRational(
    val numerator: BigInteger,
    val denominator: BigInteger,
) {
    fun roundToDecimalGrid(unitExponent: Int): BigInteger {
        val power = TEN.pow(abs(unitExponent))
        val scaledNumerator = if (unitExponent < 0) numerator * power else numerator
        val scaledDenominator = if (unitExponent > 0) denominator * power else denominator
        val division = scaledNumerator.divideAndRemainder(scaledDenominator)
        val quotient = division[0]
        val halfComparison = division[1].shiftLeft(1).compareTo(scaledDenominator)
        return if (halfComparison > 0 || (halfComparison == 0 && quotient.testBit(0))) quotient + ONE else quotient
    }
}

private fun Double.legacyIntegerDigitsOrNull(exact: BinaryRational): Pair<String, Int>? {
    val bits = toBits()
    val exponentBits = ((bits ushr FRACTION_BITS) and EXPONENT_MASK).toInt()
    if (exponentBits == 0) return null

    val binaryExponent = exponentBits - EXPONENT_BIAS
    if (binaryExponent !in MIN_SMALL_BINARY_EXPONENT..MAX_SMALL_BINARY_EXPONENT) return null

    val significand = (bits and FRACTION_MASK) or IMPLICIT_BIT
    val significantBits = FRACTION_BITS + 1 - significand.countTrailingZeroBits()
    val tinyBits = maxOf(0, significantBits - binaryExponent - 1)
    if (tinyBits != 0) return null

    val integerDivision = exact.numerator.divideAndRemainder(exact.denominator)
    if (integerDivision[1].signum() != 0) return null

    // JDK 17's integer fast path discards decimal places that are smaller than the value's binary spacing.
    val discardedDigits = if (binaryExponent > FRACTION_BITS + 1) {
        insignificantIntegerDigits(binaryExponent - (FRACTION_BITS + 1) - 1)
    } else {
        0
    }
    val decimalUnit = TEN.pow(discardedDigits)
    val roundedInteger = if (discardedDigits == 0) {
        integerDivision[0]
    } else {
        val division = integerDivision[0].divideAndRemainder(decimalUnit)
        if (division[1].shiftLeft(1) >= decimalUnit) division[0] + ONE else division[0]
    }

    val integerDigits = roundedInteger.toString()
    val trailingZeros = integerDigits.length - integerDigits.trimEnd('0').length
    return integerDigits.dropLast(trailingZeros) to discardedDigits + trailingZeros
}

private fun insignificantIntegerDigits(power: Int): Int = when (power) {
    in 4..6 -> 1
    in 7..8 -> 2
    else -> 0
}

private fun Double.toExactBinaryRational(): BinaryRational {
    val bits = toBits()
    val exponentBits = ((bits ushr FRACTION_BITS) and EXPONENT_MASK).toInt()
    val fraction = bits and FRACTION_MASK
    val significand: Long
    val binaryExponent: Int
    if (exponentBits == 0) {
        significand = fraction
        binaryExponent = MIN_SUBNORMAL_EXPONENT
    } else {
        significand = fraction or IMPLICIT_BIT
        binaryExponent = exponentBits - EXPONENT_BIAS - FRACTION_BITS
    }

    val integerSignificand = bigIntegerOf(significand)
    return if (binaryExponent >= 0) {
        BinaryRational(integerSignificand.shiftLeft(binaryExponent), ONE)
    } else {
        BinaryRational(integerSignificand, ONE.shiftLeft(-binaryExponent))
    }
}

private fun decimalCandidateRoundTrips(
    digits: String,
    unitExponent: Int,
    expected: Double,
): Boolean = "${digits}e$unitExponent".toDouble().toBits() == expected.toBits()

private fun formatJvmDouble(
    negative: Boolean,
    candidateDigits: String,
    candidateUnitExponent: Int,
): String {
    var digits = candidateDigits
    var unitExponent = candidateUnitExponent
    val scientificExponent = unitExponent + digits.lastIndex
    val minimumDigits = if (scientificExponent in PLAIN_MIN_EXPONENT..PLAIN_MAX_EXPONENT) 1 else MIN_SCIENTIFIC_DIGITS
    while (digits.length > minimumDigits && digits.endsWith('0')) {
        digits = digits.dropLast(1)
        unitExponent++
    }

    val normalizedScientificExponent = unitExponent + digits.lastIndex
    val body = if (normalizedScientificExponent in PLAIN_MIN_EXPONENT..PLAIN_MAX_EXPONENT) {
        formatPlain(digits, normalizedScientificExponent)
    } else {
        "${digits[0]}.${digits.substring(1).ifEmpty { "0" }}E$normalizedScientificExponent"
    }
    return if (negative) "-$body" else body
}

private fun formatPlain(digits: String, scientificExponent: Int): String {
    if (scientificExponent < 0) {
        return "0.${"0".repeat(-scientificExponent - 1)}$digits"
    }

    val integerDigits = scientificExponent + 1
    return if (integerDigits >= digits.length) {
        digits + "0".repeat(integerDigits - digits.length) + ".0"
    } else {
        digits.substring(0, integerDigits) + "." + digits.substring(integerDigits)
    }
}

private fun BinaryRational.decimalExponent(value: Double): Int {
    var exponent = floor(log10(value)).toInt().coerceIn(MIN_DECIMAL_EXPONENT, MAX_DECIMAL_EXPONENT)
    while (compareToPowerOfTen(exponent) < 0) exponent--
    while (exponent < MAX_DECIMAL_EXPONENT && compareToPowerOfTen(exponent + 1) >= 0) exponent++
    return exponent
}

private fun BinaryRational.compareToPowerOfTen(exponent: Int): Int = if (exponent >= 0) {
    numerator.compareTo(denominator * TEN.pow(exponent))
} else {
    (numerator * TEN.pow(-exponent)).compareTo(denominator)
}

private const val MIN_DECIMAL_PRECISION = 1
private const val MIN_SCIENTIFIC_DIGITS = 2
private const val MAX_DECIMAL_PRECISION = 17
private const val FRACTION_BITS = 52
private const val EXPONENT_BIAS = 1023
private const val MIN_SUBNORMAL_EXPONENT = -1074
private const val MIN_SMALL_BINARY_EXPONENT = -21
private const val MAX_SMALL_BINARY_EXPONENT = 62
private const val MIN_DECIMAL_EXPONENT = -324
private const val MAX_DECIMAL_EXPONENT = 308
private const val EXPONENT_MASK = 0x7FFL
private const val FRACTION_MASK = 0x000F_FFFF_FFFF_FFFFL
private const val IMPLICIT_BIT = 0x0010_0000_0000_0000L
private const val PLAIN_MIN_EXPONENT = -3
private const val PLAIN_MAX_EXPONENT = 6
private val TEN = bigIntegerOf(10)
