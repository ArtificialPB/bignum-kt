package io.github.artificialpb.bignum

expect class BigInteger : Comparable<BigInteger> {
    constructor(value: String)
    constructor(value: String, radix: Int)
    constructor(bytes: ByteArray)
    constructor(bytes: ByteArray, off: Int, len: Int)

    // Arithmetic
    fun add(other: BigInteger): BigInteger
    fun subtract(other: BigInteger): BigInteger
    fun multiply(other: BigInteger): BigInteger
    fun divide(other: BigInteger): BigInteger
    fun abs(): BigInteger
    fun pow(exponent: Int): BigInteger
    fun mod(modulus: BigInteger): BigInteger
    fun modPow(exponent: BigInteger, modulus: BigInteger): BigInteger
    fun modInverse(modulus: BigInteger): BigInteger
    fun gcd(other: BigInteger): BigInteger
    fun divideAndRemainder(other: BigInteger): Array<BigInteger>

    // Bitwise
    fun and(other: BigInteger): BigInteger
    fun or(other: BigInteger): BigInteger
    fun xor(other: BigInteger): BigInteger
    fun not(): BigInteger
    fun andNot(other: BigInteger): BigInteger
    fun shiftLeft(n: Int): BigInteger
    fun shiftRight(n: Int): BigInteger
    fun testBit(n: Int): Boolean
    fun setBit(n: Int): BigInteger
    fun clearBit(n: Int): BigInteger
    fun flipBit(n: Int): BigInteger
    fun getLowestSetBit(): Int
    fun bitLength(): Int
    fun bitCount(): Int

    // Predicates
    fun isProbablePrime(certainty: Int): Boolean
    fun nextProbablePrime(): BigInteger

    // Roots
    fun sqrt(): BigInteger

    // Conversions
    fun toByteArray(): ByteArray
    fun toInt(): Int
    fun toLong(): Long
    fun toDouble(): Double
    fun toString(radix: Int): String
    fun signum(): Int

    // Comparison
    fun min(other: BigInteger): BigInteger
    fun max(other: BigInteger): BigInteger

    override fun compareTo(other: BigInteger): Int
    override fun toString(): String
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

expect object BigIntegers {
    val ZERO: BigInteger
    val ONE: BigInteger
    val TWO: BigInteger
    val TEN: BigInteger

    fun of(value: String): BigInteger
    fun of(value: Long): BigInteger
}

// Operators
operator fun BigInteger.plus(other: BigInteger): BigInteger = add(other)
operator fun BigInteger.minus(other: BigInteger): BigInteger = subtract(other)
operator fun BigInteger.times(other: BigInteger): BigInteger = multiply(other)
operator fun BigInteger.div(other: BigInteger): BigInteger = divide(other)
expect operator fun BigInteger.rem(other: BigInteger): BigInteger
expect operator fun BigInteger.unaryMinus(): BigInteger

// inc/dec
expect operator fun BigInteger.inc(): BigInteger
expect operator fun BigInteger.dec(): BigInteger

// Range support for idiomatic loops: for (i in a..b)
operator fun BigInteger.rangeTo(other: BigInteger): BigIntegerRange =
    BigIntegerRange(this, other)

class BigIntegerRange(
    override val start: BigInteger,
    override val endInclusive: BigInteger,
) : ClosedRange<BigInteger>, Iterable<BigInteger> {
    override fun iterator(): Iterator<BigInteger> = object : Iterator<BigInteger> {
        private var current = start
        private var done = start > endInclusive

        override fun hasNext() = !done

        override fun next(): BigInteger {
            if (done) throw NoSuchElementException()
            val value = current
            if (current == endInclusive) {
                done = true
            } else {
                current++
            }
            return value
        }
    }
}

// Additional operations not on java.math.BigInteger
expect fun BigInteger.lcm(other: BigInteger): BigInteger
