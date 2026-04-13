package io.github.artificialpb.bignum

import io.github.artificialpb.bignum.tommath.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class BigInteger internal constructor(
    internal val handle: CPointer<mp_int>
) : Comparable<BigInteger> {

    actual constructor(value: String) : this(parseTomMath(value, 10))

    actual constructor(value: String, radix: Int) : this(validateRadixAndParse(value, radix))

    actual constructor(bytes: ByteArray) : this(fromTwosComplement(bytes))

    actual constructor(bytes: ByteArray, off: Int, len: Int) : this(
        validateAndSliceBytes(bytes, off, len)
    )

    // Arithmetic

    actual fun abs(): BigInteger {
        val result = allocMp()
        mp_abs(handle, result)
        return BigInteger(result)
    }

    actual fun pow(exponent: Int): BigInteger {
        if (exponent < 0) throw ArithmeticException("Negative exponent")
        val result = allocMp()
        mp_expt_n(handle, exponent, result)
        return BigInteger(result)
    }

    actual fun mod(modulus: BigInteger): BigInteger {
        if (modulus.signum() <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        val result = allocMp()
        mp_mod(handle, modulus.handle, result)
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
            mp_abs(exponent.handle, absExp)
            mp_exptmod(inverse.handle, absExp, modulus.handle, result)
            mp_clear(absExp); nativeHeap.free(absExp)
            return BigInteger(result)
        }
        val result = allocMp()
        mp_exptmod(handle, exponent.handle, modulus.handle, result)
        return BigInteger(result)
    }

    actual fun modInverse(modulus: BigInteger): BigInteger {
        if (modulus.signum() <= 0) throw ArithmeticException("BigInteger: modulus not positive")
        // JVM: x.modInverse(1) == 0 for any x
        if (modulus == BigIntegers.ONE) return BigIntegers.ZERO
        val result = allocMp()
        val err = mp_invmod(handle, modulus.handle, result)
        if (err != MP_OKAY) {
            mp_clear(result)
            nativeHeap.free(result)
            throw ArithmeticException("BigInteger not invertible")
        }
        return BigInteger(result)
    }

    actual fun divideAndRemainder(other: BigInteger): Array<BigInteger> {
        if (other.signum() == 0) throw ArithmeticException("BigInteger divide by zero")
        val quotient = allocMp()
        val remainder = allocMp()
        mp_div(handle, other.handle, quotient, remainder)
        return arrayOf(BigInteger(quotient), BigInteger(remainder))
    }

    actual fun gcd(other: BigInteger): BigInteger {
        val result = allocMp()
        mp_gcd(handle, other.handle, result)
        return BigInteger(result)
    }

    // Bitwise

    actual fun and(other: BigInteger): BigInteger {
        val result = allocMp()
        mp_and(handle, other.handle, result)
        return BigInteger(result)
    }

    actual fun or(other: BigInteger): BigInteger {
        val result = allocMp()
        mp_or(handle, other.handle, result)
        return BigInteger(result)
    }

    actual fun xor(other: BigInteger): BigInteger {
        val result = allocMp()
        mp_xor(handle, other.handle, result)
        return BigInteger(result)
    }

    actual fun not(): BigInteger {
        val result = allocMp()
        mp_complement(handle, result)
        return BigInteger(result)
    }

    actual fun andNot(other: BigInteger): BigInteger {
        val notOther = allocMp()
        mp_complement(other.handle, notOther)
        val result = allocMp()
        mp_and(handle, notOther, result)
        mp_clear(notOther)
        nativeHeap.free(notOther)
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
        val result = allocMp()
        mp_mul_2d(handle, n, result)
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
        mp_signed_rsh(handle, n, result)
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
            mp_abs(handle, absMinusOne)
            mp_decr(absMinusOne)
            val digitIndex = n / MP_DIGIT_BIT
            val bitIndex = n % MP_DIGIT_BIT
            val isSet = if (digitIndex >= absMinusOne.pointed.used) {
                true
            } else {
                (absMinusOne.pointed.dp!![digitIndex].toLong() ushr bitIndex) and 1L == 0L
            }
            mp_clear(absMinusOne); nativeHeap.free(absMinusOne)
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
        mp_2expt(bitMask, n)
        val result = allocMp()
        mp_xor(handle, bitMask, result)
        mp_clear(bitMask); nativeHeap.free(bitMask)
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
        mp_abs(handle, absMinusOne)
        mp_decr(absMinusOne)
        val bits = if (absMinusOne.pointed.used == 0) 0 else mp_count_bits(absMinusOne)
        mp_clear(absMinusOne); nativeHeap.free(absMinusOne)
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
            mp_abs(handle, target)
            mp_decr(target)
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
            mp_clear(target); nativeHeap.free(target)
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
            mp_abs(handle, abs)
            abs
        } else {
            handle
        }
        val result = alloc<IntVar>()
        mp_prime_is_prime(target, certainty.coerceAtLeast(1), result.ptr)
        if (target != handle) {
            mp_clear(target); nativeHeap.free(target)
        }
        result.value != 0
    }

    actual fun nextProbablePrime(): BigInteger {
        if (signum() < 0) throw ArithmeticException("start < 0: $this")
        val result = allocMp()
        mp_copy(handle, result)
        mp_prime_next_prime(result, 8, 0)
        return BigInteger(result)
    }

    // Roots

    actual fun sqrt(): BigInteger {
        if (signum() < 0) throw ArithmeticException("Negative BigInteger")
        val result = allocMp()
        mp_sqrt(handle, result)
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

    actual fun toDouble(): Double = mp_get_double(handle)

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

@OptIn(ExperimentalForeignApi::class)
internal fun allocMp(): CPointer<mp_int> {
    val mp = nativeHeap.alloc<mp_int>().ptr
    mp_init(mp)
    return mp
}

@OptIn(ExperimentalForeignApi::class)
internal fun validateRadixAndParse(value: String, radix: Int): CPointer<mp_int> {
    if (radix !in 2..36) throw NumberFormatException("Radix out of range: $radix")
    return parseTomMath(value, radix)
}

@OptIn(ExperimentalForeignApi::class)
internal fun validateAndSliceBytes(bytes: ByteArray, off: Int, len: Int): CPointer<mp_int> {
    if (off < 0 || len < 0 || off + len > bytes.size) {
        throw IndexOutOfBoundsException("Range [$off, ${off + len}) out of bounds for length ${bytes.size}")
    }
    if (len == 0) {
        // JVM: zero-length on empty array throws NumberFormatException
        // JVM: zero-length at off == size throws ArrayIndexOutOfBoundsException
        // JVM: zero-length at off < size returns 0
        if (bytes.isEmpty()) throw NumberFormatException("Zero length BigInteger")
        if (off >= bytes.size) throw IndexOutOfBoundsException("Range [$off, $off) out of bounds for length ${bytes.size}")
        val mp = allocMp()
        return mp
    }
    return fromTwosComplement(bytes.copyOfRange(off, off + len))
}

@OptIn(ExperimentalForeignApi::class)
internal fun parseTomMath(value: String, radix: Int): CPointer<mp_int> {
    // JVM accepts a single leading '+'; LibTomMath does not
    // Reject malformed inputs like "+-1" or "+"
    val normalized = if (value.startsWith("+")) {
        val rest = value.substring(1)
        if (rest.isEmpty() || rest.startsWith("-") || rest.startsWith("+")) {
            throw NumberFormatException("Invalid BigInteger string: $value")
        }
        rest
    } else {
        value
    }
    if (normalized.isEmpty()) throw NumberFormatException("Zero length BigInteger")
    val mp = allocMp()
    if (mp_read_radix(mp, normalized, radix) != MP_OKAY) {
        mp_clear(mp)
        nativeHeap.free(mp)
        throw NumberFormatException("Invalid BigInteger string: $value")
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
private inline fun binaryOp(
    a: BigInteger,
    b: BigInteger,
    op: (CPointer<mp_int>, CPointer<mp_int>, CPointer<mp_int>) -> Unit
): BigInteger {
    val result = allocMp()
    op(a.handle, b.handle, result)
    return BigInteger(result)
}

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.plus(other: BigInteger): BigInteger =
    binaryOp(this, other) { a, b, r -> mp_add(a, b, r) }

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.minus(other: BigInteger): BigInteger =
    binaryOp(this, other) { a, b, r -> mp_sub(a, b, r) }

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.times(other: BigInteger): BigInteger =
    binaryOp(this, other) { a, b, r -> mp_mul(a, b, r) }

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.div(other: BigInteger): BigInteger {
    if (other.signum() == 0) throw ArithmeticException("BigInteger divide by zero")
    val result = allocMp()
    mp_div(this.handle, other.handle, result, null)
    return BigInteger(result)
}

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.rem(other: BigInteger): BigInteger {
    if (other.signum() == 0) throw ArithmeticException("BigInteger divide by zero")
    val result = allocMp()
    mp_div(this.handle, other.handle, null, result)
    return BigInteger(result)
}

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.unaryMinus(): BigInteger {
    val result = allocMp()
    mp_neg(this.handle, result)
    return BigInteger(result)
}

// inc/dec

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.inc(): BigInteger {
    val result = allocMp()
    mp_copy(this.handle, result)
    mp_incr(result)
    return BigInteger(result)
}

@OptIn(ExperimentalForeignApi::class)
actual operator fun BigInteger.dec(): BigInteger {
    val result = allocMp()
    mp_copy(this.handle, result)
    mp_decr(result)
    return BigInteger(result)
}

// Additional operations

@OptIn(ExperimentalForeignApi::class)
actual fun BigInteger.lcm(other: BigInteger): BigInteger {
    if (this.signum() == 0 || other.signum() == 0) return BigIntegers.ZERO
    // Match JVM semantics: result = (this / gcd) * other (preserves sign)
    val g = allocMp()
    mp_gcd(this.handle, other.handle, g)
    val quot = allocMp()
    mp_div(this.handle, g, quot, null)
    val result = allocMp()
    mp_mul(quot, other.handle, result)
    mp_clear(g); nativeHeap.free(g)
    mp_clear(quot); nativeHeap.free(quot)
    return BigInteger(result)
}
