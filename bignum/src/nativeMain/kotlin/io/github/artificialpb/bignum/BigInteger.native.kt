package io.github.artificialpb.bignum

import io.github.artificialpb.bignum.tommath.*
import kotlinx.cinterop.*

private class BigIntegerConstruction(
    val sign: Int,
    val size: Int,
    val limbs: ULongArray,
)

@OptIn(ExperimentalForeignApi::class)
actual class BigInteger internal constructor(
    internal val sign: Int,
    internal val size: Int,
    internal val limbs: ULongArray,
) : Comparable<BigInteger> {
    private constructor(construction: BigIntegerConstruction) : this(
        construction.sign,
        construction.size,
        construction.limbs,
    )

    init {
        require(sign in -1..1) { "Invalid signum: $sign" }
        require(size in 0..limbs.size) { "Invalid magnitude size: $size for capacity ${limbs.size}" }
        if (sign == 0) {
            require(size == 0) { "Zero must have empty logical magnitude" }
        } else {
            require(size > 0) { "Non-zero BigInteger must have non-empty logical magnitude" }
            require(limbs[size - 1] != 0UL) { "Magnitude is not normalized" }
        }
    }

    internal constructor(handle: CPointer<mp_int>) : this(
        signumFromHandle(handle),
        handle.pointed.used,
        copyCanonicalLimbs(handle),
    ) {
        freeMp(handle)
    }

    actual constructor(value: String) : this(constructFromString(value, 10))

    actual constructor(value: String, radix: Int) : this(constructFromString(value, radix))

    actual constructor(bytes: ByteArray) : this(constructFromTwosComplement(bytes))

    actual constructor(bytes: ByteArray, off: Int, len: Int) : this(
        constructFromTwosComplementSlice(bytes, off, len)
    )

    // Arithmetic

    actual fun add(other: BigInteger): BigInteger {
        if (other.sign == 0) return this
        if (sign == 0) return other
        return if (sign == other.sign) {
            addAbsolute(sign, this, other)
        } else {
            when (compareMagnitudes(this, other)) {
                0 -> ZERO
                1 -> subtractAbsolute(sign, this, other)
                else -> subtractAbsolute(other.sign, other, this)
            }
        }
    }

    actual fun subtract(other: BigInteger): BigInteger {
        if (other.sign == 0) return this
        if (sign == 0) return -other
        return if (sign == other.sign) {
            when (compareMagnitudes(this, other)) {
                0 -> ZERO
                1 -> subtractAbsolute(sign, this, other)
                else -> subtractAbsolute(-sign, other, this)
            }
        } else {
            addAbsolute(sign, this, other)
        }
    }

    actual fun multiply(other: BigInteger): BigInteger {
        if (sign == 0 || other.sign == 0) return ZERO
        if (this == ONE) return other
        if (other == ONE) return this
        if (this == MINUS_ONE) return -other
        if (other == MINUS_ONE) return -this
        val resultSign = sign * other.sign
        if (size + other.size <= SCHOOLBOOK_MUL_THRESHOLD) {
            return multiplySmall(resultSign, this, other)
        }
        return withBorrowedHandles(this, other) { leftHandle, rightHandle ->
            val result = allocMp()
            checkMp(mp_mul(leftHandle, rightHandle, result), result)
            result.toBigInteger()
        }
    }

    actual fun divide(other: BigInteger): BigInteger {
        if (other.sign == 0) throw ArithmeticException("BigInteger divide by zero")
        if (sign == 0) return ZERO
        if (other == ONE) return this
        if (other == MINUS_ONE) return -this
        if (size <= SCHOOLBOOK_DIV_THRESHOLD && other.size <= SCHOOLBOOK_DIV_THRESHOLD) {
            return divideSmall(sign * other.sign, this, other)
        }
        return withBorrowedHandles(this, other) { leftHandle, rightHandle ->
            val result = allocMp()
            checkMp(mp_div(leftHandle, rightHandle, result, null), result)
            result.toBigInteger()
        }
    }

    actual fun abs(): BigInteger =
        if (sign >= 0) this else BigInteger(1, size, limbs)

    actual fun pow(exponent: Int): BigInteger {
        when {
            exponent < 0 -> throw ArithmeticException("Negative exponent")
            exponent == 0 -> return ONE
            exponent == 1 -> return this
            sign == 0 -> return ZERO
        }

        // Match JVM overflow checks (java.math.BigInteger.pow):
        // Factor out trailing zeros, check the shift, then check the odd part.
        val partToSquare = abs()
        val powersOfTwo = partToSquare.getLowestSetBit().toLong()
        val bitsToShift = powersOfTwo * exponent.toLong()
        if (bitsToShift > MAX_BIT_LENGTH) {
            throw ArithmeticException("BigInteger would overflow supported range")
        }

        // Bit length of the base after stripping trailing zeros.
        val oddPart = if (powersOfTwo > 0) partToSquare.shiftRight(powersOfTwo.toInt()) else partToSquare
        val remainingBits = oddPart.bitLength().toLong()

        // JVM early-return: if the odd part is 1 (i.e. base is ±2^k),
        // the result is just a shift — delegate to shiftLeft which has
        // its own overflow guard.
        if (remainingBits == 1L) {
            return if (sign < 0 && exponent % 2 == 1) {
                MINUS_ONE.shiftLeft(bitsToShift.toInt())
            } else {
                ONE.shiftLeft(bitsToShift.toInt())
            }
        }

        // JVM: (long)remainingBits * exponent / Integer.SIZE > MAX_MAG_LENGTH
        if (remainingBits * exponent.toLong() / 32 > MAX_MAG_LENGTH) {
            throw ArithmeticException("BigInteger would overflow supported range")
        }

        // Binary exponentiation using our multiply (which uses fast schoolbook for small operands)
        val resultSign = if (sign < 0 && exponent % 2 == 1) -1 else 1
        val base = abs()
        var result = ONE
        var power = base
        var exp = exponent
        while (exp > 0) {
            if (exp and 1 == 1) {
                result = result.multiply(power)
            }
            exp = exp shr 1
            if (exp > 0) {
                power = power.multiply(power)
            }
        }
        return if (resultSign < 0) -result else result
    }

    actual fun mod(modulus: BigInteger): BigInteger {
        if (modulus.sign <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        if (sign == 0) return ZERO
        if (size <= 2 && modulus.size <= 2) {
            val r = remainderSmall(sign, this, modulus)
            // mod returns non-negative: if remainder is negative, add modulus
            return if (r.sign < 0) r.add(modulus) else r
        }
        return withBorrowedHandles(this, modulus) { handle, modulusHandle ->
            val result = allocMp()
            checkMp(mp_mod(handle, modulusHandle, result), result)
            result.toBigInteger()
        }
    }

    actual fun modPow(exponent: BigInteger, modulus: BigInteger): BigInteger {
        if (modulus.sign <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        // x^e mod 1 == 0 for all x, e
        if (modulus == ONE) return ZERO
        if (exponent.sign < 0) {
            // JVM semantics: modPow(negExp, mod) = modInverse(this, mod)^|negExp| mod mod
            val inverse = modInverse(modulus)
            val absExponent = exponent.abs()
            return withBorrowedHandles(inverse, absExponent, modulus) { inverseHandle, exponentHandle, modulusHandle ->
                val result = allocMp()
                checkMp(mp_exptmod(inverseHandle, exponentHandle, modulusHandle, result), result)
                result.toBigInteger()
            }
        }
        return withBorrowedHandles(this, exponent, modulus) { handle, exponentHandle, modulusHandle ->
            val result = allocMp()
            checkMp(mp_exptmod(handle, exponentHandle, modulusHandle, result), result)
            result.toBigInteger()
        }
    }

    actual fun modInverse(modulus: BigInteger): BigInteger {
        if (modulus.sign <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        // JVM: x.modInverse(1) == 0 for any x
        if (modulus == ONE) return ZERO
        val positiveBase = abs()
        val inverse = withBorrowedHandles(positiveBase, modulus) { baseHandle, modulusHandle ->
            val result = allocMp()
            val err = mp_invmod(baseHandle, modulusHandle, result)
            if (err != MP_OKAY) {
                freeMp(result)
                throw ArithmeticException("BigInteger not invertible")
            }
            result.toBigInteger()
        }
        // For negative input: (-a)^-1 mod m = m - (a^-1 mod m), when a^-1 != 0
        if (sign < 0 && inverse.sign != 0) {
            return modulus - inverse
        }
        return inverse
    }

    actual fun divideAndRemainder(other: BigInteger): Array<BigInteger> {
        if (other.sign == 0) throw ArithmeticException("BigInteger divide by zero")
        if (sign == 0) return arrayOf(ZERO, ZERO)
        if (size <= SCHOOLBOOK_DIV_THRESHOLD && other.size <= SCHOOLBOOK_DIV_THRESHOLD) {
            val (q, r) = divRemMagnitude(this, other)
            val qSign = if (q.sign == 0) 0 else sign * other.sign
            val rSign = if (r.sign == 0) 0 else sign
            return arrayOf(
                if (qSign == 0) ZERO else BigInteger(qSign, q.size, q.limbs),
                if (rSign == 0) ZERO else BigInteger(rSign, r.size, r.limbs),
            )
        }
        return withBorrowedHandles(this, other) { handle, otherHandle ->
            val quotient = allocMp()
            val remainder = allocMp()
            checkMp(mp_div(handle, otherHandle, quotient, remainder), quotient, remainder)
            arrayOf(quotient.toBigInteger(), remainder.toBigInteger())
        }
    }

    actual fun gcd(other: BigInteger): BigInteger {
        if (sign == 0) return other.abs()
        if (other.sign == 0) return abs()
        return withBorrowedHandles(this, other) { handle, otherHandle ->
            val result = allocMp()
            checkMp(mp_gcd(handle, otherHandle, result), result)
            result.toBigInteger()
        }
    }

    // Bitwise

    actual fun and(other: BigInteger): BigInteger {
        if (sign == 0 || other.sign == 0) return ZERO
        if (this === other) return this
        return when {
            sign > 0 && other.sign > 0 -> andPositive(this, other)
            sign > 0 -> andPositiveNegative(this, other)
            other.sign > 0 -> andPositiveNegative(other, this)
            else -> andNegativeNegative(this, other)
        }
    }

    actual fun or(other: BigInteger): BigInteger {
        if (sign == 0) return other
        if (other.sign == 0) return this
        if (this === other) return this
        return when {
            sign > 0 && other.sign > 0 -> orPositive(this, other)
            sign > 0 -> orPositiveNegative(this, other)
            other.sign > 0 -> orPositiveNegative(other, this)
            else -> orNegativeNegative(this, other)
        }
    }

    actual fun xor(other: BigInteger): BigInteger {
        if (sign == 0) return other
        if (other.sign == 0) return this
        if (this === other) return ZERO
        return when {
            sign > 0 && other.sign > 0 -> xorPositive(this, other)
            sign > 0 -> xorPositiveNegative(this, other)
            other.sign > 0 -> xorPositiveNegative(other, this)
            else -> xorNegativeNegative(this, other)
        }
    }

    actual fun not(): BigInteger = when (sign) {
        0 -> MINUS_ONE
        1 -> addMagnitudeOne(-1, this)
        else -> subtractMagnitudeOne(1, this)
    }

    actual fun andNot(other: BigInteger): BigInteger {
        if (sign == 0) return ZERO
        if (other.sign == 0) return this
        if (this === other) return ZERO
        return when {
            sign > 0 && other.sign > 0 -> andNotPositive(this, other)
            sign > 0 -> andNotPositiveNegative(this, other)
            other.sign > 0 -> andNotNegativePositive(this, other)
            else -> andNotNegativeNegative(this, other)
        }
    }

    actual fun shiftLeft(n: Int): BigInteger {
        if (n < 0) {
            if (n == Int.MIN_VALUE) {
                // shiftLeft(MIN_VALUE) = shiftRight(2^31): arithmetic right shift by huge amount
                // positive/zero → 0, negative → -1
                return if (sign < 0) MINUS_ONE else ZERO
            }
            return shiftRight(-n)
        }
        if (sign == 0 || n == 0) return this
        if (n.toLong() + bitLength().toLong() > MAX_BIT_LENGTH) {
            throw ArithmeticException("BigInteger would overflow supported range")
        }
        return shiftLeftMagnitude(sign, this, n)
    }

    actual fun shiftRight(n: Int): BigInteger {
        if (n < 0) {
            if (n == Int.MIN_VALUE) {
                // shiftRight(MIN_VALUE) = shiftLeft(2^31): only zero survives
                if (sign == 0) return ZERO
                throw ArithmeticException("Shift amount too large")
            }
            return shiftLeft(-n)
        }
        if (sign == 0 || n == 0) return this
        val digitShift = n / CANONICAL_LIMB_BITS
        if (digitShift >= size) return if (sign < 0) MINUS_ONE else ZERO
        val bitShift = n % CANONICAL_LIMB_BITS
        val shiftedMagnitude = shiftRightMagnitude(this, digitShift, bitShift)
        if (sign > 0) return shiftedMagnitude
        if (!hasDiscardedShiftBits(this, digitShift, bitShift)) {
            return BigInteger(-1, shiftedMagnitude.size, shiftedMagnitude.limbs)
        }
        return addMagnitudeOne(-1, shiftedMagnitude)
    }

    actual fun testBit(n: Int): Boolean {
        if (n < 0) throw ArithmeticException("Negative bit address")
        if (sign >= 0) {
            val digitIndex = n / CANONICAL_LIMB_BITS
            val bitIndex = n % CANONICAL_LIMB_BITS
            if (digitIndex >= size) return false
            return ((limbs[digitIndex] shr bitIndex) and 1UL) == 1UL
        }
        val digitIndex = n / CANONICAL_LIMB_BITS
        val bitIndex = n % CANONICAL_LIMB_BITS
        if (digitIndex >= size) return true
        return ((negativePayloadDigitAt(this, digitIndex) shr bitIndex) and 1UL) == 0UL
    }

    actual fun setBit(n: Int): BigInteger {
        if (n < 0) throw ArithmeticException("Negative bit address")
        if (sign >= 0) {
            if (testBit(n)) return this
            return flipBitNonNegative(this, n)
        }
        if (testBit(n)) return this
        return subtractSingleBitMagnitude(-1, this, n)
    }

    actual fun clearBit(n: Int): BigInteger {
        if (n < 0) throw ArithmeticException("Negative bit address")
        if (sign >= 0) {
            if (!testBit(n)) return this
            return flipBitNonNegative(this, n)
        }
        if (!testBit(n)) return this
        return addSingleBitMagnitude(-1, this, n)
    }

    actual fun flipBit(n: Int): BigInteger {
        if (n < 0) throw ArithmeticException("Negative bit address")
        if (sign >= 0) return flipBitNonNegative(this, n)
        return if (testBit(n)) {
            addSingleBitMagnitude(-1, this, n)
        } else {
            subtractSingleBitMagnitude(-1, this, n)
        }
    }

    actual fun getLowestSetBit(): Int {
        if (size == 0) return -1
        for (index in 0 until size) {
            val digit = limbs[index]
            if (digit != 0UL) {
                return index * CANONICAL_LIMB_BITS + digit.countTrailingZeroBits()
            }
        }
        return -1
    }

    actual fun bitLength(): Int {
        if (size == 0) return 0
        val digitBitLength = ULong.SIZE_BITS - limbs[size - 1].countLeadingZeroBits()
        val positiveBits = (size - 1) * CANONICAL_LIMB_BITS + digitBitLength
        return if (sign > 0) positiveBits else if (isPowerOfTwoMagnitude(this)) positiveBits - 1 else positiveBits
    }

    actual fun bitCount(): Int {
        if (size == 0) return 0
        var count = 0
        for (index in 0 until size) {
            count += limbs[index].countOneBits()
        }
        return if (sign > 0) count else count + getLowestSetBit() - 1
    }

    // Predicates

    actual fun isProbablePrime(certainty: Int): Boolean = memScoped {
        // JVM semantics: certainty <= 0 means always return true
        if (certainty <= 0) return true
        // JVM tests absolute value for primality
        if (sign == 0) return false
        val target = if (sign < 0) abs() else this@BigInteger
        target.toNonNegativeLongOrNull()?.let { return isPrimeLong(it) }
        // JVM certainty means error probability ≤ 2^(-certainty).
        // Each Miller-Rabin round has error ≤ 1/4 = 2^(-2),
        // so ceil(certainty / 2) rounds achieve the required bound.
        val rounds = (certainty + 1) / 2
        target.withBorrowedHandle { handle ->
            val result = alloc<UIntVar>()
            checkMp(mp_prime_is_prime(handle, rounds.coerceAtLeast(1), result.ptr))
            result.value != 0u
        }
    }

    actual fun nextProbablePrime(): BigInteger {
        if (sign < 0) throw ArithmeticException("start < 0: $this")
        if (sign == 0 || this == ONE) return TWO
        toNonNegativeLongOrNull()?.let { start ->
            nextProbablePrimeLongOrNull(start)?.let { return bigIntegerOf(it) }
        }
        return withBorrowedHandle { handle ->
            val result = allocMp()
            checkMp(mp_copy(handle, result), result)
            // JVM uses DEFAULT_PRIME_CERTAINTY = 100, which maps to
            // ceil(100/2) = 50 MR rounds via the same certainty→rounds
            // conversion as isProbablePrime.
            val defaultRounds = (DEFAULT_PRIME_CERTAINTY + 1) / 2
            checkMp(mp_prime_next_prime(result, defaultRounds, 0), result)
            result.toBigInteger()
        }
    }

    // Roots

    actual fun sqrt(): BigInteger {
        if (sign < 0) throw ArithmeticException("Negative BigInteger")
        if (sign == 0) return ZERO
        return withBorrowedHandle { handle ->
            val result = allocMp()
            checkMp(mp_sqrt(handle, result), result)
            result.toBigInteger()
        }
    }

    // Conversions

    actual fun toByteArray(): ByteArray {
        if (sign == 0) return byteArrayOf(0)

        val magnitude = withBorrowedHandle { handle ->
            val bytesSize = mp_ubin_size(handle)
            memScoped {
                val buf = allocArray<UByteVar>(bytesSize.toLong())
                val written = alloc<ULongVar>()
                mp_to_ubin(handle, buf.reinterpret(), bytesSize, written.ptr)
                val count = written.value.toInt()
                ByteArray(count) { buf[it].toByte() }
            }
        }

        if (sign > 0) {
            return if ((magnitude[0].toInt() and 0x80) != 0) {
                byteArrayOf(0) + magnitude
            } else {
                magnitude
            }
        }

        for (index in magnitude.indices) {
            magnitude[index] = magnitude[index].toInt().inv().toByte()
        }
        var carry = 1
        for (index in magnitude.indices.reversed()) {
            val sum = (magnitude[index].toInt() and 0xFF) + carry
            magnitude[index] = sum.toByte()
            carry = sum shr 8
        }
        return if ((magnitude[0].toInt() and 0x80) == 0) {
            byteArrayOf(0xFF.toByte()) + magnitude
        } else {
            magnitude
        }
    }

    actual fun toInt(): Int =
        withBorrowedHandle { handle -> mp_get_i32(handle) }

    actual fun toLong(): Long =
        withBorrowedHandle { handle -> mp_get_i64(handle) }

    actual fun toDouble(): Double = toString().toDouble()

    actual fun signum(): Int = sign

    // Comparison

    actual fun min(other: BigInteger): BigInteger =
        if (compareTo(other) <= 0) this else other

    actual fun max(other: BigInteger): BigInteger =
        if (compareTo(other) >= 0) this else other

    actual override fun compareTo(other: BigInteger): Int {
        if (sign != other.sign) return sign.compareTo(other.sign)
        return when (sign) {
            0 -> 0
            1 -> compareMagnitudes(this, other)
            else -> -compareMagnitudes(this, other)
        }
    }

    actual fun toString(radix: Int): String {
        // JVM semantics: invalid radix falls back to radix 10
        val effectiveRadix = if (radix in 2..36) radix else 10
        return withBorrowedHandle { handle ->
            memScoped {
                val sizeVar = alloc<IntVar>()
                mp_radix_size(handle, effectiveRadix, sizeVar.ptr)
                val stringSize = sizeVar.value
                val buf = allocArray<ByteVar>(stringSize)
                val written = alloc<ULongVar>()
                mp_to_radix(handle, buf, stringSize.toULong(), written.ptr, effectiveRadix)
                buf.toKString().lowercase()
            }
        }
    }

    actual override fun toString(): String = toString(10)

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BigInteger) return false
        if (sign != other.sign || size != other.size) return false
        for (index in 0 until size) {
            if (limbs[index] != other.limbs[index]) return false
        }
        return true
    }

    actual override fun hashCode(): Int {
        // Match java.math.BigInteger.hashCode():
        // XOR each 32-bit word of big-endian magnitude with factor 31, then multiply by signum
        if (sign == 0) return 0
        val bytes = toByteArray()
        // Strip sign byte if present to get magnitude
        val start = if (sign >= 0 && bytes[0] == 0.toByte() && bytes.size > 1) 1 else 0
        val mag = if (sign >= 0) {
            if (start == 1) bytes.copyOfRange(1, bytes.size) else bytes
        } else {
            withBorrowedHandle { handle ->
                val bytesSize = mp_ubin_size(handle)
                memScoped {
                    val buf = allocArray<UByteVar>(bytesSize.toLong())
                    val written = alloc<ULongVar>()
                    mp_to_ubin(handle, buf.reinterpret(), bytesSize, written.ptr)
                    val count = written.value.toInt()
                    ByteArray(count) { buf[it].toByte() }
                }
            }
        }
        val padded = if (mag.size % 4 != 0) {
            ByteArray(((mag.size + 3) / 4) * 4 - mag.size) + mag
        } else {
            mag
        }
        var hashCode = 0
        for (index in padded.indices step 4) {
            val word = ((padded[index].toInt() and 0xFF) shl 24) or
                ((padded[index + 1].toInt() and 0xFF) shl 16) or
                ((padded[index + 2].toInt() and 0xFF) shl 8) or
                (padded[index + 3].toInt() and 0xFF)
            hashCode = 31 * hashCode + word
        }
        return hashCode * sign
    }
}

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.rem(other: BigInteger): BigInteger {
    if (other.sign == 0) throw ArithmeticException("BigInteger divide by zero")
    if (sign == 0) return ZERO
    if (size <= SCHOOLBOOK_DIV_THRESHOLD && other.size <= SCHOOLBOOK_DIV_THRESHOLD) {
        return remainderSmall(sign, this, other)
    }
    return withBorrowedHandles(this, other) { handle, otherHandle ->
        val result = allocMp()
        checkMp(mp_div(handle, otherHandle, null, result), result)
        result.toBigInteger()
    }
}

actual operator fun BigInteger.unaryMinus(): BigInteger =
    if (sign == 0) this else BigInteger(-sign, size, limbs)

// inc/dec

actual operator fun BigInteger.inc(): BigInteger = when (sign) {
    0 -> ONE
    1 -> addMagnitudeOne(1, this)
    else -> subtractMagnitudeOne(-1, this)
}

actual operator fun BigInteger.dec(): BigInteger = when (sign) {
    0 -> MINUS_ONE
    -1 -> addMagnitudeOne(-1, this)
    else -> subtractMagnitudeOne(1, this)
}

// Additional operations

@OptIn(ExperimentalForeignApi::class)
actual fun BigInteger.lcm(other: BigInteger): BigInteger {
    if (this.sign == 0 || other.sign == 0) return ZERO
    return withBorrowedHandles(this, other) { handle, otherHandle ->
        val result = allocMp()
        checkMp(mp_lcm(handle, otherHandle, result), result)
        // mp_lcm always returns positive; JVM semantics: (this / gcd) * other preserves sign
        if (this.sign * other.sign < 0) {
            mp_neg(result, result)
        }
        result.toBigInteger()
    }
}

// Constants

/**
 * Maximum bit length we allow before refusing an operation.
 * Matches the JVM's practical limit (~2^31 bits, i.e. ~256 MB magnitude).
 */
private const val MAX_BIT_LENGTH = Int.MAX_VALUE.toLong()

/** Matches JVM's BigInteger.MAX_MAG_LENGTH: Integer.MAX_VALUE / Integer.SIZE + 1 */
private const val MAX_MAG_LENGTH = Int.MAX_VALUE / 32 + 1L

/** Matches JVM's BigInteger.DEFAULT_PRIME_CERTAINTY used by nextProbablePrime(). */
private const val DEFAULT_PRIME_CERTAINTY = 100

internal const val CANONICAL_LIMB_BITS = 60
internal const val CANONICAL_LIMB_MASK = 0x0FFFFFFFFFFFFFFFUL
internal const val CANONICAL_LIMB_BASE = 0x1000000000000000UL

internal val EMPTY_LIMBS = ULongArray(0)
internal val BORROWED_ZERO_LIMBS = ULongArray(1)

// Cached constants

internal val ZERO = BigInteger(0, 0, EMPTY_LIMBS)
internal val ONE = newBigIntegerFromLong(1L)
private val TWO = newBigIntegerFromLong(2L)
private val TEN = newBigIntegerFromLong(10L)
private val HUNDRED = newBigIntegerFromLong(100L)
internal val MINUS_ONE = bigIntegerOf(-1L)

// Factory functions

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
    else -> newBigIntegerFromLong(value)
}

