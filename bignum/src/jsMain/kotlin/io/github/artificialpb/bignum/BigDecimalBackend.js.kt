@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.artificialpb.bignum

internal actual object BigDecimalBackend {
    actual fun multiplyByPowerOfTen(value: BigInteger, power: Int, addedBits: Long): BigInteger = value * bigIntegerOf(10).pow(power)

    actual fun stripSmallFactor(value: BigInteger, factor: ULong, maxCount: Int): SmallFactorReduction {
        if (maxCount <= 0 || value.signum() == 0) return SmallFactorReduction(value, 0)

        val bigFactor = bigIntegerOfUnsignedMagnitude(factor)
        var reduced = value
        var count = 0
        while (count < maxCount) {
            val division = reduced.divideAndRemainder(bigFactor)
            if (division[1].signum() != 0) break
            reduced = division[0]
            count++
        }
        return SmallFactorReduction(reduced, count)
    }

    actual fun singleLimbMagnitudeOrNull(value: BigInteger): ULong? = if (value.signum() != 0 && value.abs().bitLength() <= 60) value.abs().toString().toULongOrNull() else null

    actual fun multiplyByUnsignedMagnitude(value: BigInteger, digit: ULong, digitSign: Int): BigInteger {
        require(digitSign != 0)
        require(digit != 0UL)
        if (value.signum() == 0) return ZERO
        if (digit == 1UL) return if (digitSign > 0) value else -value

        val product = value * bigIntegerOfUnsignedMagnitude(digit)
        return if (digitSign > 0) product else -product
    }

    actual fun multiplyCompactMagnitudes(left: ULong, right: ULong, sign: Int): BigInteger {
        require(sign != 0)
        if (left == 0UL || right == 0UL) return ZERO

        val magnitude = bigIntegerOfUnsignedMagnitude(left) * bigIntegerOfUnsignedMagnitude(right)
        return if (sign > 0) magnitude else -magnitude
    }

    actual fun magnitudeAsULongOrNull(value: BigInteger): ULong? = if (value.signum() == 0) {
        0UL
    } else if (value.abs().bitLength() <= ULong.SIZE_BITS) {
        value.abs().toString().toULongOrNull()
    } else {
        null
    }

    actual fun divisionByDigitMagnitudeOrNull(value: BigInteger): ULong? = magnitudeAsULongOrNull(value)

    actual fun divideAndRemainderByDigit(
        value: BigInteger,
        divisor: ULong,
        divisorSign: Int,
    ): SmallDigitDivision {
        require(divisor != 0UL) { "Division by zero" }
        require(divisorSign != 0) { "Division by zero" }
        val signedDivisor = bigIntegerOfUnsignedMagnitude(divisor).let { if (divisorSign < 0) -it else it }
        val division = value.divideAndRemainder(signedDivisor)
        return SmallDigitDivision(division[0], division[1])
    }

    actual fun divideExactQuotientOrNull(dividend: BigInteger, divisor: BigInteger): BigInteger? {
        require(divisor.signum() != 0) { "Division by zero" }
        if (dividend.signum() == 0) return ZERO

        val division = dividend.divideAndRemainder(divisor)
        return if (division[1].signum() == 0) division[0] else null
    }

    actual fun scaledDigitMagnitudeOrNull(value: BigInteger, factor: ULong): ULong? {
        val digit = divisionByDigitMagnitudeOrNull(value) ?: return null
        if (digit == 0UL || digit > ULong.MAX_VALUE / factor) return null
        return digit * factor
    }

    actual fun divideAndRemainderByDigitWithScaledQuotient(
        value: BigInteger,
        divisor: ULong,
        divisorSign: Int,
        quotientScaleFactor: ULong,
    ): SmallDigitDivision {
        val division = divideAndRemainderByDigit(value, divisor, divisorSign)
        return SmallDigitDivision(
            multiplyByUnsignedMagnitude(division.quotient, quotientScaleFactor, 1),
            division.remainder,
        )
    }

    actual fun bigIntegerOfUnsignedMagnitude(value: ULong): BigInteger = if (value == 0UL) ZERO else bigIntegerOf(value.toString())

    actual fun divideByPowerOfTen(
        value: BigInteger,
        power: Int,
        digitDivisor: ULong?,
        cachedDivisor: BigInteger?,
        addedBits: Long,
    ): PowerOfTenDivision {
        val divisor = cachedDivisor ?: bigIntegerOf(10).pow(power)
        val division = value.divideAndRemainder(divisor)
        val doubledRemainder = division[1].abs() * bigIntegerOf(2)
        return PowerOfTenDivision(
            division[0],
            division[1],
            doubledRemainder.compareTo(divisor),
        )
    }

    actual fun magnitudeBitLength(value: BigInteger): Int = value.abs().bitLength()
}
