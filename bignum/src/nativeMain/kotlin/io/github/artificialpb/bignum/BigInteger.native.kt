package io.github.artificialpb.bignum

import io.github.artificialpb.bignum.tommath.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
actual class BigInteger internal constructor(
    internal val handle: CPointer<mp_int>
) : Comparable<BigInteger> {

    @Suppress("unused")
    private val cleaner = createCleaner(handle) { ptr ->
        mp_clear(ptr)
        nativeHeap.free(ptr)
    }

    actual constructor(value: String) : this(parseTomMath(value, 10))

    actual constructor(value: String, radix: Int) : this(validateRadixAndParse(value, radix))

    actual constructor(bytes: ByteArray) : this(fromTwosComplement(bytes))

    actual constructor(bytes: ByteArray, off: Int, len: Int) : this(
        validateAndSliceBytes(bytes, off, len)
    )

    // Arithmetic

    actual fun add(other: BigInteger): BigInteger {
        val result = allocMp()
        checkMp(mp_add(handle, other.handle, result), result)
        return BigInteger(result)
    }

    actual fun subtract(other: BigInteger): BigInteger {
        val result = allocMp()
        checkMp(mp_sub(handle, other.handle, result), result)
        return BigInteger(result)
    }

    actual fun multiply(other: BigInteger): BigInteger {
        val result = allocMp()
        checkMp(mp_mul(handle, other.handle, result), result)
        return BigInteger(result)
    }

    actual fun divide(other: BigInteger): BigInteger {
        if (other.signum() == 0) throw ArithmeticException("BigInteger divide by zero")
        val result = allocMp()
        checkMp(mp_div(handle, other.handle, result, null), result)
        return BigInteger(result)
    }

    actual fun abs(): BigInteger {
        if (signum() >= 0) return this

        val result = allocMp()
        checkMp(mp_abs(handle, result), result)
        return BigInteger(result)
    }

    actual fun pow(exponent: Int): BigInteger {
        if (exponent < 0) throw ArithmeticException("Negative exponent")
        if (signum() == 0) return if (exponent == 0) BigIntegers.ONE else BigIntegers.ZERO

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
            return if (signum() < 0 && exponent % 2 == 1) {
                BigIntegers.of(-1L).shiftLeft(bitsToShift.toInt())
            } else {
                BigIntegers.ONE.shiftLeft(bitsToShift.toInt())
            }
        }

        // JVM: (long)remainingBits * exponent / Integer.SIZE > MAX_MAG_LENGTH
        if (remainingBits * exponent.toLong() / 32 > MAX_MAG_LENGTH) {
            throw ArithmeticException("BigInteger would overflow supported range")
        }

        val result = allocMp()
        val err = mp_expt_n(handle, exponent, result)
        if (err != MP_OKAY) {
            freeMp(result)
            throw ArithmeticException("BigInteger would overflow supported range")
        }
        return BigInteger(result)
    }

    actual fun mod(modulus: BigInteger): BigInteger {
        if (modulus.signum() <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        val result = allocMp()
        checkMp(mp_mod(handle, modulus.handle, result), result)
        return BigInteger(result)
    }

    actual fun modPow(exponent: BigInteger, modulus: BigInteger): BigInteger {
        if (modulus.signum() <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        // x^e mod 1 == 0 for all x, e
        if (modulus == BigIntegers.ONE) return BigIntegers.ZERO
        if (exponent.signum() < 0) {
            // JVM semantics: modPow(negExp, mod) = modInverse(this, mod)^|negExp| mod mod
            val inverse = modInverse(modulus)
            val result = allocMp()
            val absExp = allocMp()
            checkMp(mp_abs(exponent.handle, absExp), absExp)
            checkMp(mp_exptmod(inverse.handle, absExp, modulus.handle, result), result)
            freeMp(absExp)
            return BigInteger(result)
        }
        val result = allocMp()
        checkMp(mp_exptmod(handle, exponent.handle, modulus.handle, result), result)
        return BigInteger(result)
    }

    actual fun modInverse(modulus: BigInteger): BigInteger {
        if (modulus.signum() <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        // JVM: x.modInverse(1) == 0 for any x
        if (modulus == BigIntegers.ONE) return BigIntegers.ZERO
        // mp_invmod gives incorrect results for negative inputs, so always
        // compute the inverse of the absolute value and adjust for sign.
        val absHandle = if (signum() < 0) {
            val abs = allocMp()
            checkMp(mp_abs(handle, abs), abs)
            abs
        } else {
            handle
        }
        val result = allocMp()
        val err = mp_invmod(absHandle, modulus.handle, result)
        if (absHandle != handle) {
            freeMp(absHandle)
        }
        if (err != MP_OKAY) {
            freeMp(result)
            throw ArithmeticException("BigInteger not invertible")
        }
        val bi = BigInteger(result)
        // For negative input: (-a)^-1 mod m = m - (a^-1 mod m), when a^-1 != 0
        if (signum() < 0 && bi.signum() != 0) {
            return modulus - bi
        }
        return bi
    }

    actual fun divideAndRemainder(other: BigInteger): Array<BigInteger> {
        if (other.signum() == 0) throw ArithmeticException("BigInteger divide by zero")
        val quotient = allocMp()
        val remainder = allocMp()
        checkMp(mp_div(handle, other.handle, quotient, remainder), quotient, remainder)
        return arrayOf(BigInteger(quotient), BigInteger(remainder))
    }

    actual fun gcd(other: BigInteger): BigInteger {
        val result = allocMp()
        checkMp(mp_gcd(handle, other.handle, result), result)
        return BigInteger(result)
    }

    // Bitwise

    actual fun and(other: BigInteger): BigInteger {
        val result = allocMp()
        checkMp(mp_and(handle, other.handle, result), result)
        return BigInteger(result)
    }

    actual fun or(other: BigInteger): BigInteger {
        val result = allocMp()
        checkMp(mp_or(handle, other.handle, result), result)
        return BigInteger(result)
    }

    actual fun xor(other: BigInteger): BigInteger {
        val result = allocMp()
        checkMp(mp_xor(handle, other.handle, result), result)
        return BigInteger(result)
    }

    actual fun not(): BigInteger {
        val result = allocMp()
        checkMp(mp_complement(handle, result), result)
        return BigInteger(result)
    }

    actual fun andNot(other: BigInteger): BigInteger {
        val notOther = allocMp()
        checkMp(mp_complement(other.handle, notOther), notOther)
        val result = allocMp()
        val err = mp_and(handle, notOther, result)
        freeMp(notOther)
        if (err != MP_OKAY) {
            freeMp(result)
            throw ArithmeticException("LibTomMath error: $err")
        }
        return BigInteger(result)
    }

    actual fun shiftLeft(n: Int): BigInteger {
        if (n < 0) {
            if (n == Int.MIN_VALUE) {
                // shiftLeft(MIN_VALUE) = shiftRight(2^31): arithmetic right shift by huge amount
                // positive/zero → 0, negative → -1
                return if (signum() < 0) BigIntegers.of(-1L) else BigIntegers.ZERO
            }
            return shiftRight(-n)
        }
        if (signum() != 0 && n.toLong() + bitLength().toLong() > MAX_BIT_LENGTH) {
            throw ArithmeticException("BigInteger would overflow supported range")
        }
        val result = allocMp()
        val err = mp_mul_2d(handle, n, result)
        if (err != MP_OKAY) {
            freeMp(result)
            throw ArithmeticException("BigInteger would overflow supported range")
        }
        return BigInteger(result)
    }

    actual fun shiftRight(n: Int): BigInteger {
        if (n < 0) {
            if (n == Int.MIN_VALUE) {
                // shiftRight(MIN_VALUE) = shiftLeft(2^31): only zero survives
                if (signum() == 0) return BigIntegers.ZERO
                throw ArithmeticException("Shift amount too large")
            }
            return shiftLeft(-n)
        }
        val result = allocMp()
        checkMp(mp_signed_rsh(handle, n, result), result)
        return BigInteger(result)
    }

    actual fun testBit(n: Int): Boolean {
        if (n < 0) throw ArithmeticException("Negative bit address")
        if (signum() >= 0) {
            val digitIndex = n / MP_DIGIT_BIT
            val bitIndex = n % MP_DIGIT_BIT
            if (digitIndex >= handle.pointed.used) return false
            return (handle.pointed.dp!![digitIndex].toLong() ushr bitIndex) and 1L == 1L
        } else {
            val absMinusOne = allocMp()
            checkMp(mp_abs(handle, absMinusOne), absMinusOne)
            checkMp(mp_decr(absMinusOne), absMinusOne)
            val digitIndex = n / MP_DIGIT_BIT
            val bitIndex = n % MP_DIGIT_BIT
            val isSet = if (digitIndex >= absMinusOne.pointed.used) {
                true
            } else {
                (absMinusOne.pointed.dp!![digitIndex].toLong() ushr bitIndex) and 1L == 0L
            }
            freeMp(absMinusOne)
            return isSet
        }
    }

    actual fun setBit(n: Int): BigInteger {
        if (n < 0) throw ArithmeticException("Negative bit address")
        if (testBit(n)) return this
        return this.flipBit(n)
    }

    actual fun clearBit(n: Int): BigInteger {
        if (n < 0) throw ArithmeticException("Negative bit address")
        if (!testBit(n)) return this
        return this.flipBit(n)
    }

    actual fun flipBit(n: Int): BigInteger {
        if (n < 0) throw ArithmeticException("Negative bit address")
        val bitMask = allocMp()
        checkMp(mp_2expt(bitMask, n), bitMask)
        val result = allocMp()
        val err = mp_xor(handle, bitMask, result)
        freeMp(bitMask)
        if (err != MP_OKAY) {
            freeMp(result)
            throw ArithmeticException("LibTomMath error: $err")
        }
        return BigInteger(result)
    }

    actual fun getLowestSetBit(): Int {
        if (handle.pointed.used == 0) return -1
        return mp_cnt_lsb(handle)
    }

    actual fun bitLength(): Int {
        if (handle.pointed.used == 0) return 0
        if (signum() > 0) return mp_count_bits(handle)
        val absMinusOne = allocMp()
        checkMp(mp_abs(handle, absMinusOne), absMinusOne)
        checkMp(mp_decr(absMinusOne), absMinusOne)
        val bits = if (absMinusOne.pointed.used == 0) 0 else mp_count_bits(absMinusOne)
        freeMp(absMinusOne)
        return bits
    }

    actual fun bitCount(): Int {
        if (handle.pointed.used == 0) return 0

        val target: CPointer<mp_int>
        val needsFree: Boolean
        if (signum() > 0) {
            target = handle
            needsFree = false
        } else {
            target = allocMp()
            checkMp(mp_abs(handle, target), target)
            checkMp(mp_decr(target), target)
            needsFree = true
        }

        var count = 0
        val dp = target.pointed.dp!!
        for (i in 0 until target.pointed.used) {
            var digit = dp[i]
            while (digit != 0uL) {
                digit = digit and (digit - 1uL)
                count++
            }
        }

        if (needsFree) {
            freeMp(target)
        }
        return count
    }

    // Predicates

    actual fun isProbablePrime(certainty: Int): Boolean = memScoped {
        // JVM semantics: certainty <= 0 means always return true
        if (certainty <= 0) return true
        // JVM tests absolute value for primality
        if (handle.pointed.used == 0) return false
        val target = if (signum() < 0) {
            val abs = allocMp()
            checkMp(mp_abs(handle, abs), abs)
            abs
        } else {
            handle
        }
        // JVM certainty means error probability ≤ 2^(-certainty).
        // Each Miller-Rabin round has error ≤ 1/4 = 2^(-2),
        // so ceil(certainty / 2) rounds achieve the required bound.
        val rounds = (certainty + 1) / 2
        val result = alloc<IntVar>()
        checkMp(mp_prime_is_prime(target, rounds.coerceAtLeast(1), result.ptr))
        if (target != handle) {
            freeMp(target)
        }
        result.value != 0
    }

    actual fun nextProbablePrime(): BigInteger {
        if (signum() < 0) throw ArithmeticException("start < 0: $this")
        val result = allocMp()
        checkMp(mp_copy(handle, result), result)
        // JVM uses DEFAULT_PRIME_CERTAINTY = 100, which maps to
        // ceil(100/2) = 50 MR rounds via the same certainty→rounds
        // conversion as isProbablePrime.
        val defaultRounds = (DEFAULT_PRIME_CERTAINTY + 1) / 2
        checkMp(mp_prime_next_prime(result, defaultRounds, 0), result)
        return BigInteger(result)
    }

    // Roots

    actual fun sqrt(): BigInteger {
        if (signum() < 0) throw ArithmeticException("Negative BigInteger")
        val result = allocMp()
        checkMp(mp_sqrt(handle, result), result)
        return BigInteger(result)
    }

    // Conversions

    actual fun toByteArray(): ByteArray {
        val sign = signum()
        if (sign == 0) return byteArrayOf(0)

        val size = mp_ubin_size(handle)
        val magnitude: ByteArray = memScoped {
            val buf = allocArray<UByteVar>(size.toLong())
            val written = alloc<ULongVar>()
            mp_to_ubin(handle, buf.reinterpret(), size, written.ptr)
            val count = written.value.toInt()
            ByteArray(count) { buf[it].toByte() }
        }

        if (sign > 0) {
            return if ((magnitude[0].toInt() and 0x80) != 0) {
                byteArrayOf(0) + magnitude
            } else {
                magnitude
            }
        } else {
            for (i in magnitude.indices) {
                magnitude[i] = magnitude[i].toInt().inv().toByte()
            }
            var carry = 1
            for (i in magnitude.indices.reversed()) {
                val sum = (magnitude[i].toInt() and 0xFF) + carry
                magnitude[i] = sum.toByte()
                carry = sum shr 8
            }
            return if ((magnitude[0].toInt() and 0x80) == 0) {
                byteArrayOf(0xFF.toByte()) + magnitude
            } else {
                magnitude
            }
        }
    }

    actual fun toInt(): Int = mp_get_i32(handle)

    actual fun toLong(): Long = mp_get_i64(handle)

    actual fun toDouble(): Double = toString().toDouble()

    actual fun signum(): Int {
        if (handle.pointed.used == 0) return 0
        return if (handle.pointed.sign == MP_NEG) -1 else 1
    }

    // Comparison

    actual fun min(other: BigInteger): BigInteger =
        if (compareTo(other) <= 0) this else other

    actual fun max(other: BigInteger): BigInteger =
        if (compareTo(other) >= 0) this else other

    actual override fun compareTo(other: BigInteger): Int =
        mp_cmp(handle, other.handle)

    actual fun toString(radix: Int): String {
        // JVM semantics: invalid radix falls back to radix 10
        val effectiveRadix = if (radix in 2..36) radix else 10
        return memScoped {
            val sizeVar = alloc<IntVar>()
            mp_radix_size(handle, effectiveRadix, sizeVar.ptr)
            val size = sizeVar.value
            val buf = allocArray<ByteVar>(size)
            val written = alloc<ULongVar>()
            mp_to_radix(handle, buf, size.toULong(), written.ptr, effectiveRadix)
            buf.toKString().lowercase()
        }
    }

    actual override fun toString(): String = toString(10)

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BigInteger) return false
        return mp_cmp(handle, other.handle) == MP_EQ
    }

    actual override fun hashCode(): Int {
        // Match java.math.BigInteger.hashCode():
        // XOR each 32-bit word of big-endian magnitude with factor 31, then multiply by signum
        if (handle.pointed.used == 0) return 0
        val bytes = toByteArray()
        // Strip sign byte if present to get magnitude
        val start = if (signum() >= 0 && bytes[0] == 0.toByte() && bytes.size > 1) 1 else 0
        // For negative, compute magnitude from two's complement
        val mag: ByteArray
        if (signum() >= 0) {
            mag = if (start == 1) bytes.copyOfRange(1, bytes.size) else bytes
        } else {
            // Get unsigned magnitude bytes
            val size = mp_ubin_size(handle)
            mag = memScoped {
                val buf = allocArray<UByteVar>(size.toLong())
                val written = alloc<ULongVar>()
                mp_to_ubin(handle, buf.reinterpret(), size, written.ptr)
                val count = written.value.toInt()
                ByteArray(count) { buf[it].toByte() }
            }
        }
        // Pad to 4-byte boundary for int conversion
        val padded = if (mag.size % 4 != 0) {
            ByteArray(((mag.size + 3) / 4) * 4 - mag.size) + mag
        } else {
            mag
        }
        var hashCode = 0
        for (i in padded.indices step 4) {
            val word = ((padded[i].toInt() and 0xFF) shl 24) or
                ((padded[i + 1].toInt() and 0xFF) shl 16) or
                ((padded[i + 2].toInt() and 0xFF) shl 8) or
                (padded[i + 3].toInt() and 0xFF)
            hashCode = 31 * hashCode + word
        }
        return hashCode * signum()
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

@OptIn(ExperimentalForeignApi::class)
internal fun allocMp(): CPointer<mp_int> {
    val mp = nativeHeap.alloc<mp_int>().ptr
    if (mp_init(mp) != MP_OKAY) {
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
            for (i in bytes.indices) buf[i] = bytes[i].toUByte()
            mp_from_ubin(mp, buf.reinterpret(), bytes.size.toULong())
        }
    } else {
        val mag = bytes.copyOf()
        for (i in mag.indices) {
            mag[i] = mag[i].toInt().inv().toByte()
        }
        var carry = 1
        for (i in mag.indices.reversed()) {
            val sum = (mag[i].toInt() and 0xFF) + carry
            mag[i] = sum.toByte()
            carry = sum shr 8
        }
        memScoped {
            val buf = allocArray<UByteVar>(mag.size)
            for (i in mag.indices) buf[i] = mag[i].toUByte()
            mp_from_ubin(mp, buf.reinterpret(), mag.size.toULong())
        }
        mp_neg(mp, mp)
    }
    return mp
}

// Constants and factory methods

actual object BigIntegers {
    actual val ZERO: BigInteger = of(0L)
    actual val ONE: BigInteger = of(1L)
    actual val TWO: BigInteger = of(2L)
    actual val TEN: BigInteger = of(10L)

    actual fun of(value: String): BigInteger = BigInteger(value)

    @OptIn(ExperimentalForeignApi::class)
    actual fun of(value: Long): BigInteger {
        val mp = allocMp()
        mp_set_i64(mp, value)
        return BigInteger(mp)
    }
}

// Operators

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.rem(other: BigInteger): BigInteger {
    if (other.signum() == 0) throw ArithmeticException("BigInteger divide by zero")
    val result = allocMp()
    checkMp(mp_div(this.handle, other.handle, null, result), result)
    return BigInteger(result)
}

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.unaryMinus(): BigInteger {
    val result = allocMp()
    checkMp(mp_neg(this.handle, result), result)
    return BigInteger(result)
}

// inc/dec

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.inc(): BigInteger {
    this + BigIntegers.ONE
    val result = allocMp()
    checkMp(mp_copy(this.handle, result), result)
    checkMp(mp_incr(result), result)
    return BigInteger(result)
}

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.dec(): BigInteger {
    val result = allocMp()
    checkMp(mp_copy(this.handle, result), result)
    checkMp(mp_decr(result), result)
    return BigInteger(result)
}

// Additional operations

@OptIn(ExperimentalForeignApi::class)
actual fun BigInteger.lcm(other: BigInteger): BigInteger {
    if (this.signum() == 0 || other.signum() == 0) return BigIntegers.ZERO
    // Match JVM semantics: result = (this / gcd) * other (preserves sign)
    val g = allocMp()
    checkMp(mp_gcd(this.handle, other.handle, g), g)
    val quot = allocMp()
    val err1 = mp_div(this.handle, g, quot, null)
    freeMp(g)
    if (err1 != MP_OKAY) {
        freeMp(quot)
        throw ArithmeticException("LibTomMath error: $err1")
    }
    val result = allocMp()
    val err2 = mp_mul(quot, other.handle, result)
    freeMp(quot)
    if (err2 != MP_OKAY) {
        freeMp(result)
        throw ArithmeticException("LibTomMath error: $err2")
    }
    return BigInteger(result)
}
