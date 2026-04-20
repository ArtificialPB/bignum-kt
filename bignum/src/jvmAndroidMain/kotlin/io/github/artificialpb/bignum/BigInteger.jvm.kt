package io.github.artificialpb.bignum

actual typealias BigInteger = java.math.BigInteger

// Cached constants
private val ZERO = java.math.BigInteger.ZERO
private val ONE = java.math.BigInteger.ONE
private val TWO = java.math.BigInteger.TWO
private val TEN = java.math.BigInteger.TEN
private val HUNDRED = java.math.BigInteger.valueOf(100)

// Top-level factory functions
actual fun bigIntegerOf(value: String): BigInteger = when (value) {
    "0" -> ZERO
    "1" -> ONE
    "2" -> TWO
    "10" -> TEN
    "100" -> HUNDRED
    else -> java.math.BigInteger(value)
}

actual fun bigIntegerOf(value: Long): BigInteger = when (value) {
    0L -> ZERO
    1L -> ONE
    2L -> TWO
    10L -> TEN
    100L -> HUNDRED
    else -> java.math.BigInteger.valueOf(value)
}

actual fun bigIntegerOf(value: Int): BigInteger = when (value) {
    0 -> ZERO
    1 -> ONE
    2 -> TWO
    10 -> TEN
    100 -> HUNDRED
    else -> java.math.BigInteger.valueOf(value.toLong())
}

// Operators
actual operator fun BigInteger.rem(other: BigInteger): BigInteger = this.remainder(other)
actual operator fun BigInteger.unaryMinus(): BigInteger = this.negate()

// inc/dec
actual operator fun BigInteger.inc(): BigInteger = this.add(java.math.BigInteger.ONE)
actual operator fun BigInteger.dec(): BigInteger = this.subtract(java.math.BigInteger.ONE)

// Additional operations
actual fun BigInteger.lcm(other: BigInteger): BigInteger {
    if (this.signum() == 0 || other.signum() == 0) return java.math.BigInteger.ZERO
    return (this / this.gcd(other)) * other
}