actual fun bigIntegerOf(value: Int): BigInteger = when (value) {
    0 -> ZERO
    1 -> ONE
    2 -> TWO
    10 -> TEN
    100 -> HUNDRED
    else -> newBigIntegerFromLong(value.toLong())
}

private fun newBigIntegerFromLong(value: Long): BigInteger {
    if (value == 0L) return ZERO
    val sign = if (value < 0) -1 else 1
    val magnitude = when {
        value >= 0L -> value.toULong()
        value == Long.MIN_VALUE -> 1UL shl 63
        else -> (-value).toULong()
    }
    val lower = magnitude and CANONICAL_LIMB_MASK
    val upper = magnitude shr CANONICAL_LIMB_BITS
    val limbs = if (upper == 0UL) {
        ulongArrayOf(lower)
    } else {
        ulongArrayOf(lower, upper)
    }
    return BigInteger(sign, limbs.size, limbs)
}

private fun BigInteger.toNonNegativeLongOrNull(): Long? {
    if (sign < 0) return null
    return when (size) {
        0 -> 0L
        1 -> limbs[0].toLong()
        2 -> {
            val upper = limbs[1]
            if (upper >= 8UL) return null
            val combined = (upper shl CANONICAL_LIMB_BITS) or limbs[0]
            if (combined > Long.MAX_VALUE.toULong()) return null
            combined.toLong()
        }
        else -> null
    }
}

