package io.github.artificialpb.bignum

import io.github.artificialpb.bignum.tommath.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
internal fun copyCanonicalLimbs(handle: CPointer<mp_int>): ULongArray =
    try {
        val used = handle.pointed.used
        if (used == 0) {
            EMPTY_LIMBS
        } else {
            val dp = handle.pointed.dp!!
            ULongArray(used) { index -> dp[index] and CANONICAL_LIMB_MASK }
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
            handle.sign = if (signum() < 0) MP_NEG else MP_ZPOS
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
