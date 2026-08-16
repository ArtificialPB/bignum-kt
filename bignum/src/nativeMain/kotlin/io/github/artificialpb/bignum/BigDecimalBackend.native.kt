@file:OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)

package io.github.artificialpb.bignum

import io.github.artificialpb.bignum.tommath.MP_EQ
import io.github.artificialpb.bignum.tommath.MP_GT
import io.github.artificialpb.bignum.tommath.MP_LT
import io.github.artificialpb.bignum.tommath.MP_OKAY
import io.github.artificialpb.bignum.tommath.mp_cmp_mag
import io.github.artificialpb.bignum.tommath.mp_div
import io.github.artificialpb.bignum.tommath.mp_div_d
import io.github.artificialpb.bignum.tommath.mp_expt_u32
import io.github.artificialpb.bignum.tommath.mp_mul
import io.github.artificialpb.bignum.tommath.mp_mul_2
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

internal actual object BigDecimalBackend {
    actual fun multiplyByPowerOfTen(value: BigInteger, power: Int, addedBits: Long): BigInteger {
        return bigIntegerOf(10).withBorrowedHandle { tenHandle ->
            value.withBorrowedHandle { valueHandle ->
                val factor = allocMp(estimatedCanonicalLimbs(addedBits))
                checkMp(mp_expt_u32(tenHandle, power.toUInt(), factor), factor)
                val result = allocMp(estimatedCanonicalLimbs(value.bitLength().toLong() + addedBits))
                checkMp(mp_mul(valueHandle, factor, result), factor, result)
                freeMp(factor)
                BigInteger(result)
            }
        }
    }

    actual fun stripSmallFactor(value: BigInteger, factor: ULong, maxCount: Int): SmallFactorReduction {
        if (maxCount <= 0 || value.signum() == 0) return SmallFactorReduction(value, 0)

        return value.withBorrowedHandle { handle ->
            var current = handle
            var ownedCurrent: CPointer<io.github.artificialpb.bignum.tommath.mp_int>? = null
            var count = 0

            memScoped {
                val remainder = alloc<ULongVar>()
                while (count < maxCount) {
                    val quotient = allocMp(maxOf(current.pointed.used, 1))
                    val err = mp_div_d(current, factor, quotient, remainder.ptr)
                    if (err != MP_OKAY) {
                        ownedCurrent?.let { freeMp(it) }
                        checkMp(err, quotient)
                    }
                    if (remainder.value != 0UL) {
                        freeMp(quotient)
                        break
                    }

                    ownedCurrent?.let { freeMp(it) }
                    current = quotient
                    ownedCurrent = quotient
                    count++
                }
            }

            val reduced = ownedCurrent ?: return@withBorrowedHandle SmallFactorReduction(value, 0)
            SmallFactorReduction(BigInteger(reduced), count)
        }
    }

    actual fun singleLimbMagnitudeOrNull(value: BigInteger): ULong? = if (value.size == 1) value.limbs[0] else null

    actual fun multiplyByUnsignedMagnitude(value: BigInteger, digit: ULong, digitSign: Int): BigInteger {
        require(digitSign != 0)
        require(digit != 0UL)
        if (value.signum() == 0) return ZERO
        if (digit == 1UL) return if (digitSign > 0) value else -value

        val digitLow = digit and SINGLE_LIMB_HALF_MASK
        val digitHigh = digit shr SINGLE_LIMB_HALF_BITS
        val result = ULongArray(value.size + 1)
        var carry = 0UL
        for (index in 0 until value.size) {
            val limb = value.limbs[index]
            val limbLow = limb and SINGLE_LIMB_HALF_MASK
            val limbHigh = limb shr SINGLE_LIMB_HALF_BITS
            val p0 = limbLow * digitLow
            val mid = limbHigh * digitLow + limbLow * digitHigh
            val p3 = limbHigh * digitHigh
            val lowSum = p0 + ((mid and SINGLE_LIMB_HALF_MASK) shl SINGLE_LIMB_HALF_BITS)
            val productLow = lowSum and CANONICAL_LIMB_MASK
            val productHigh = p3 + (lowSum shr CANONICAL_LIMB_BITS) + (mid shr SINGLE_LIMB_HALF_BITS)
            val sum = productLow + carry
            result[index] = sum and CANONICAL_LIMB_MASK
            carry = productHigh + (sum shr CANONICAL_LIMB_BITS)
        }
        if (carry != 0UL) result[value.size] = carry
        return bigIntegerFromLimbs(
            value.signum() * digitSign,
            if (carry != 0UL) value.size + 1 else value.size,
            result,
        )
    }

    actual fun multiplyCompactMagnitudes(left: ULong, right: ULong, sign: Int): BigInteger {
        require(sign != 0)
        if (left == 0UL || right == 0UL) return ZERO

        // Recreate the JDK-style compact multiply path with an explicit 128-bit product.
        val leftLow = left and COMPACT_WORD_MASK
        val leftHigh = left shr 32
        val rightLow = right and COMPACT_WORD_MASK
        val rightHigh = right shr 32
        val p0 = leftLow * rightLow
        val p1 = leftLow * rightHigh
        val p2 = leftHigh * rightLow
        val p3 = leftHigh * rightHigh
        val middle = (p0 shr 32) + (p1 and COMPACT_WORD_MASK) + (p2 and COMPACT_WORD_MASK)
        val low = (p0 and COMPACT_WORD_MASK) or ((middle and COMPACT_WORD_MASK) shl 32)
        val high = p3 + (p1 shr 32) + (p2 shr 32) + (middle shr 32)

        if (high == 0UL) {
            val magnitude = bigIntegerOfUnsignedMagnitude(low)
            return if (sign > 0) magnitude else -magnitude
        }

        val limbs = ulongArrayOf(
            low and CANONICAL_LIMB_MASK,
            (
                (low shr CANONICAL_LIMB_BITS) or
                    ((high and COMPACT_HIGH_LOW_MASK) shl COMPACT_REMAINDER_BITS)
                ) and CANONICAL_LIMB_MASK,
            high shr COMPACT_HIGH_LOW_BITS,
        )
        return bigIntegerFromLimbs(sign, limbs.size, limbs)
    }

    actual fun magnitudeAsULongOrNull(value: BigInteger): ULong? = when (value.size) {
        0 -> 0UL
        1 -> value.limbs[0]
        2 -> {
            val upper = value.limbs[1]
            if (upper >= UNSIGNED_ULONG_UPPER_LIMB_EXCLUSIVE) {
                null
            } else {
                (upper shl CANONICAL_LIMB_BITS) or value.limbs[0]
            }
        }

        else -> null
    }

    actual fun divisionByDigitMagnitudeOrNull(value: BigInteger): ULong? = when (value.size) {
        0 -> 0UL
        1 -> value.limbs[0]
        else -> null
    }

    actual fun divideAndRemainderByDigit(
        value: BigInteger,
        divisor: ULong,
        divisorSign: Int,
    ): SmallDigitDivision {
        require(divisor != 0UL) { "Division by zero" }
        require(divisorSign != 0) { "Division by zero" }
        return value.withBorrowedHandle { handle ->
            val quotient = allocMp(maxOf(handle.pointed.used, 1))
            memScoped {
                val remainder = alloc<ULongVar>()
                checkMp(mp_div_d(handle, divisor, quotient, remainder.ptr), quotient)
                val quotientMagnitude = BigInteger(quotient)
                val signedQuotient = if (divisorSign < 0 && quotientMagnitude.signum() != 0) {
                    -quotientMagnitude
                } else {
                    quotientMagnitude
                }
                val remainderMagnitude = bigIntegerOfUnsignedMagnitude(remainder.value)
                val signedRemainder = if (value.signum() < 0 && remainderMagnitude.signum() != 0) {
                    -remainderMagnitude
                } else {
                    remainderMagnitude
                }
                SmallDigitDivision(signedQuotient, signedRemainder)
            }
        }
    }

    actual fun divideExactQuotientOrNull(dividend: BigInteger, divisor: BigInteger): BigInteger? {
        require(divisor.signum() != 0) { "Division by zero" }
        if (dividend.signum() == 0) return ZERO

        divisionByDigitMagnitudeOrNull(divisor)?.let { divisorDigit ->
            val division = divideAndRemainderByDigit(dividend, divisorDigit, divisor.signum())
            return if (division.remainder.signum() == 0) division.quotient else null
        }

        if (dividend.size <= SCHOOLBOOK_DIV_THRESHOLD && divisor.size <= SCHOOLBOOK_DIV_THRESHOLD) {
            val dividendMagnitude = if (dividend.signum() < 0) dividend.abs() else dividend
            val divisorMagnitude = if (divisor.signum() < 0) divisor.abs() else divisor
            val quotientSign = dividend.signum() * divisor.signum()
            return divRemMagnitude(dividendMagnitude, divisorMagnitude) { quotient, remainder ->
                if (remainder.signum() != 0) {
                    null
                } else if (quotientSign < 0 && quotient.signum() != 0) {
                    -quotient
                } else {
                    quotient
                }
            }
        }

        return withBorrowedHandles(dividend, divisor) { dividendHandle, divisorHandle ->
            val quotient = allocMp()
            val remainder = allocMp()
            checkMp(mp_div(dividendHandle, divisorHandle, quotient, remainder), quotient, remainder)
            if (remainder.pointed.used != 0) {
                freeMp(quotient)
                freeMp(remainder)
                return@withBorrowedHandles null
            }
            freeMp(remainder)
            BigInteger(quotient)
        }
    }

    actual fun scaledDigitMagnitudeOrNull(value: BigInteger, factor: ULong): ULong? {
        val digit = divisionByDigitMagnitudeOrNull(value) ?: return null
        if (digit == 0UL || digit > CANONICAL_LIMB_MASK / factor) return null
        return digit * factor
    }

    actual fun divideAndRemainderByDigitWithScaledQuotient(
        value: BigInteger,
        divisor: ULong,
        divisorSign: Int,
        quotientScaleFactor: ULong,
    ): SmallDigitDivision {
        require(divisor != 0UL) { "Division by zero" }
        require(divisorSign != 0) { "Division by zero" }
        return value.withBorrowedHandle { handle ->
            val quotient = allocMp(maxOf(handle.pointed.used, 1))
            memScoped {
                val remainder = alloc<ULongVar>()
                checkMp(mp_div_d(handle, divisor, quotient, remainder.ptr), quotient)
                val signedQuotient = multiplyByUnsignedMagnitude(
                    BigInteger(quotient),
                    quotientScaleFactor,
                    if (divisorSign < 0) -1 else 1,
                )
                val remainderMagnitude = bigIntegerOfUnsignedMagnitude(remainder.value)
                val signedRemainder = if (value.signum() < 0 && remainderMagnitude.signum() != 0) {
                    -remainderMagnitude
                } else {
                    remainderMagnitude
                }
                SmallDigitDivision(signedQuotient, signedRemainder)
            }
        }
    }

    actual fun bigIntegerOfUnsignedMagnitude(value: ULong): BigInteger = when {
        value == 0UL -> ZERO
        value < CANONICAL_LIMB_BASE -> BigInteger(1, 1, ulongArrayOf(value))
        else -> {
            val lower = value and CANONICAL_LIMB_MASK
            val upper = value shr CANONICAL_LIMB_BITS
            BigInteger(1, 2, ulongArrayOf(lower, upper))
        }
    }

    actual fun divideByPowerOfTen(
        value: BigInteger,
        power: Int,
        digitDivisor: ULong?,
        cachedDivisor: BigInteger?,
        addedBits: Long,
    ): PowerOfTenDivision {
        digitDivisor?.let { divisor ->
            return value.withBorrowedHandle { handle ->
                val quotient = allocMp(maxOf(handle.pointed.used, 1))
                memScoped {
                    val remainder = alloc<ULongVar>()
                    checkMp(mp_div_d(handle, divisor, quotient, remainder.ptr), quotient)
                    val remainderMagnitude = bigIntegerOfUnsignedMagnitude(remainder.value)
                    val signedRemainder = if (value.signum() < 0 && remainderMagnitude.signum() != 0) {
                        -remainderMagnitude
                    } else {
                        remainderMagnitude
                    }
                    PowerOfTenDivision(
                        BigInteger(quotient),
                        signedRemainder,
                        compareDigitRemainderToHalfDivisor(remainder.value, divisor),
                    )
                }
            }
        }

        cachedDivisor?.let { divisor ->
            return withBorrowedHandles(value, divisor) { valueHandle, divisorHandle ->
                val quotient = allocMp()
                val remainder = allocMp()
                checkMp(mp_div(valueHandle, divisorHandle, quotient, remainder), quotient, remainder)
                val compareHalf = compareRemainderToHalfDivisor(remainder, divisorHandle)
                PowerOfTenDivision(BigInteger(quotient), BigInteger(remainder), compareHalf)
            }
        }

        return bigIntegerOf(10).withBorrowedHandle { tenHandle ->
            value.withBorrowedHandle { valueHandle ->
                val divisor = allocMp(estimatedCanonicalLimbs(addedBits))
                checkMp(mp_expt_u32(tenHandle, power.toUInt(), divisor), divisor)
                val quotient = allocMp()
                val remainder = allocMp()
                checkMp(mp_div(valueHandle, divisor, quotient, remainder), divisor, quotient, remainder)
                val compareHalf = compareRemainderToHalfDivisor(remainder, divisor)
                freeMp(divisor)
                PowerOfTenDivision(BigInteger(quotient), BigInteger(remainder), compareHalf)
            }
        }
    }

    actual fun magnitudeBitLength(value: BigInteger): Int {
        if (value.size == 0) return 0
        val highLimbBits = ULong.SIZE_BITS - value.limbs[value.size - 1].countLeadingZeroBits()
        return (((value.size - 1).toLong() * CANONICAL_LIMB_BITS) + highLimbBits.toLong()).toInt()
    }
}