private fun nextProbablePrimeLongOrNull(start: Long): Long? {
    if (start < 2L) return 2L
    if (start > Long.MAX_VALUE - 2L) return null

    var candidate = start + 1L
    if (candidate <= 2L) return 2L
    if ((candidate and 1L) == 0L) {
        candidate++
    }

    while (true) {
        if (isPrimeLong(candidate)) return candidate
        if (candidate > Long.MAX_VALUE - 2L) return null
        candidate += 2L
    }
}

private fun isPrimeLong(value: Long): Boolean {
    if (value < 2L) return false
    if (value == 2L || value == 3L) return true
    if ((value and 1L) == 0L) return false

    val candidate = value.toULong()
    for (prime in SMALL_PRIME_PRETESTS) {
        if (value == prime.toLong()) return true
        if (value % prime.toLong() == 0L) return false
    }

    val minusOne = candidate - 1UL
    var oddPart = minusOne
    var powersOfTwo = 0
    while ((oddPart and 1UL) == 0UL) {
        oddPart = oddPart shr 1
        powersOfTwo++
    }

    for (base in LONG_MILLER_RABIN_BASES) {
        val witness = base % candidate
        if (witness <= 1UL) continue

        var x = powModULong(witness, oddPart, candidate)
        if (x == 1UL || x == minusOne) continue

        var passed = false
        var round = 1
        while (round < powersOfTwo) {
            x = multiplyModULong(x, x, candidate)
            if (x == minusOne) {
                passed = true
                break
            }
            round++
        }
        if (!passed) return false
    }

    return true
}

