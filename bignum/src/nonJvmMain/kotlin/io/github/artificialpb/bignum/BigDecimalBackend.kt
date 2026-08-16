package io.github.artificialpb.bignum

internal expect object BigDecimalBackend {
    fun multiplyByPowerOfTen(value: BigInteger, power: Int, addedBits: Long): BigInteger

    fun stripSmallFactor(value: BigInteger, factor: ULong, maxCount: Int): SmallFactorReduction

    fun singleLimbMagnitudeOrNull(value: BigInteger): ULong?

    fun multiplyByUnsignedMagnitude(value: BigInteger, digit: ULong, digitSign: Int): BigInteger

    fun multiplyCompactMagnitudes(left: ULong, right: ULong, sign: Int): BigInteger

    fun magnitudeAsULongOrNull(value: BigInteger): ULong?

    fun divisionByDigitMagnitudeOrNull(value: BigInteger): ULong?

    fun divideAndRemainderByDigit(value: BigInteger, divisor: ULong, divisorSign: Int): SmallDigitDivision

    fun divideExactQuotientOrNull(dividend: BigInteger, divisor: BigInteger): BigInteger?

    fun scaledDigitMagnitudeOrNull(value: BigInteger, factor: ULong): ULong?

    fun divideAndRemainderByDigitWithScaledQuotient(
        value: BigInteger,
        divisor: ULong,
        divisorSign: Int,
        quotientScaleFactor: ULong,
    ): SmallDigitDivision

    fun bigIntegerOfUnsignedMagnitude(value: ULong): BigInteger

    fun divideByPowerOfTen(
        value: BigInteger,
        power: Int,
        digitDivisor: ULong?,
        cachedDivisor: BigInteger?,
        addedBits: Long,
    ): PowerOfTenDivision

    fun magnitudeBitLength(value: BigInteger): Int
}
