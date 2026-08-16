package io.github.artificialpb.bignum

/** Kotlin/JS implementation backed by the host's arbitrary-precision ECMAScript BigInt. */
actual class BigInteger internal constructor(
    internal val raw: dynamic,
    @Suppress("UNUSED_PARAMETER") trusted: Boolean,
) : Number(), Comparable<BigInteger> {
    actual constructor(value: String) : this(parseBigInteger(value, 10), true)

    actual constructor(value: String, radix: Int) : this(parseBigInteger(value, radix), true)

    actual constructor(bytes: ByteArray) : this(parseTwosComplement(bytes, 0, bytes.size), true)

    actual constructor(bytes: ByteArray, off: Int, len: Int) : this(parseTwosComplement(bytes, off, len), true)

    actual constructor(signum: Int, magnitude: ByteArray) : this(
        parseSignMagnitude(signum, magnitude, 0, magnitude.size),
        true,
    )

    actual constructor(signum: Int, magnitude: ByteArray, off: Int, len: Int) : this(
        parseSignMagnitude(signum, magnitude, off, len),
        true,
    )

    actual fun add(other: BigInteger): BigInteger = fromRaw(rawAdd(raw, other.raw))

    actual fun subtract(other: BigInteger): BigInteger = fromRaw(rawSubtract(raw, other.raw))

    actual fun multiply(other: BigInteger): BigInteger = fromRaw(rawMultiply(raw, other.raw))

    actual fun divide(other: BigInteger): BigInteger {
        requireNonZero(other)
        return fromRaw(rawDivide(raw, other.raw))
    }

    actual fun abs(): BigInteger = if (signum() >= 0) this else fromRaw(rawNegate(raw))

    actual fun negate(): BigInteger = if (signum() == 0) this else fromRaw(rawNegate(raw))

    actual fun pow(exponent: Int): BigInteger {
        if (exponent < 0) throw ArithmeticException("Negative exponent")
        if (exponent == 0) return ONE
        if (exponent == 1 || signum() == 0) return this

        val estimatedBits = bitLength().toLong() * exponent.toLong()
        if (estimatedBits > MAX_BIT_LENGTH) {
            throw ArithmeticException("BigInteger would overflow supported range")
        }
        return fromRaw(rawPow(raw, exponent))
    }

    actual fun mod(modulus: BigInteger): BigInteger {
        if (modulus.signum() <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        val remainder = rawRemainder(raw, modulus.raw)
        return fromRaw(if (rawIsNegative(remainder)) rawAdd(remainder, modulus.raw) else remainder)
    }

    actual fun modPow(exponent: BigInteger, modulus: BigInteger): BigInteger {
        if (modulus.signum() <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        if (modulus == ONE) return ZERO
        val base = if (exponent.signum() < 0) modInverse(modulus) else this
        return fromRaw(rawModPow(base.raw, exponent.abs().raw, modulus.raw))
    }

    actual fun modInverse(modulus: BigInteger): BigInteger {
        if (modulus.signum() <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        if (modulus == ONE) return ZERO

        var oldR = mod(modulus).raw
        var r = modulus.raw
        var oldS = RAW_ONE
        var s = RAW_ZERO
        while (!rawIsZero(r)) {
            val quotient = rawDivide(oldR, r)
            val nextR = rawSubtract(oldR, rawMultiply(quotient, r))
            oldR = r
            r = nextR
            val nextS = rawSubtract(oldS, rawMultiply(quotient, s))
            oldS = s
            s = nextS
        }
        if (!rawEquals(oldR, RAW_ONE)) throw ArithmeticException("BigInteger not invertible")
        if (rawIsNegative(oldS)) oldS = rawAdd(oldS, modulus.raw)
        return fromRaw(oldS)
    }

    actual fun gcd(other: BigInteger): BigInteger {
        var left = abs().raw
        var right = other.abs().raw
        while (!rawIsZero(right)) {
            val remainder = rawRemainder(left, right)
            left = right
            right = remainder
        }
        return fromRaw(left)
    }

    actual fun divideAndRemainder(other: BigInteger): Array<BigInteger> {
        requireNonZero(other)
        return arrayOf(fromRaw(rawDivide(raw, other.raw)), fromRaw(rawRemainder(raw, other.raw)))
    }

    actual fun and(other: BigInteger): BigInteger = fromRaw(rawAnd(raw, other.raw))

    actual fun or(other: BigInteger): BigInteger = fromRaw(rawOr(raw, other.raw))

    actual fun xor(other: BigInteger): BigInteger = fromRaw(rawXor(raw, other.raw))

    actual fun not(): BigInteger = fromRaw(rawNot(raw))

    actual fun andNot(other: BigInteger): BigInteger = fromRaw(rawAnd(raw, rawNot(other.raw)))

    actual fun shiftLeft(n: Int): BigInteger {
        if (n < 0) {
            if (n == Int.MIN_VALUE) return if (signum() < 0) MINUS_ONE else ZERO
            return shiftRight(-n)
        }
        if (signum() == 0 || n == 0) return this
        if (bitLength().toLong() + n.toLong() > MAX_BIT_LENGTH) {
            throw ArithmeticException("BigInteger would overflow supported range")
        }
        return fromRaw(rawShiftLeft(raw, rawBigInt(n.toString())))
    }

    actual fun shiftRight(n: Int): BigInteger {
        if (n < 0) {
            if (n == Int.MIN_VALUE) {
                if (signum() == 0) return ZERO
                throw ArithmeticException("Shift amount too large")
            }
            return shiftLeft(-n)
        }
        if (signum() == 0 || n == 0) return this
        if (n >= bitLength() + 1) return if (signum() < 0) MINUS_ONE else ZERO
        return fromRaw(rawShiftRight(raw, rawBigInt(n.toString())))
    }

    actual fun testBit(n: Int): Boolean {
        requireBitIndex(n)
        return rawEquals(rawAnd(rawShiftRight(raw, rawBigInt(n.toString())), RAW_ONE), RAW_ONE)
    }

    actual fun setBit(n: Int): BigInteger {
        requireBitIndex(n)
        return fromRaw(rawOr(raw, rawShiftLeft(RAW_ONE, rawBigInt(n.toString()))))
    }

    actual fun clearBit(n: Int): BigInteger {
        requireBitIndex(n)
        return fromRaw(rawAnd(raw, rawNot(rawShiftLeft(RAW_ONE, rawBigInt(n.toString())))))
    }

    actual fun flipBit(n: Int): BigInteger {
        requireBitIndex(n)
        return fromRaw(rawXor(raw, rawShiftLeft(RAW_ONE, rawBigInt(n.toString()))))
    }

    actual fun getLowestSetBit(): Int {
        if (signum() == 0) return -1
        var value = if (signum() < 0) rawNegate(raw) else raw
        var count = 0
        while (rawEquals(rawAnd(value, RAW_WORD_MASK), RAW_ZERO)) {
            value = rawShiftRight(value, RAW_WORD_BITS)
            count += 32
        }
        val lowWord = rawLowUnsignedInt(value)
        return count + lowWord.countTrailingZeroBits()
    }

    actual fun bitLength(): Int {
        if (signum() == 0) return 0
        val magnitude = if (signum() < 0) rawSubtract(rawNegate(raw), RAW_ONE) else raw
        if (rawIsZero(magnitude)) return 0
        return rawToString(magnitude, 2).length
    }

    actual fun bitCount(): Int {
        var value = if (signum() < 0) rawSubtract(rawNegate(raw), RAW_ONE) else raw
        var count = 0
        while (!rawIsZero(value)) {
            count += rawLowUnsignedInt(value).countOneBits()
            value = rawShiftRight(value, RAW_WORD_BITS)
        }
        return count
    }

    actual fun isProbablePrime(certainty: Int): Boolean {
        if (certainty <= 0) return true
        val candidate = abs()
        if (candidate < TWO) return false
        for (prime in SMALL_PRIMES) {
            val smallPrime = bigIntegerOf(prime)
            if (candidate == smallPrime) return true
            if ((candidate % smallPrime).signum() == 0) return false
        }
        if (!candidate.testBit(0)) return false

        val rounds = primeTrialsForCertainty(certainty, candidate.bitLength())
        val minusOne = candidate - ONE
        val powersOfTwo = minusOne.getLowestSetBit()
        val oddPart = minusOne.shiftRight(powersOfTwo)
        for (index in 0 until rounds) {
            val base = millerRabinBase(index).mod(candidate - TWO) + TWO
            var witness = base.modPow(oddPart, candidate)
            if (witness == ONE || witness == minusOne) continue
            var passed = false
            for (round in 1 until powersOfTwo) {
                witness = witness.multiply(witness).mod(candidate)
                if (witness == minusOne) {
                    passed = true
                    break
                }
                if (witness == ONE) return false
            }
            if (!passed) return false
        }
        return true
    }

    actual fun nextProbablePrime(): BigInteger {
        if (signum() < 0) throw ArithmeticException("start < 0: $this")
        if (this < TWO) return TWO
        var candidate = this + ONE
        if (!candidate.testBit(0)) candidate += ONE
        while (!candidate.isProbablePrime(DEFAULT_PRIME_CERTAINTY)) {
            candidate += TWO
        }
        return candidate
    }

    actual fun sqrt(): BigInteger {
        if (signum() < 0) throw ArithmeticException("Negative BigInteger")
        if (signum() == 0) return ZERO
        var estimate = ONE.shiftLeft((bitLength() + 1) / 2)
        while (true) {
            val next = (estimate + divide(estimate)).shiftRight(1)
            if (next >= estimate) return estimate
            estimate = next
        }
    }

    actual fun toByteArray(): ByteArray {
        if (signum() == 0) return byteArrayOf(0)
        val byteCount = bitLength() / 8 + 1
        var encoded = if (signum() < 0) {
            rawAdd(raw, rawShiftLeft(RAW_ONE, rawBigInt((byteCount * 8).toString())))
        } else {
            raw
        }
        return ByteArray(byteCount).also { result ->
            for (index in result.lastIndex downTo 0) {
                result[index] = rawLowByte(encoded).toByte()
                encoded = rawShiftRight(encoded, RAW_EIGHT)
            }
        }
    }

    override fun toByte(): Byte = toInt().toByte()

    override fun toShort(): Short = toInt().toShort()

    actual override fun toInt(): Int = rawAsInt(raw)

    actual override fun toLong(): Long = rawToString(rawAsInt64(raw), 10).toLong()

    override fun toFloat(): Float = toString().toFloat()

    actual override fun toDouble(): Double = rawAsDouble(raw)

    actual fun toString(radix: Int): String = rawToString(raw, if (radix in 2..36) radix else 10)

    actual fun signum(): Int = when {
        rawIsNegative(raw) -> -1
        rawIsZero(raw) -> 0
        else -> 1
    }

    actual fun min(other: BigInteger): BigInteger = if (this <= other) this else other

    actual fun max(other: BigInteger): BigInteger = if (this >= other) this else other

    actual override fun compareTo(other: BigInteger): Int = when {
        rawLessThan(raw, other.raw) -> -1
        rawGreaterThan(raw, other.raw) -> 1
        else -> 0
    }

    actual override fun toString(): String = rawToString(raw, 10)

    actual override fun equals(other: Any?): Boolean = other is BigInteger && rawEquals(raw, other.raw)

    actual override fun hashCode(): Int {
        if (signum() == 0) return 0
        var magnitude = if (signum() < 0) rawNegate(raw) else raw
        val words = mutableListOf<Int>()
        while (!rawIsZero(magnitude)) {
            words += rawAsInt(magnitude)
            magnitude = rawShiftRight(magnitude, RAW_WORD_BITS)
        }
        var result = 0
        for (index in words.indices.reversed()) {
            result = 31 * result + words[index]
        }
        return result * signum()
    }
}

actual fun bigIntegerOf(value: String): BigInteger = when (value) {
    "0" -> ZERO
    "1" -> ONE
    "2" -> TWO
    "10" -> TEN
    "100" -> HUNDRED
    else -> BigInteger(value)
}

actual fun bigIntegerOf(value: Long): BigInteger = when (value) {
    0L -> ZERO
    1L -> ONE
    2L -> TWO
    10L -> TEN
    100L -> HUNDRED
    else -> fromRaw(rawBigInt(value.toString()))
}

actual fun bigIntegerOf(value: Int): BigInteger = when (value) {
    0 -> ZERO
    1 -> ONE
    2 -> TWO
    10 -> TEN
    100 -> HUNDRED
    else -> fromRaw(rawBigInt(value.toString()))
}

actual operator fun BigInteger.rem(other: BigInteger): BigInteger {
    requireNonZero(other)
    return fromRaw(rawRemainder(raw, other.raw))
}

actual operator fun BigInteger.unaryMinus(): BigInteger = negate()

actual operator fun BigInteger.inc(): BigInteger = add(ONE)

actual operator fun BigInteger.dec(): BigInteger = subtract(ONE)

actual fun BigInteger.lcm(other: BigInteger): BigInteger {
    if (signum() == 0 || other.signum() == 0) return ZERO
    return (this / gcd(other)) * other
}

private const val MAX_BIT_LENGTH = Int.MAX_VALUE.toLong()
private const val DEFAULT_PRIME_CERTAINTY = 100
private val SMALL_PRIMES = intArrayOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37)
private val MILLER_RABIN_BASES = intArrayOf(
    2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53,
    59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 103, 107, 109, 113,
    127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181,
    191, 193, 197, 199, 211, 223, 227, 229,
)

private val RAW_ZERO: dynamic = rawBigInt("0")
private val RAW_ONE: dynamic = rawBigInt("1")
private val RAW_EIGHT: dynamic = rawBigInt("8")
private val RAW_WORD_BITS: dynamic = rawBigInt("32")
private val RAW_WORD_MASK: dynamic = rawBigInt("4294967295")

internal actual val ZERO = fromRaw(RAW_ZERO)
internal actual val ONE = fromRaw(RAW_ONE)
private val TWO = fromRaw(rawBigInt("2"))
private val TEN = fromRaw(rawBigInt("10"))
private val HUNDRED = fromRaw(rawBigInt("100"))
internal actual val MINUS_ONE = fromRaw(rawBigInt("-1"))

internal actual fun parseDecimalBigIntegerSkippingIndex(
    value: String,
    digitsStart: Int,
    digitsEnd: Int,
    skippedIndex: Int,
    sign: Int,
    digitCount: Int,
): BigInteger {
    if (digitCount == 0) return ZERO
    val digits = buildString(digitCount + 1) {
        if (sign < 0) append('-')
        for (index in digitsStart until digitsEnd) {
            if (index != skippedIndex) append(value[index])
        }
    }
    return BigInteger(digits)
}

internal actual fun compareMagnitudes(left: BigInteger, right: BigInteger): Int = left.abs().compareTo(right.abs())

private fun fromRaw(value: dynamic): BigInteger = BigInteger(value, true)

private fun requireNonZero(value: BigInteger) {
    if (value.signum() == 0) throw ArithmeticException("BigInteger divide by zero")
}

private fun requireBitIndex(index: Int) {
    if (index < 0) throw ArithmeticException("Negative bit address")
}

private fun parseBigInteger(value: String, radix: Int): dynamic {
    if (radix !in 2..36) throw NumberFormatException("Radix out of range: $radix")
    if (value.isEmpty()) throw NumberFormatException("Zero length BigInteger")
    var index = 0
    var negative = false
    val minusIndex = value.lastIndexOf('-')
    val plusIndex = value.lastIndexOf('+')
    if (minusIndex >= 0) {
        if (minusIndex != 0 || plusIndex >= 0) {
            throw NumberFormatException("Illegal embedded sign character")
        }
        negative = true
        index = 1
    } else if (plusIndex >= 0) {
        if (plusIndex != 0) {
            throw NumberFormatException("Illegal embedded sign character")
        }
        index = 1
    }
    if (index == value.length) throw NumberFormatException("Zero length BigInteger")

    val rawRadix = rawBigInt(radix.toString())
    var result = RAW_ZERO
    while (index < value.length) {
        val digit = value[index].digitToIntOrNull(radix) ?: throw NumberFormatException(
            if (radix == 10) "For input string: \"$value\"" else "For input string: \"$value\" under radix $radix",
        )
        result = rawAdd(rawMultiply(result, rawRadix), rawBigInt(digit.toString()))
        index++
    }
    return if (negative && !rawIsZero(result)) rawNegate(result) else result
}

private fun checkSlice(bytes: ByteArray, off: Int, len: Int) {
    if (off < 0 || len < 0 || off.toLong() + len.toLong() > bytes.size.toLong()) {
        throw IndexOutOfBoundsException("Range [$off, ${off.toLong() + len.toLong()}) out of bounds for length ${bytes.size}")
    }
}

private fun parseTwosComplement(bytes: ByteArray, off: Int, len: Int): dynamic {
    checkSlice(bytes, off, len)
    if (bytes.isEmpty()) throw NumberFormatException("Zero length BigInteger")
    if (len == 0) {
        if (bytes[off] >= 0) return RAW_ZERO
        val magnitude = -(((bytes[off - 1].toInt().inv()) and 0xFF) + 1).toLong()
        return rawBigInt(magnitude.toString())
    }
    var result = RAW_ZERO
    for (index in off until off + len) {
        result = rawAdd(rawShiftLeft(result, RAW_EIGHT), rawBigInt((bytes[index].toInt() and 0xFF).toString()))
    }
    if (bytes[off] < 0) {
        result = rawSubtract(result, rawShiftLeft(RAW_ONE, rawBigInt((len * 8).toString())))
    }
    return result
}

private fun parseSignMagnitude(signum: Int, magnitude: ByteArray, off: Int, len: Int): dynamic {
    if (signum !in -1..1) throw NumberFormatException("Invalid signum value")
    checkSlice(magnitude, off, len)
    var result = RAW_ZERO
    for (index in off until off + len) {
        result = rawAdd(rawShiftLeft(result, RAW_EIGHT), rawBigInt((magnitude[index].toInt() and 0xFF).toString()))
    }
    if (rawIsZero(result)) return result
    if (signum == 0) throw NumberFormatException("signum-magnitude mismatch")
    return if (signum < 0) rawNegate(result) else result
}

private fun rawModPow(baseValue: dynamic, exponentValue: dynamic, modulus: dynamic): dynamic {
    var result = RAW_ONE
    var base = rawRemainder(baseValue, modulus)
    if (rawIsNegative(base)) base = rawAdd(base, modulus)
    var exponent = exponentValue
    while (!rawIsZero(exponent)) {
        if (rawEquals(rawAnd(exponent, RAW_ONE), RAW_ONE)) {
            result = rawRemainder(rawMultiply(result, base), modulus)
        }
        exponent = rawShiftRight(exponent, RAW_ONE)
        if (!rawIsZero(exponent)) base = rawRemainder(rawMultiply(base, base), modulus)
    }
    return result
}

private fun primeTrialsForCertainty(certainty: Int, bitLength: Int): Int {
    val halfCertainty = ((certainty.coerceAtMost(Int.MAX_VALUE - 1)) + 1) / 2
    val maxTrials = when {
        bitLength < 100 -> 50
        bitLength < 256 -> 27
        bitLength < 512 -> 15
        bitLength < 768 -> 8
        bitLength < 1024 -> 4
        else -> 2
    }
    return minOf(halfCertainty, maxTrials).coerceAtLeast(1)
}

private fun millerRabinBase(index: Int): BigInteger = bigIntegerOf(MILLER_RABIN_BASES[index % MILLER_RABIN_BASES.size])

@Suppress("UnsafeCastFromDynamic")
private fun rawBigInt(stringValue: String): dynamic = js("BigInt(stringValue)")

@Suppress("UnsafeCastFromDynamic")
private fun rawAdd(left: dynamic, right: dynamic): dynamic = js("left + right")

@Suppress("UnsafeCastFromDynamic")
private fun rawSubtract(left: dynamic, right: dynamic): dynamic = js("left - right")

@Suppress("UnsafeCastFromDynamic")
private fun rawMultiply(left: dynamic, right: dynamic): dynamic = js("left * right")

@Suppress("UnsafeCastFromDynamic")
private fun rawDivide(left: dynamic, right: dynamic): dynamic = js("left / right")

@Suppress("UnsafeCastFromDynamic")
private fun rawRemainder(left: dynamic, right: dynamic): dynamic = js("left % right")

private fun rawPow(base: dynamic, exponent: Int): dynamic {
    var result = RAW_ONE
    var factor = base
    var remaining = exponent
    while (remaining != 0) {
        if (remaining and 1 != 0) result = rawMultiply(result, factor)
        remaining = remaining ushr 1
        if (remaining != 0) factor = rawMultiply(factor, factor)
    }
    return result
}

@Suppress("UnsafeCastFromDynamic")
private fun rawNegate(value: dynamic): dynamic = js("-value")

@Suppress("UnsafeCastFromDynamic")
private fun rawAnd(left: dynamic, right: dynamic): dynamic = js("left & right")

@Suppress("UnsafeCastFromDynamic")
private fun rawOr(left: dynamic, right: dynamic): dynamic = js("left | right")

@Suppress("UnsafeCastFromDynamic")
private fun rawXor(left: dynamic, right: dynamic): dynamic = js("left ^ right")

@Suppress("UnsafeCastFromDynamic")
private fun rawNot(value: dynamic): dynamic = js("~value")

@Suppress("UnsafeCastFromDynamic")
private fun rawShiftLeft(value: dynamic, bits: dynamic): dynamic = js("value << bits")

@Suppress("UnsafeCastFromDynamic")
private fun rawShiftRight(value: dynamic, bits: dynamic): dynamic = js("value >> bits")

@Suppress("UnsafeCastFromDynamic")
private fun rawEquals(left: dynamic, right: dynamic): Boolean = js("left === right")

@Suppress("UnsafeCastFromDynamic")
private fun rawLessThan(left: dynamic, right: dynamic): Boolean = js("left < right")

@Suppress("UnsafeCastFromDynamic")
private fun rawGreaterThan(left: dynamic, right: dynamic): Boolean = js("left > right")

private fun rawIsZero(value: dynamic): Boolean = rawEquals(value, RAW_ZERO)

private fun rawIsNegative(value: dynamic): Boolean = rawLessThan(value, RAW_ZERO)

@Suppress("UnsafeCastFromDynamic")
private fun rawToString(value: dynamic, radix: Int): String = js("value.toString(radix)")

@Suppress("UnsafeCastFromDynamic")
private fun rawAsInt(value: dynamic): Int = js("Number(BigInt.asIntN(32, value))")

@Suppress("UnsafeCastFromDynamic")
private fun rawAsInt64(value: dynamic): dynamic = js("BigInt.asIntN(64, value)")

@Suppress("UnsafeCastFromDynamic")
private fun rawAsDouble(value: dynamic): Double = js("Number(value)")

@Suppress("UnsafeCastFromDynamic")
private fun rawLowUnsignedInt(value: dynamic): Int = js("Number(BigInt.asIntN(32, value))")

@Suppress("UnsafeCastFromDynamic")
private fun rawLowByte(value: dynamic): Int = js("Number(BigInt.asUintN(8, value))")