private fun powModULong(base: ULong, exponent: ULong, modulus: ULong): ULong {
    var result = 1UL
    var factor = base % modulus
    var remaining = exponent
    while (remaining != 0UL) {
        if ((remaining and 1UL) != 0UL) {
            result = multiplyModULong(result, factor, modulus)
        }
        remaining = remaining shr 1
        if (remaining != 0UL) {
            factor = multiplyModULong(factor, factor, modulus)
        }
    }
    return result
}

private fun multiplyModULong(left: ULong, right: ULong, modulus: ULong): ULong {
    if (modulus == 1UL || left == 0UL || right == 0UL) return 0UL

    var multiplicand = left % modulus
    var multiplier = right % modulus
    var result = 0UL
    while (multiplier != 0UL) {
        if ((multiplier and 1UL) != 0UL) {
            result = addModULong(result, multiplicand, modulus)
        }
        multiplier = multiplier shr 1
        if (multiplier != 0UL) {
            multiplicand = addModULong(multiplicand, multiplicand, modulus)
        }
    }
    return result
}

private fun addModULong(left: ULong, right: ULong, modulus: ULong): ULong =
    if (left >= modulus - right) {
        left - (modulus - right)
    } else {
        left + right
    }

private val SMALL_PRIME_PRETESTS = intArrayOf(
    3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37
)

