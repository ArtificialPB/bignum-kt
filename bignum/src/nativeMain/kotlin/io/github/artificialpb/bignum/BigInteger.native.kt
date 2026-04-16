package io.github.artificialpb.bignum

import io.github.artificialpb.bignum.tommath.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class BigInteger internal constructor(
    internal val sign: Int,
    internal val size: Int,
    internal val limbs: ULongArray,
) : Comparable<BigInteger> {
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

    actual constructor(value: String) : this(parseTomMath(value, 10))

    actual constructor(value: String, radix: Int) : this(validateRadixAndParse(value, radix))

    actual constructor(bytes: ByteArray) : this(fromTwosComplement(bytes))

    actual constructor(bytes: ByteArray, off: Int, len: Int) : this(
        validateAndSliceBytes(bytes, off, len)
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

        return withBorrowedHandle { handle ->
            val result = allocMp()
            val err = mp_expt_n(handle, exponent, result)
            if (err != MP_OKAY) {
                freeMp(result)
                throw ArithmeticException("BigInteger would overflow supported range")
            }
            result.toBigInteger()
        }
    }

    actual fun mod(modulus: BigInteger): BigInteger {
        if (modulus.sign <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        if (sign == 0) return ZERO
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

    actual fun and(other: BigInteger): BigInteger =
        withBorrowedHandles(this, other) { handle, otherHandle ->
            val result = allocMp()
            checkMp(mp_and(handle, otherHandle, result), result)
            result.toBigInteger()
        }

    actual fun or(other: BigInteger): BigInteger =
        withBorrowedHandles(this, other) { handle, otherHandle ->
            val result = allocMp()
            checkMp(mp_or(handle, otherHandle, result), result)
            result.toBigInteger()
        }

    actual fun xor(other: BigInteger): BigInteger =
        withBorrowedHandles(this, other) { handle, otherHandle ->
            val result = allocMp()
            checkMp(mp_xor(handle, otherHandle, result), result)
            result.toBigInteger()
        }

    actual fun not(): BigInteger =
        withBorrowedHandle { handle ->
            val result = allocMp()
            checkMp(mp_complement(handle, result), result)
            result.toBigInteger()
        }

    actual fun andNot(other: BigInteger): BigInteger =
        withBorrowedHandles(this, other) { handle, otherHandle ->
            val notOther = allocMp()
            checkMp(mp_complement(otherHandle, notOther), notOther)
            val result = allocMp()
            val err = mp_and(handle, notOther, result)
            freeMp(notOther)
            if (err != MP_OKAY) {
                freeMp(result)
                throw ArithmeticException("LibTomMath error: $err")
            }
            result.toBigInteger()
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
        if (sign != 0 && n.toLong() + bitLength().toLong() > MAX_BIT_LENGTH) {
            throw ArithmeticException("BigInteger would overflow supported range")
        }
        return withBorrowedHandle { handle ->
            val result = allocMp()
            val err = mp_mul_2d(handle, n, result)
            if (err != MP_OKAY) {
                freeMp(result)
                throw ArithmeticException("BigInteger would overflow supported range")
            }
            result.toBigInteger()
        }
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
        if (sign == 0) return ZERO
        return withBorrowedHandle { handle ->
            val result = allocMp()
            checkMp(mp_signed_rsh(handle, n, result), result)
            result.toBigInteger()
        }
    }

    actual fun testBit(n: Int): Boolean {
        if (n < 0) throw ArithmeticException("Negative bit address")
        if (sign >= 0) {
            val digitIndex = n / CANONICAL_LIMB_BITS
            val bitIndex = n % CANONICAL_LIMB_BITS
            if (digitIndex >= size) return false
            return ((limbs[digitIndex] shr bitIndex) and 1UL) == 1UL
        }
        return withBorrowedHandle { handle ->
            val absMinusOne = allocMp()
            checkMp(mp_abs(handle, absMinusOne), absMinusOne)
            checkMp(mp_decr(absMinusOne), absMinusOne)
            val digitIndex = n / CANONICAL_LIMB_BITS
            val bitIndex = n % CANONICAL_LIMB_BITS
            val isSet = if (digitIndex >= absMinusOne.pointed.used) {
                true
            } else {
                (absMinusOne.pointed.dp!![digitIndex].toLong() ushr bitIndex) and 1L == 0L
            }
            freeMp(absMinusOne)
            isSet
        }
    }

    actual fun setBit(n: Int): BigInteger {
        if (n < 0) throw ArithmeticException("Negative bit address")
        if (testBit(n)) return this
        return flipBit(n)
    }

    actual fun clearBit(n: Int): BigInteger {
        if (n < 0) throw ArithmeticException("Negative bit address")
        if (!testBit(n)) return this
        return flipBit(n)
    }

    actual fun flipBit(n: Int): BigInteger {
        if (n < 0) throw ArithmeticException("Negative bit address")
        return withBorrowedHandle { handle ->
            val bitMask = allocMp()
            checkMp(mp_2expt(bitMask, n), bitMask)
            val result = allocMp()
            val err = mp_xor(handle, bitMask, result)
            freeMp(bitMask)
            if (err != MP_OKAY) {
                freeMp(result)
                throw ArithmeticException("LibTomMath error: $err")
            }
            result.toBigInteger()
        }
    }

    actual fun getLowestSetBit(): Int {
        if (size == 0) return -1
        for (index in 0 until size) {
            val digit = limbs[index]
            if (digit != 0UL) {
                return index * CANONICAL_LIMB_BITS + trailingZeroBits(digit)
            }
        }
        return -1
    }

    actual fun bitLength(): Int {
        if (size == 0) return 0
        if (sign > 0) return (size - 1) * CANONICAL_LIMB_BITS + digitBitLength(limbs[size - 1])
        return withBorrowedHandle { handle ->
            val absMinusOne = allocMp()
            checkMp(mp_abs(handle, absMinusOne), absMinusOne)
            checkMp(mp_decr(absMinusOne), absMinusOne)
            val bits = if (absMinusOne.pointed.used == 0) 0 else mp_count_bits(absMinusOne)
            freeMp(absMinusOne)
            bits
        }
    }

    actual fun bitCount(): Int {
        if (size == 0) return 0
        if (sign > 0) {
            var count = 0
            for (index in 0 until size) {
                count += digitBitCount(limbs[index])
            }
            return count
        }
        return withBorrowedHandle { handle ->
            val target = allocMp()
            checkMp(mp_abs(handle, target), target)
            checkMp(mp_decr(target), target)
            var count = 0
            val dp = target.pointed.dp!!
            for (index in 0 until target.pointed.used) {
                var digit = dp[index]
                while (digit != 0uL) {
                    digit = digit and (digit - 1uL)
                    count++
                }
            }
            freeMp(target)
            count
        }
    }

    // Predicates

    actual fun isProbablePrime(certainty: Int): Boolean = memScoped {
        // JVM semantics: certainty <= 0 means always return true
        if (certainty <= 0) return true
        // JVM tests absolute value for primality
        if (sign == 0) return false
        val target = if (sign < 0) abs() else this@BigInteger
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

// Internal helpers

/**
 * Maximum bit length we allow before refusing an operation.
 * Matches the JVM's practical limit (~2^31 bits, i.e. ~256 MB magnitude).
 */
private const val MAX_BIT_LENGTH = Int.MAX_VALUE.toLong()

/** Matches JVM's BigInteger.MAX_MAG_LENGTH: Integer.MAX_VALUE / Integer.SIZE + 1 */
private const val MAX_MAG_LENGTH = Int.MAX_VALUE / 32 + 1L

/** Matches JVM's BigInteger.DEFAULT_PRIME_CERTAINTY used by nextProbablePrime(). */
private const val DEFAULT_PRIME_CERTAINTY = 100

private const val CANONICAL_LIMB_BITS = 60
private const val CANONICAL_LIMB_MASK = 0x0FFFFFFFFFFFFFFFUL
private const val CANONICAL_LIMB_BASE = 0x1000000000000000UL

private val EMPTY_LIMBS = ULongArray(0)
private val BORROWED_ZERO_LIMBS = ULongArray(1)

private fun BigInteger.digit(index: Int): ULong =
    if (index in 0 until size) limbs[index] else 0UL

private fun normalizeMagnitudeSize(size: Int, limbs: ULongArray): Int {
    var normalizedSize = minOf(size, limbs.size)
    while (normalizedSize > 0 && limbs[normalizedSize - 1] == 0UL) {
        normalizedSize--
    }
    return normalizedSize
}

private fun bigIntegerFromLimbs(sign: Int, size: Int, limbs: ULongArray): BigInteger {
    val normalizedSize = normalizeMagnitudeSize(size, limbs)
    if (normalizedSize == 0 || sign == 0) return ZERO
    return BigInteger(sign, normalizedSize, limbs)
}

private fun compareMagnitudes(left: BigInteger, right: BigInteger): Int {
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

private fun addAbsolute(sign: Int, left: BigInteger, right: BigInteger): BigInteger {
    if (left.size == right.size) {
        val size = left.size
        val leftLimbs = left.limbs
        val rightLimbs = right.limbs
        val result = ULongArray(size + 1)
        var carry = 0UL
        var index = 0
        while (index < size) {
            val sum = leftLimbs[index] + rightLimbs[index] + carry
            result[index] = sum and CANONICAL_LIMB_MASK
            carry = sum shr CANONICAL_LIMB_BITS
            index++
        }
        return if (carry != 0UL) {
            result[size] = carry
            BigInteger(sign, size + 1, result)
        } else {
            BigInteger(sign, size, result)
        }
    }

    val maxSize = maxOf(left.size, right.size)
    val result = ULongArray(maxSize + 1)
    var carry = 0UL
    for (index in 0 until maxSize) {
        val sum = left.digit(index) + right.digit(index) + carry
        result[index] = sum and CANONICAL_LIMB_MASK
        carry = sum shr CANONICAL_LIMB_BITS
    }
    val resultSize = if (carry != 0UL) {
        result[maxSize] = carry
        maxSize + 1
    } else {
        maxSize
    }
    return bigIntegerFromLimbs(sign, resultSize, result)
}

private fun subtractAbsolute(sign: Int, larger: BigInteger, smaller: BigInteger): BigInteger {
    if (larger.size == smaller.size) {
        val size = larger.size
        val largerLimbs = larger.limbs
        val smallerLimbs = smaller.limbs
        val result = ULongArray(size)
        var borrow = 0UL
        var lastNonZero = 0
        var index = 0
        while (index < size) {
            val leftDigit = largerLimbs[index]
            val subtrahend = smallerLimbs[index] + borrow
            val digit = if (leftDigit >= subtrahend) {
                borrow = 0UL
                leftDigit - subtrahend
            } else {
                borrow = 1UL
                CANONICAL_LIMB_BASE + leftDigit - subtrahend
            }
            result[index] = digit
            if (digit != 0UL) {
                lastNonZero = index + 1
            }
            index++
        }
        return if (lastNonZero == 0) ZERO else BigInteger(sign, lastNonZero, result)
    }

    val result = ULongArray(larger.size)
    var borrow = 0UL
    var lastNonZero = 0
    for (index in 0 until larger.size) {
        val leftDigit = larger.digit(index)
        val rightDigit = smaller.digit(index)
        val subtrahend = rightDigit + borrow
        if (leftDigit >= subtrahend) {
            result[index] = leftDigit - subtrahend
            borrow = 0UL
        } else {
            result[index] = CANONICAL_LIMB_BASE + leftDigit - subtrahend
            borrow = 1UL
        }
        if (result[index] != 0UL) {
            lastNonZero = index + 1
        }
    }
    return if (lastNonZero == 0) ZERO else BigInteger(sign, lastNonZero, result)
}

/** Schoolbook multiply when total result limbs (left.size + right.size) is at or below this. */
private const val SCHOOLBOOK_MUL_THRESHOLD = 14

private const val HALF_LIMB_BITS = 30
private const val HALF_LIMB_MASK = 0x3FFFFFFFUL

private fun multiplySmall(sign: Int, left: BigInteger, right: BigInteger): BigInteger {
    val resultCapacity = left.size + right.size
    val result = ULongArray(resultCapacity)
    for (i in 0 until left.size) {
        val aLimb = left.limbs[i]
        val aL = aLimb and HALF_LIMB_MASK
        val aH = aLimb shr HALF_LIMB_BITS
        var carry = 0UL
        for (j in 0 until right.size) {
            val bLimb = right.limbs[j]
            val bL = bLimb and HALF_LIMB_MASK
            val bH = bLimb shr HALF_LIMB_BITS
            // Full 120-bit product of two 60-bit limbs, split via 30-bit halves
            val p0 = aL * bL
            val mid = aH * bL + aL * bH
            val p3 = aH * bH
            val lowSum = p0 + ((mid and HALF_LIMB_MASK) shl HALF_LIMB_BITS)
            val prodLow = lowSum and CANONICAL_LIMB_MASK
            val prodHigh = p3 + (lowSum shr CANONICAL_LIMB_BITS) + (mid shr HALF_LIMB_BITS)
            val acc = prodLow + result[i + j] + carry
            result[i + j] = acc and CANONICAL_LIMB_MASK
            carry = prodHigh + (acc shr CANONICAL_LIMB_BITS)
        }
        if (carry != 0UL) {
            result[i + right.size] = carry
        }
    }
    return bigIntegerFromLimbs(sign, resultCapacity, result)
}

private fun digitBitLength(value: ULong): Int {
    var current = value
    var bits = 0
    while (current != 0UL) {
        current = current shr 1
        bits++
    }
    return bits
}

private fun trailingZeroBits(value: ULong): Int {
    var current = value
    var zeros = 0
    while ((current and 1UL) == 0UL) {
        current = current shr 1
        zeros++
    }
    return zeros
}

private fun digitBitCount(value: ULong): Int {
    var current = value
    var count = 0
    while (current != 0UL) {
        current = current and (current - 1UL)
        count++
    }
    return count
}

@OptIn(ExperimentalForeignApi::class)
private fun signumFromHandle(handle: CPointer<mp_int>): Int {
    if (handle.pointed.used == 0) return 0
    return if (handle.pointed.sign == MP_NEG) -1 else 1
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<mp_int>.toBigInteger(): BigInteger {
    return BigInteger(this)
}

@OptIn(ExperimentalForeignApi::class)
private fun copyCanonicalLimbs(handle: CPointer<mp_int>): ULongArray =
    try {
        val used = handle.pointed.used
        if (used == 0) {
            EMPTY_LIMBS
        } else {
            val dp = handle.pointed.dp!!
            ULongArray(used) { index ->
                dp[index] and CANONICAL_LIMB_MASK
            }
        }
    } catch (t: Throwable) {
        freeMp(handle)
        throw t
    }

@OptIn(ExperimentalForeignApi::class)
private inline fun <R> withBorrowedHandles(
    first: BigInteger,
    second: BigInteger,
    block: (CPointer<mp_int>, CPointer<mp_int>) -> R,
): R =
    first.withBorrowedHandle { firstHandle ->
        second.withBorrowedHandle { secondHandle ->
            block(firstHandle, secondHandle)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private inline fun <R> withBorrowedHandles(
    first: BigInteger,
    second: BigInteger,
    third: BigInteger,
    block: (CPointer<mp_int>, CPointer<mp_int>, CPointer<mp_int>) -> R,
): R =
    first.withBorrowedHandle { firstHandle ->
        second.withBorrowedHandle { secondHandle ->
            third.withBorrowedHandle { thirdHandle ->
                block(firstHandle, secondHandle, thirdHandle)
            }
        }
    }

@OptIn(ExperimentalForeignApi::class)
private inline fun <R> BigInteger.withBorrowedHandle(block: (CPointer<mp_int>) -> R): R {
    val storage = if (limbs.isEmpty()) BORROWED_ZERO_LIMBS else limbs
    return storage.usePinned { pinned ->
        memScoped {
            val handle = alloc<mp_int>()
            handle.used = size
            handle.alloc = storage.size
            handle.sign = if (sign < 0) MP_NEG else MP_ZPOS
            handle.dp = pinned.addressOf(0).reinterpret()
            block(handle.ptr)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun allocMp(sizeHint: Int = 0): CPointer<mp_int> {
    val mp = nativeHeap.alloc<mp_int>().ptr
    val err = if (sizeHint > 0) mp_init_size(mp, sizeHint) else mp_init(mp)
    if (err != MP_OKAY) {
        nativeHeap.free(mp)
        throw OutOfMemoryError("Failed to initialize mp_int")
    }
    return mp
}

/** Free a temporary mp_int that is NOT owned by a BigInteger instance. */
@OptIn(ExperimentalForeignApi::class)
internal fun freeMp(mp: CPointer<mp_int>) {
    mp_clear(mp)
    nativeHeap.free(mp)
}

/**
 * Check a LibTomMath error code; if not OK, free the given temporaries and throw.
 * Use this for operations that should never fail under normal conditions
 * (allocation-only failures).
 */
@OptIn(ExperimentalForeignApi::class)
internal fun checkMp(err: mp_err, vararg temps: CPointer<mp_int>) {
    if (err != MP_OKAY) {
        for (t in temps) freeMp(t)
        throw ArithmeticException("LibTomMath error: $err")
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun validateRadixAndParse(value: String, radix: Int): CPointer<mp_int> {
    if (radix !in 2..36) throw NumberFormatException("Radix out of range: $radix")
    return parseTomMath(value, radix)
}

@OptIn(ExperimentalForeignApi::class)
internal fun validateAndSliceBytes(bytes: ByteArray, off: Int, len: Int): CPointer<mp_int> {
    if (off < 0 || len < 0 || off.toLong() + len.toLong() > bytes.size) {
        throw IndexOutOfBoundsException("Range [$off, ${off.toLong() + len.toLong()}) out of bounds for length ${bytes.size}")
    }
    if (bytes.isEmpty()) throw NumberFormatException("Zero length BigInteger")

    // Match JDK 21 exactly. For len == 0, the constructor still reads bytes[off]
    // before delegating into its positive/negative parsing helpers.
    val leadingByte = bytes[off]
    if (len == 0) {
        if (leadingByte >= 0) {
            return allocMp()
        }

        val magnitude = ((bytes[off - 1].toInt().inv()) and 0xFF) + 1
        val mp = allocMp()
        mp_set_i64(mp, magnitude.toLong())
        mp_neg(mp, mp)
        return mp
    }

    return fromTwosComplement(bytes.copyOfRange(off, off + len))
}

@OptIn(ExperimentalForeignApi::class)
internal fun parseTomMath(value: String, radix: Int): CPointer<mp_int> {
    // JVM accepts a single leading sign character (+ or -), then digits only.
    // Reject malformed inputs: empty after sign, or embedded sign characters.
    if (value.isEmpty()) throw NumberFormatException("Zero length BigInteger")
    val hasSign = value[0] == '+' || value[0] == '-'
    if (hasSign && value.length == 1) {
        throw NumberFormatException("Zero length BigInteger")
    }
    if (hasSign) {
        val rest = value.substring(1)
        if (rest.contains('+') || rest.contains('-')) {
            throw NumberFormatException("Illegal embedded sign character")
        }
    }
    // Strip leading '+' since LibTomMath doesn't accept it (but does accept '-')
    val normalized = if (value.startsWith("+")) value.substring(1) else value
    val mp = allocMp()
    if (mp_read_radix(mp, normalized, radix) != MP_OKAY) {
        freeMp(mp)
        // Match JVM message format
        val msg = if (radix != 10) {
            "For input string: \"$value\" under radix $radix"
        } else {
            "For input string: \"$value\""
        }
        throw NumberFormatException(msg)
    }
    return mp
}

@OptIn(ExperimentalForeignApi::class)
internal fun fromTwosComplement(bytes: ByteArray): CPointer<mp_int> {
    if (bytes.isEmpty()) throw NumberFormatException("Zero length BigInteger")
    val mp = allocMp()
    val negative = (bytes[0].toInt() and 0x80) != 0

    if (!negative) {
        memScoped {
            val buf = allocArray<UByteVar>(bytes.size)
            for (index in bytes.indices) buf[index] = bytes[index].toUByte()
            mp_from_ubin(mp, buf.reinterpret(), bytes.size.toULong())
        }
    } else {
        val mag = bytes.copyOf()
        for (index in mag.indices) {
            mag[index] = mag[index].toInt().inv().toByte()
        }
        var carry = 1
        for (index in mag.indices.reversed()) {
            val sum = (mag[index].toInt() and 0xFF) + carry
            mag[index] = sum.toByte()
            carry = sum shr 8
        }
        memScoped {
            val buf = allocArray<UByteVar>(mag.size)
            for (index in mag.indices) buf[index] = mag[index].toUByte()
            mp_from_ubin(mp, buf.reinterpret(), mag.size.toULong())
        }
        mp_neg(mp, mp)
    }
    return mp
}

// Cached constants
private val MINUS_ONE = bigIntegerOf(-1L)
private val ZERO = BigInteger(0, 0, EMPTY_LIMBS)
private val ONE = newBigIntegerFromLong(1L)
private val TWO = newBigIntegerFromLong(2L)
private val TEN = newBigIntegerFromLong(10L)
private val HUNDRED = newBigIntegerFromLong(100L)

// Top-level factory functions
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

// Operators

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.rem(other: BigInteger): BigInteger {
    if (other.sign == 0) throw ArithmeticException("BigInteger divide by zero")
    if (sign == 0) return ZERO
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

private fun addMagnitudeOne(sign: Int, value: BigInteger): BigInteger {
    val size = value.size
    val limbs = value.limbs
    val result = ULongArray(size + 1)
    var carry = 1UL
    var index = 0
    while (index < size) {
        val sum = limbs[index] + carry
        result[index] = sum and CANONICAL_LIMB_MASK
        carry = sum shr CANONICAL_LIMB_BITS
        index++
        if (carry == 0UL) {
            limbs.copyInto(result, destinationOffset = index, startIndex = index, endIndex = size)
            return BigInteger(sign, size, result)
        }
    }
    result[size] = carry
    return BigInteger(sign, size + 1, result)
}

private fun subtractMagnitudeOne(sign: Int, value: BigInteger): BigInteger {
    val size = value.size
    val limbs = value.limbs
    if (size == 1) {
        val digit = limbs[0]
        return if (digit == 1UL) ZERO else BigInteger(sign, 1, ulongArrayOf(digit - 1UL))
    }

    val result = ULongArray(size)
    var index = 0
    while (limbs[index] == 0UL) {
        result[index] = CANONICAL_LIMB_MASK
        index++
    }
    result[index] = limbs[index] - 1UL
    index++
    limbs.copyInto(result, destinationOffset = index, startIndex = index, endIndex = size)
    val resultSize = if (result[size - 1] == 0UL) size - 1 else size
    return BigInteger(sign, resultSize, result)
}