private val UNSIGNED_ULONG_UPPER_LIMB_EXCLUSIVE = 1UL shl (64 - CANONICAL_LIMB_BITS)
private const val COMPACT_WORD_MASK = 0xFFFF_FFFFUL
private const val COMPACT_REMAINDER_BITS = 64 - CANONICAL_LIMB_BITS
private const val COMPACT_HIGH_LOW_BITS = CANONICAL_LIMB_BITS - COMPACT_REMAINDER_BITS
private val COMPACT_HIGH_LOW_MASK = (1UL shl COMPACT_HIGH_LOW_BITS) - 1UL
private const val SINGLE_LIMB_HALF_BITS = 30
private const val SINGLE_LIMB_HALF_MASK = 0x3FFF_FFFFUL

private fun estimatedCanonicalLimbs(bitLength: Long): Int = maxOf(1L, (bitLength + CANONICAL_LIMB_BITS - 1L) / CANONICAL_LIMB_BITS).toInt()

private fun compareRemainderToHalfDivisor(
    remainderHandle: CPointer<io.github.artificialpb.bignum.tommath.mp_int>,
    divisorHandle: CPointer<io.github.artificialpb.bignum.tommath.mp_int>,
): Int {
    val doubledRemainder = allocMp(maxOf(remainderHandle.pointed.used + 1, 1))
    checkMp(mp_mul_2(remainderHandle, doubledRemainder), doubledRemainder)
    val comparison = mp_cmp_mag(doubledRemainder, divisorHandle)
    freeMp(doubledRemainder)
    return when (comparison) {
        MP_LT -> -1
        MP_EQ -> 0
        MP_GT -> 1
        else -> error("Unexpected comparison result: $comparison")
    }
}

private fun compareDigitRemainderToHalfDivisor(remainder: ULong, divisor: ULong): Int {
    val doubledRemainder = remainder * 2UL
    return when {
        doubledRemainder < divisor -> -1
        doubledRemainder > divisor -> 1
        else -> 0
    }
}