private val LONG_MILLER_RABIN_BASES = ulongArrayOf(
    2UL, 325UL, 9375UL, 28178UL, 450775UL, 9780504UL, 1795265022UL
)

private val ZERO_CONSTRUCTION = BigIntegerConstruction(0, 0, EMPTY_LIMBS)
private const val STRING_PARSE_WORD_BITS = 32
private const val STRING_PARSE_WORD_MASK = 0xFFFFFFFFUL
private val STRING_BITS_PER_DIGIT = longArrayOf(
    0L, 0L,
    1024L, 1624L, 2048L, 2378L, 2648L, 2875L, 3072L, 3247L, 3402L, 3543L, 3672L,
    3790L, 3899L, 4001L, 4096L, 4186L, 4271L, 4350L, 4426L, 4498L, 4567L, 4633L,
    4696L, 4756L, 4814L, 4870L, 4923L, 4975L, 5025L, 5074L, 5120L, 5166L, 5210L,
    5253L, 5295L,
)
private val STRING_DIGITS_PER_LIMB = intArrayOf(
    0, 0, 60, 37, 30, 25, 23, 21,
    20, 18, 18, 17, 16, 16, 15, 15,
    15, 14, 14, 14, 13, 13, 13, 13,
    13, 12, 12, 12, 12, 12, 12, 12,
    12, 11, 11, 11, 11,
)
private val STRING_LIMB_RADIX = ulongArrayOf(
    0x0UL, 0x0UL, 0x1000000000000000UL, 0x63fbad3a2b55473UL,
    0x1000000000000000UL, 0x422ca8b0a00a425UL, 0xaf5af7425800000UL, 0x7c05a810b72a027UL,
    0x1000000000000000UL, 0x2153e468b91c6d1UL, 0xde0b6b3a7640000UL, 0x703b564fa7a264bUL,
    0x290d74100000000UL, 0x93c08e16a022441UL, 0x228b05bd21b8000UL, 0x613b62c597707efUL,
    0x1000000000000000UL, 0x25632bdbc201be1UL, 0x5339ac59fcc4000UL, 0xb16a458ef403f19UL,
    0x12309ce54000000UL, 0x224cbcf22c00b65UL, 0x3ecbe3fcf076000UL, 0x6feb266931a75b7UL,
    0xc29e98000000000UL, 0xd3c21bcecceda1UL, 0x1530821671b1000UL, 0x2153e468b91c6d1UL,
    0x339014821000000UL, 0x4e900abb53e6b71UL, 0x7600ec618141000UL, 0xaee5720ee830681UL,
    0x1000000000000000UL, 0xb38fc730f35d61UL, 0xf95c61a43d8800UL, 0x15702f27495705bUL,
    0x1d39d3e06400000UL,
)

