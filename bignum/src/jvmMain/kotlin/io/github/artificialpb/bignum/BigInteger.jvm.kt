package io.github.artificialpb.bignum

actual typealias BigInteger = java.math.BigInteger

actual object BigIntegers {
    actual val ZERO: BigInteger = java.math.BigInteger.ZERO
    actual val ONE: BigInteger = java.math.BigInteger.ONE
    actual val TWO: BigInteger = java.math.BigInteger.TWO
    actual val TEN: BigInteger = java.math.BigInteger.TEN

    actual fun of(value: String): BigInteger = java.math.BigInteger(value)
    actual fun of(value: Long): BigInteger = java.math.BigInteger.valueOf(value)
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
