@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.artificialpb.bignum

internal actual object BigDecimalBackend {
    actual fun multiplyByPowerOfTen(value: BigInteger, power: Int, addedBits: Long): BigInteger = value * bigIntegerOf(10).pow(power)

    actual fun stripSmallFactor(value: BigInteger, factor: ULong, maxCount: Int): SmallFactorReduction {
        if (maxCount <= 0 || value.signum() == 0) return SmallFactorReduction(value, 0)

        val factorRaw = rawUnsignedLong(factor)
        var reducedRaw = value.raw
        var count = 0
        while (count < maxCount) {
            val quotientRaw = rawDivide(reducedRaw, factorRaw)
            val remainderRaw = rawSubtract(reducedRaw, rawMultiply(quotientRaw, factorRaw))
            if (!rawIsZero(remainderRaw)) break
            reducedRaw = quotientRaw
            count++
        }
        return SmallFactorReduction(if (count == 0) value else BigInteger(reducedRaw), count)
    }

    actual fun singleLimbMagnitudeOrNull(value: BigInteger): ULong? {
        val magnitude = rawMagnitude(value.raw)
        return if (!rawIsZero(magnitude) && rawLessThan(magnitude, RAW_TWO_TO_60)) rawAsULong(magnitude) else null
    }

    actual fun multiplyByUnsignedMagnitude(value: BigInteger, digit: ULong, digitSign: Int): BigInteger {
        require(digitSign != 0)
        require(digit != 0UL)
        if (value.signum() == 0) return ZERO
        if (digit == 1UL) return if (digitSign > 0) value else -value

        val productRaw = rawMultiply(value.raw, rawUnsignedLong(digit))
        return BigInteger(if (digitSign > 0) productRaw else rawNegate(productRaw))
    }

    actual fun multiplyCompactMagnitudes(left: ULong, right: ULong, sign: Int): BigInteger {
        require(sign != 0)
        if (left == 0UL || right == 0UL) return ZERO

        val magnitudeRaw = rawMultiply(rawUnsignedLong(left), rawUnsignedLong(right))
        return BigInteger(if (sign > 0) magnitudeRaw else rawNegate(magnitudeRaw))
    }

    actual fun magnitudeAsULongOrNull(value: BigInteger): ULong? {
        val magnitude = rawMagnitude(value.raw)
        return if (rawLessThan(magnitude, RAW_TWO_TO_64)) rawAsULong(magnitude) else null
    }

    actual fun divisionByDigitMagnitudeOrNull(value: BigInteger): ULong? = magnitudeAsULongOrNull(value)

    actual fun divideAndRemainderByDigit(
        value: BigInteger,
        divisor: ULong,
        divisorSign: Int,
    ): SmallDigitDivision {
        require(divisor != 0UL) { "Division by zero" }
        require(divisorSign != 0) { "Division by zero" }
        val divisorMagnitudeRaw = rawUnsignedLong(divisor)
        val divisorRaw = if (divisorSign < 0) rawNegate(divisorMagnitudeRaw) else divisorMagnitudeRaw
        val quotientRaw = rawDivide(value.raw, divisorRaw)
        val remainderRaw = rawSubtract(value.raw, rawMultiply(quotientRaw, divisorRaw))
        return SmallDigitDivision(BigInteger(quotientRaw), BigInteger(remainderRaw))
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

    actual fun bigIntegerOfUnsignedMagnitude(value: ULong): BigInteger = if (value == 0UL) {
        ZERO
    } else {
        BigInteger(rawUnsignedLong(value))
    }

    actual fun divideByPowerOfTen(
        value: BigInteger,
        power: Int,
        digitDivisor: ULong?,
        cachedDivisor: BigInteger?,
        addedBits: Long,
    ): PowerOfTenDivision {
        val divisorRaw = if (digitDivisor != null) {
            rawUnsignedLong(digitDivisor)
        } else {
            (cachedDivisor ?: bigIntegerOf(10).pow(power)).raw
        }
        val quotientRaw = rawDivide(value.raw, divisorRaw)
        val remainderRaw = rawSubtract(value.raw, rawMultiply(quotientRaw, divisorRaw))
        val doubledRemainderRaw = rawShiftLeft(rawMagnitude(remainderRaw), 1)
        return PowerOfTenDivision(
            BigInteger(quotientRaw),
            BigInteger(remainderRaw),
            rawCompare(doubledRemainderRaw, divisorRaw),
        )
    }

    actual fun magnitudeBitLength(value: BigInteger): Int = value.abs().bitLength()
}

private fun rawUnsignedLong(value: ULong): JsBigInt {
    val low = value.toInt()
    val high = (value shr 32).toInt()
    return rawUnsignedLong(high, low)
}

@Suppress("UnsafeCastFromDynamic")
private fun rawUnsignedLong(high: Int, low: Int): JsBigInt = js("(BigInt(high >>> 0) << BigInt(32)) + BigInt(low >>> 0)")

private val RAW_TWO_TO_60: JsBigInt = rawUnsignedLong(1UL shl 60)
private val RAW_TWO_TO_64: JsBigInt = rawShiftLeft(rawUnsignedLong(1UL), 64)

@Suppress("UnsafeCastFromDynamic")
private fun rawMagnitude(value: JsBigInt): JsBigInt = js("value < BigInt(0) ? -value : value")

@Suppress("UnsafeCastFromDynamic")
private fun rawLessThan(left: JsBigInt, right: JsBigInt): Boolean = js("left < right")

@Suppress("UnsafeCastFromDynamic")
private fun rawIsZero(value: JsBigInt): Boolean = js("value === BigInt(0)")

@Suppress("UnsafeCastFromDynamic")
private fun rawShiftLeft(value: JsBigInt, bits: Int): JsBigInt = js("value << BigInt(bits)")

private fun rawAsULong(value: JsBigInt): ULong {
    val low = rawLowSignedWord(value).toUInt().toULong()
    val high = rawLowSignedWord(rawShiftRight(value, 32)).toUInt().toULong()
    return (high shl 32) or low
}

@Suppress("UnsafeCastFromDynamic")
private fun rawShiftRight(value: JsBigInt, bits: Int): JsBigInt = js("value >> BigInt(bits)")

@Suppress("UnsafeCastFromDynamic")
private fun rawLowSignedWord(value: JsBigInt): Int = js("Number(BigInt.asIntN(32, value))")

@Suppress("UnsafeCastFromDynamic")
private fun rawNegate(value: JsBigInt): JsBigInt = js("-value")

@Suppress("UnsafeCastFromDynamic")
private fun rawDivide(left: JsBigInt, right: JsBigInt): JsBigInt = js("left / right")

@Suppress("UnsafeCastFromDynamic")
private fun rawMultiply(left: JsBigInt, right: JsBigInt): JsBigInt = js("left * right")

@Suppress("UnsafeCastFromDynamic")
private fun rawSubtract(left: JsBigInt, right: JsBigInt): JsBigInt = js("left - right")

private fun rawCompare(left: JsBigInt, right: JsBigInt): Int = when {
    rawLessThan(left, right) -> -1
    rawLessThan(right, left) -> 1
    else -> 0
}