private data class ParsedStringMetadata(
    val sign: Int,
    val cursor: Int,
    val numDigits: Int,
)

private fun constructFromString(value: String, radix: Int): BigIntegerConstruction {
    val metadata = parseStringMetadata(value, radix) ?: return ZERO_CONSTRUCTION
    val numDigits = metadata.numDigits
    val numBits = ((numDigits.toLong() * STRING_BITS_PER_DIGIT[radix]) ushr 10) + 1L
    if (numBits > MAX_BIT_LENGTH) {
        throw ArithmeticException("BigInteger would overflow supported range")
    }

    val estimatedLimbs = ((numBits + CANONICAL_LIMB_BITS - 1) / CANONICAL_LIMB_BITS).toInt()
    val magnitude = ULongArray(estimatedLimbs + 1)
    var size = 1
    var cursor = metadata.cursor
    val digitsPerGroup = STRING_DIGITS_PER_LIMB[radix]
    var firstGroupLen = numDigits % digitsPerGroup
    if (firstGroupLen == 0) {
        firstGroupLen = digitsPerGroup
    }
    magnitude[0] = parseDigitGroup(value, cursor, cursor + firstGroupLen, radix)
    cursor += firstGroupLen
    val superRadix = STRING_LIMB_RADIX[radix]
    while (cursor < value.length) {
        val groupEnd = cursor + digitsPerGroup
        val groupValue = parseDigitGroup(value, cursor, groupEnd, radix)
        size = destructiveMulAdd(magnitude, size, superRadix, groupValue)
        cursor = groupEnd
    }
    val normalizedSize = normalizeMagnitudeSize(size, magnitude)
    if (normalizedSize == 0) return ZERO_CONSTRUCTION
    return BigIntegerConstruction(metadata.sign, normalizedSize, magnitude)
}

