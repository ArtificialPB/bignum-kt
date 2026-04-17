package io.github.artificialpb.bignum

import io.github.artificialpb.bignum.tommath.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
internal fun signumFromHandle(handle: CPointer<mp_int>): Int {
    if (handle.pointed.used == 0) return 0
    return if (handle.pointed.sign == MP_NEG) -1 else 1
}

@OptIn(ExperimentalForeignApi::class)
internal fun CPointer<mp_int>.toBigInteger(): BigInteger {
    return BigInteger(this)
}

@OptIn(ExperimentalForeignApi::class)
internal fun copyCanonicalLimbs(handle: CPointer<mp_int>): ULongArray =
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
internal inline fun <R> withBorrowedHandles(
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
internal inline fun <R> withBorrowedHandles(
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
internal inline fun <R> BigInteger.withBorrowedHandle(block: (CPointer<mp_int>) -> R): R {
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
