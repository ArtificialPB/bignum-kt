package io.github.artificialpb.bignum

internal expect val ZERO: BigInteger
internal expect val ONE: BigInteger
internal expect val MINUS_ONE: BigInteger

internal expect fun parseDecimalBigIntegerSkippingIndex(
    value: String,
    digitsStart: Int,
    digitsEnd: Int,
    skippedIndex: Int,
    sign: Int,
    digitCount: Int,
): BigInteger

internal expect fun compareMagnitudes(left: BigInteger, right: BigInteger): Int