private fun parseStringMetadata(value: String, radix: Int): ParsedStringMetadata? {
    if (radix !in 2..36) throw NumberFormatException("Radix out of range: $radix")
    val length = value.length
    if (length == 0) throw NumberFormatException("Zero length BigInteger")

    var cursor = 0
    var sign = 1
    val minusIndex = value.lastIndexOf('-')
    val plusIndex = value.lastIndexOf('+')
    if (minusIndex >= 0) {
        if (minusIndex != 0 || plusIndex >= 0) {
            throw NumberFormatException("Illegal embedded sign character")
        }
        sign = -1
        cursor = 1
    } else if (plusIndex >= 0) {
        if (plusIndex != 0) {
            throw NumberFormatException("Illegal embedded sign character")
        }
        cursor = 1
    }

    if (cursor == length) throw NumberFormatException("Zero length BigInteger")

    while (cursor < length) {
        val digit = digitOrThrow(value[cursor], radix, value)
        if (digit != 0) break
        cursor++
    }
    if (cursor == length) return null
    return ParsedStringMetadata(sign, cursor, length - cursor)
}

private fun parseDigitGroup(value: String, start: Int, end: Int, radix: Int): ULong {
    val radixValue = radix.toULong()
    var result = digitOrThrow(value[start], radix, value).toULong()
    for (index in start + 1 until end) {
        result = result * radixValue + digitOrThrow(value[index], radix, value).toULong()
    }
    return result
}

private fun digitOrThrow(char: Char, radix: Int, value: String): Int =
    char.digitToIntOrNull(radix) ?: throw invalidDigitException(value, radix)

private fun invalidDigitException(value: String, radix: Int): NumberFormatException {
    val message = if (radix == 10) {
        "For input string: \"$value\""
    } else {
        "For input string: \"$value\" under radix $radix"
    }
    return NumberFormatException(message)
}

private fun destructiveMulAdd(
    magnitude: ULongArray,
    size: Int,
    multiplier: ULong,
    addend: ULong,
): Int {
    var carry = 0UL
    var index = 0
    val multiplierLow = multiplier and STRING_PARSE_WORD_MASK
    val multiplierHigh = multiplier shr STRING_PARSE_WORD_BITS
    while (index < size) {
        val limb = magnitude[index]
        val limbLow = limb and STRING_PARSE_WORD_MASK
        val limbHigh = limb shr STRING_PARSE_WORD_BITS
        val lowProduct = limbLow * multiplierLow
        val crossProduct = limbLow * multiplierHigh + limbHigh * multiplierLow
        var low64 = lowProduct + ((crossProduct and STRING_PARSE_WORD_MASK) shl STRING_PARSE_WORD_BITS)
        var high64 = limbHigh * multiplierHigh + (crossProduct shr STRING_PARSE_WORD_BITS)
        if (low64 < lowProduct) {
            high64++
        }

        val product = low64 + carry
        if (product < low64) {
            high64++
        }

        magnitude[index] = product and CANONICAL_LIMB_MASK
        carry = (high64 shl (ULong.SIZE_BITS - CANONICAL_LIMB_BITS)) or (product shr CANONICAL_LIMB_BITS)
        index++
    }

    var resultSize = size
    if (carry != 0UL) {
        magnitude[resultSize++] = carry
    }

    var addCarry = addend
    index = 0
    while (addCarry != 0UL) {
        if (index == resultSize) {
            magnitude[resultSize++] = addCarry
            break
        }
        val sum = magnitude[index] + addCarry
        magnitude[index] = sum and CANONICAL_LIMB_MASK
        addCarry = sum shr CANONICAL_LIMB_BITS
        index++
    }
    return resultSize
}

private fun constructFromTwosComplement(bytes: ByteArray): BigIntegerConstruction {
    if (bytes.isEmpty()) throw NumberFormatException("Zero length BigInteger")
    return constructFromTwosComplementRange(bytes, 0, bytes.size)
}

private fun constructFromTwosComplementSlice(
    bytes: ByteArray,
    off: Int,
    len: Int,
): BigIntegerConstruction {
    if (off < 0 || len < 0 || off.toLong() + len.toLong() > bytes.size) {
        throw IndexOutOfBoundsException("Range [$off, ${off.toLong() + len.toLong()}) out of bounds for length ${bytes.size}")
    }
    if (bytes.isEmpty()) throw NumberFormatException("Zero length BigInteger")

    if (len == 0) {
        val leadingByte = bytes[off]
        if (leadingByte >= 0) {
            return ZERO_CONSTRUCTION
        }
        val magnitude = ((bytes[off - 1].toInt().inv()) and 0xFF) + 1
        return constructFromLong(-magnitude.toLong())
    }

    return constructFromTwosComplementRange(bytes, off, off + len)
}

private fun constructFromTwosComplementRange(
    bytes: ByteArray,
    start: Int,
    end: Int,
): BigIntegerConstruction =
    if ((bytes[start].toInt() and 0x80) == 0) {
        constructPositiveBigEndian(bytes, start, end)
    } else {
        constructNegativeTwosComplement(bytes, start, end)
    }

private fun constructFromLong(value: Long): BigIntegerConstruction {
    if (value == 0L) return ZERO_CONSTRUCTION
    val sign = if (value < 0) -1 else 1
    val magnitude = when {
        value >= 0L -> value.toULong()
        value == Long.MIN_VALUE -> 1UL shl 63
        else -> (-value).toULong()
    }
    val lower = magnitude and CANONICAL_LIMB_MASK
    val upper = magnitude shr CANONICAL_LIMB_BITS
    val limbs = if (upper == 0UL) {
        ulongArrayOf(lower)
    } else {
        ulongArrayOf(lower, upper)
    }
    return BigIntegerConstruction(sign, limbs.size, limbs)
}

private fun constructPositiveBigEndian(
    bytes: ByteArray,
    start: Int,
    end: Int,
): BigIntegerConstruction {
    var first = start
    while (first < end && bytes[first] == 0.toByte()) {
        first++
    }
    if (first == end) return ZERO_CONSTRUCTION
    val byteCount = end - first
    val limbs = ULongArray(((byteCount.toLong() * 8 + CANONICAL_LIMB_BITS - 1) / CANONICAL_LIMB_BITS).toInt())
    var limbIndex = 0
    var accumulator = 0UL
    var accumulatorBits = 0
    for (index in end - 1 downTo first) {
        accumulator = accumulator or (((bytes[index].toInt() and 0xFF).toULong()) shl accumulatorBits)
        accumulatorBits += 8
        if (accumulatorBits >= CANONICAL_LIMB_BITS) {
            limbs[limbIndex++] = accumulator and CANONICAL_LIMB_MASK
            accumulator = accumulator shr CANONICAL_LIMB_BITS
            accumulatorBits -= CANONICAL_LIMB_BITS
        }
    }
    if (accumulatorBits > 0 && accumulator != 0UL) {
        limbs[limbIndex++] = accumulator
    }
    val size = normalizeMagnitudeSize(limbIndex, limbs)
    if (size == 0) return ZERO_CONSTRUCTION
    return BigIntegerConstruction(1, size, limbs)
}

private fun constructNegativeTwosComplement(
    bytes: ByteArray,
    start: Int,
    end: Int,
): BigIntegerConstruction {
    val byteCount = end - start
    val limbs = ULongArray(((byteCount.toLong() * 8 + CANONICAL_LIMB_BITS - 1) / CANONICAL_LIMB_BITS).toInt())
    var limbIndex = 0
    var accumulator = 0UL
    var accumulatorBits = 0
    var carry = 1
    for (index in end - 1 downTo start) {
        val sum = (bytes[index].toInt().inv() and 0xFF) + carry
        accumulator = accumulator or (((sum and 0xFF).toULong()) shl accumulatorBits)
        carry = sum ushr 8
        accumulatorBits += 8
        if (accumulatorBits >= CANONICAL_LIMB_BITS) {
            limbs[limbIndex++] = accumulator and CANONICAL_LIMB_MASK
            accumulator = accumulator shr CANONICAL_LIMB_BITS
            accumulatorBits -= CANONICAL_LIMB_BITS
        }
    }
    if (accumulatorBits > 0) {
        limbs[limbIndex++] = accumulator
    }
    val size = normalizeMagnitudeSize(limbIndex, limbs)
    if (size == 0) return ZERO_CONSTRUCTION
    return BigIntegerConstruction(-1, size, limbs)
}

// Magnitude utilities

internal fun normalizeMagnitudeSize(size: Int, limbs: ULongArray): Int {
    var normalizedSize = minOf(size, limbs.size)
    while (normalizedSize > 0 && limbs[normalizedSize - 1] == 0UL) {
        normalizedSize--
    }
    return normalizedSize
}

internal fun bigIntegerFromLimbs(sign: Int, size: Int, limbs: ULongArray): BigInteger {
    val normalizedSize = normalizeMagnitudeSize(size, limbs)
    if (normalizedSize == 0 || sign == 0) return ZERO
    return BigInteger(sign, normalizedSize, limbs)
}

internal fun compareMagnitudes(left: BigInteger, right: BigInteger): Int {
    if (left.size != right.size) return left.size.compareTo(right.size)
    for (index in left.size - 1 downTo 0) {
        val leftDigit = left.limbs[index]
        val rightDigit = right.limbs[index]
        if (leftDigit != rightDigit) {
            return if (leftDigit < rightDigit) -1 else 1
        }
    }
    return 0
}
