package io.github.artificialpb.bignum

internal fun andPositive(left: BigInteger, right: BigInteger): BigInteger {
    val resultSize = minOf(left.size, right.size)
    val result = ULongArray(resultSize)
    var lastNonZero = 0
    var index = 0
    while (index < resultSize) {
        val digit = left.limbs[index] and right.limbs[index]
        result[index] = digit
        if (digit != 0UL) {
            lastNonZero = index + 1
        }
        index++
    }
    return if (lastNonZero == 0) ZERO else BigInteger(1, lastNonZero, result)
}

internal fun orPositive(left: BigInteger, right: BigInteger): BigInteger {
    val smaller: BigInteger
    val larger: BigInteger
    if (left.size < right.size) {
        smaller = left
        larger = right
    } else {
        smaller = right
        larger = left
    }

    val result = ULongArray(larger.size)
    var index = 0
    while (index < smaller.size) {
        result[index] = larger.limbs[index] or smaller.limbs[index]
        index++
    }
    while (index < larger.size) {
        result[index] = larger.limbs[index]
        index++
    }
    return BigInteger(1, larger.size, result)
}

internal fun xorPositive(left: BigInteger, right: BigInteger): BigInteger {
    val smaller: BigInteger
    val larger: BigInteger
    if (left.size < right.size) {
        smaller = left
        larger = right
    } else {
        smaller = right
        larger = left
    }

    val result = ULongArray(larger.size)
    var lastNonZero = 0
    var index = 0
    while (index < smaller.size) {
        val digit = larger.limbs[index] xor smaller.limbs[index]
        result[index] = digit
        if (digit != 0UL) {
            lastNonZero = index + 1
        }
        index++
    }
    while (index < larger.size) {
        val digit = larger.limbs[index]
        result[index] = digit
        if (digit != 0UL) {
            lastNonZero = index + 1
        }
        index++
    }
    return if (lastNonZero == 0) ZERO else BigInteger(1, lastNonZero, result)
}

internal fun andNotPositive(left: BigInteger, right: BigInteger): BigInteger {
    val result = ULongArray(left.size)
    val overlapSize = minOf(left.size, right.size)
    var lastNonZero = 0
    var index = 0
    while (index < overlapSize) {
        val digit = left.limbs[index] and (right.limbs[index].inv() and CANONICAL_LIMB_MASK)
        result[index] = digit
        if (digit != 0UL) {
            lastNonZero = index + 1
        }
        index++
    }
    while (index < left.size) {
        val digit = left.limbs[index]
        result[index] = digit
        if (digit != 0UL) {
            lastNonZero = index + 1
        }
        index++
    }
    return if (lastNonZero == 0) ZERO else BigInteger(1, lastNonZero, result)
}

internal fun flipBitNonNegative(value: BigInteger, n: Int): BigInteger {
    val digitIndex = n / CANONICAL_LIMB_BITS
    val bitMask = 1UL shl (n % CANONICAL_LIMB_BITS)
    val resultSize = maxOf(value.size, digitIndex + 1)
    val result = ULongArray(resultSize)
    if (value.size > 0) {
        value.limbs.copyInto(result, endIndex = value.size)
    }
    result[digitIndex] = result[digitIndex] xor bitMask
    return bigIntegerFromLimbs(1, resultSize, result)
}

private inline fun combineNegativePayloadsToPositive(
    left: BigInteger,
    right: BigInteger,
    combine: (ULong, ULong) -> ULong,
): BigInteger {
    val resultSize = maxOf(left.size, right.size)
    val result = ULongArray(resultSize)
    var leftBorrowPending = true
    var rightBorrowPending = true
    var lastNonZero = 0
    var index = 0
    while (index < resultSize) {
        val leftRaw = if (index < left.size) left.limbs[index] else 0UL
        val leftPayload = if (leftBorrowPending) {
            if (leftRaw == 0UL) {
                CANONICAL_LIMB_MASK
            } else {
                leftBorrowPending = false
                leftRaw - 1UL
            }
        } else {
            leftRaw
        }
        val rightRaw = if (index < right.size) right.limbs[index] else 0UL
        val rightPayload = if (rightBorrowPending) {
            if (rightRaw == 0UL) {
                CANONICAL_LIMB_MASK
            } else {
                rightBorrowPending = false
                rightRaw - 1UL
            }
        } else {
            rightRaw
        }
        val digit = combine(leftPayload, rightPayload)
        result[index] = digit
        if (digit != 0UL) {
            lastNonZero = index + 1
        }
        index++
    }
    return if (lastNonZero == 0) ZERO else BigInteger(1, lastNonZero, result)
}

private inline fun combineNegativePayloadsToNegative(
    left: BigInteger,
    right: BigInteger,
    combine: (ULong, ULong) -> ULong,
): BigInteger {
    val payloadSize = maxOf(left.size, right.size)
    val result = ULongArray(payloadSize + 1)
    var leftBorrowPending = true
    var rightBorrowPending = true
    var carry = 1UL
    var lastNonZero = 0
    var index = 0
    while (index < payloadSize) {
        val leftRaw = if (index < left.size) left.limbs[index] else 0UL
        val leftPayload = if (leftBorrowPending) {
            if (leftRaw == 0UL) {
                CANONICAL_LIMB_MASK
            } else {
                leftBorrowPending = false
                leftRaw - 1UL
            }
        } else {
            leftRaw
        }
        val rightRaw = if (index < right.size) right.limbs[index] else 0UL
        val rightPayload = if (rightBorrowPending) {
            if (rightRaw == 0UL) {
                CANONICAL_LIMB_MASK
            } else {
                rightBorrowPending = false
                rightRaw - 1UL
            }
        } else {
            rightRaw
        }
        val sum = combine(leftPayload, rightPayload) + carry
        val digit = sum and CANONICAL_LIMB_MASK
        result[index] = digit
        carry = sum shr CANONICAL_LIMB_BITS
        if (digit != 0UL) {
            lastNonZero = index + 1
        }
        index++
    }
    if (carry != 0UL) {
        result[payloadSize] = carry
        lastNonZero = payloadSize + 1
    }
    return BigInteger(-1, lastNonZero, result)
}

private inline fun combinePositiveWithNegativePayloadToPositive(
    positive: BigInteger,
    negative: BigInteger,
    combine: (ULong, ULong) -> ULong,
): BigInteger {
    val result = ULongArray(positive.size)
    var negativeBorrowPending = true
    var lastNonZero = 0
    var index = 0
    while (index < positive.size) {
        val negativeRaw = if (index < negative.size) negative.limbs[index] else 0UL
        val negativePayload = if (negativeBorrowPending) {
            if (negativeRaw == 0UL) {
                CANONICAL_LIMB_MASK
            } else {
                negativeBorrowPending = false
                negativeRaw - 1UL
            }
        } else {
            negativeRaw
        }
        val digit = combine(positive.limbs[index], negativePayload)
        result[index] = digit
        if (digit != 0UL) {
            lastNonZero = index + 1
        }
        index++
    }
    return if (lastNonZero == 0) ZERO else BigInteger(1, lastNonZero, result)
}

private inline fun combinePositiveWithNegativePayloadToNegative(
    positive: BigInteger,
    negative: BigInteger,
    combine: (ULong, ULong) -> ULong,
): BigInteger {
    val payloadSize = maxOf(positive.size, negative.size)
    val result = ULongArray(payloadSize + 1)
    var negativeBorrowPending = true
    var carry = 1UL
    var lastNonZero = 0
    var index = 0
    while (index < payloadSize) {
        val positiveDigit = if (index < positive.size) positive.limbs[index] else 0UL
        val negativeRaw = if (index < negative.size) negative.limbs[index] else 0UL
        val negativePayload = if (negativeBorrowPending) {
            if (negativeRaw == 0UL) {
                CANONICAL_LIMB_MASK
            } else {
                negativeBorrowPending = false
                negativeRaw - 1UL
            }
        } else {
            negativeRaw
        }
        val sum = combine(positiveDigit, negativePayload) + carry
        val digit = sum and CANONICAL_LIMB_MASK
        result[index] = digit
        carry = sum shr CANONICAL_LIMB_BITS
        if (digit != 0UL) {
            lastNonZero = index + 1
        }
        index++
    }
    if (carry != 0UL) {
        result[payloadSize] = carry
        lastNonZero = payloadSize + 1
    }
    return BigInteger(-1, lastNonZero, result)
}

internal fun andPositiveNegative(positive: BigInteger, negative: BigInteger): BigInteger =
    combinePositiveWithNegativePayloadToPositive(positive, negative) { positiveDigit, negativePayload ->
        positiveDigit and (negativePayload.inv() and CANONICAL_LIMB_MASK)
    }

internal fun andNotPositiveNegative(positive: BigInteger, negative: BigInteger): BigInteger =
    combinePositiveWithNegativePayloadToPositive(positive, negative) { positiveDigit, negativePayload ->
        positiveDigit and negativePayload
    }

internal fun xorPositiveNegative(positive: BigInteger, negative: BigInteger): BigInteger =
    combinePositiveWithNegativePayloadToNegative(positive, negative) { positiveDigit, negativePayload ->
        positiveDigit xor negativePayload
    }

internal fun orPositiveNegative(positive: BigInteger, negative: BigInteger): BigInteger =
    combinePositiveWithNegativePayloadToNegative(positive, negative) { positiveDigit, negativePayload ->
        negativePayload and (positiveDigit.inv() and CANONICAL_LIMB_MASK)
    }

internal fun andNotNegativePositive(negative: BigInteger, positive: BigInteger): BigInteger =
    combinePositiveWithNegativePayloadToNegative(positive, negative) { positiveDigit, negativePayload ->
        positiveDigit or negativePayload
    }

internal fun andNegativeNegative(left: BigInteger, right: BigInteger): BigInteger =
    combineNegativePayloadsToNegative(left, right) { leftPayload, rightPayload ->
        leftPayload or rightPayload
    }

internal fun orNegativeNegative(left: BigInteger, right: BigInteger): BigInteger =
    combineNegativePayloadsToNegative(left, right) { leftPayload, rightPayload ->
        leftPayload and rightPayload
    }

internal fun xorNegativeNegative(left: BigInteger, right: BigInteger): BigInteger =
    combineNegativePayloadsToPositive(left, right) { leftPayload, rightPayload ->
        leftPayload xor rightPayload
    }

internal fun andNotNegativeNegative(left: BigInteger, right: BigInteger): BigInteger =
    combineNegativePayloadsToPositive(left, right) { leftPayload, rightPayload ->
        rightPayload and (leftPayload.inv() and CANONICAL_LIMB_MASK)
    }

internal fun addSingleBitMagnitude(sign: Int, value: BigInteger, n: Int): BigInteger {
    val digitIndex = n / CANONICAL_LIMB_BITS
    val bitMask = 1UL shl (n % CANONICAL_LIMB_BITS)
    if (digitIndex >= value.size) {
        val resultSize = digitIndex + 1
        val result = ULongArray(resultSize)
        if (value.size > 0) {
            value.limbs.copyInto(result, endIndex = value.size)
        }
        result[digitIndex] = bitMask
        return BigInteger(sign, resultSize, result)
    }

    val result = ULongArray(value.size + 1)
    if (digitIndex > 0) {
        value.limbs.copyInto(result, endIndex = digitIndex)
    }
    var carry = bitMask
    var index = digitIndex
    while (index < value.size && carry != 0UL) {
        val sum = value.limbs[index] + carry
        result[index] = sum and CANONICAL_LIMB_MASK
        carry = sum shr CANONICAL_LIMB_BITS
        index++
    }
    if (index < value.size) {
        value.limbs.copyInto(result, destinationOffset = index, startIndex = index, endIndex = value.size)
        return BigInteger(sign, value.size, result)
    }
    return if (carry != 0UL) {
        result[value.size] = carry
        BigInteger(sign, value.size + 1, result)
    } else {
        BigInteger(sign, value.size, result)
    }
}

internal fun subtractSingleBitMagnitude(sign: Int, value: BigInteger, n: Int): BigInteger {
    val digitIndex = n / CANONICAL_LIMB_BITS
    val bitMask = 1UL shl (n % CANONICAL_LIMB_BITS)
    val result = ULongArray(value.size)
    if (digitIndex > 0) {
        value.limbs.copyInto(result, endIndex = digitIndex)
    }
    var borrow = bitMask
    var index = digitIndex
    while (true) {
        val digit = value.limbs[index]
        if (digit >= borrow) {
            result[index] = digit - borrow
            index++
            if (index < value.size) {
                value.limbs.copyInto(result, destinationOffset = index, startIndex = index, endIndex = value.size)
            }
            return bigIntegerFromLimbs(sign, value.size, result)
        }
        result[index] = CANONICAL_LIMB_BASE + digit - borrow
        borrow = 1UL
        index++
    }
}

internal fun negativePayloadDigitAt(value: BigInteger, digitIndex: Int): ULong {
    if (digitIndex >= value.size) return 0UL
    for (index in 0 until digitIndex) {
        if (value.limbs[index] != 0UL) {
            return value.limbs[digitIndex]
        }
    }
    val digit = value.limbs[digitIndex]
    return if (digit == 0UL) CANONICAL_LIMB_MASK else digit - 1UL
}

internal fun isPowerOfTwoMagnitude(value: BigInteger): Boolean {
    var seenBit = false
    for (index in 0 until value.size) {
        val digit = value.limbs[index]
        if (digit == 0UL) continue
        if (seenBit || (digit and (digit - 1UL)) != 0UL) {
            return false
        }
        seenBit = true
    }
    return seenBit
}

internal fun shiftLeftMagnitude(sign: Int, value: BigInteger, n: Int): BigInteger {
    val digitShift = n / CANONICAL_LIMB_BITS
    val bitShift = n % CANONICAL_LIMB_BITS
    if (bitShift == 0) {
        val resultSize = value.size + digitShift
        val result = ULongArray(resultSize)
        value.limbs.copyInto(result, destinationOffset = digitShift, endIndex = value.size)
        return BigInteger(sign, resultSize, result)
    }

    val result = ULongArray(value.size + digitShift + 1)
    val carryShift = CANONICAL_LIMB_BITS - bitShift
    var carry = 0UL
    var sourceIndex = 0
    while (sourceIndex < value.size) {
        val limb = value.limbs[sourceIndex]
        result[sourceIndex + digitShift] = ((limb shl bitShift) and CANONICAL_LIMB_MASK) or carry
        carry = limb shr carryShift
        sourceIndex++
    }
    return if (carry != 0UL) {
        result[value.size + digitShift] = carry
        BigInteger(sign, value.size + digitShift + 1, result)
    } else {
        BigInteger(sign, value.size + digitShift, result)
    }
}

internal fun shiftRightMagnitude(value: BigInteger, digitShift: Int, bitShift: Int): BigInteger {
    val resultSize = value.size - digitShift
    val result = ULongArray(resultSize)
    if (bitShift == 0) {
        value.limbs.copyInto(result, startIndex = digitShift, endIndex = value.size)
        return BigInteger(1, resultSize, result)
    }

    val lowMask = (1UL shl bitShift) - 1UL
    val carryShift = CANONICAL_LIMB_BITS - bitShift
    var carry = 0UL
    var sourceIndex = value.size - 1
    while (sourceIndex >= digitShift) {
        val limb = value.limbs[sourceIndex]
        result[sourceIndex - digitShift] = (limb shr bitShift) or carry
        carry = (limb and lowMask) shl carryShift
        sourceIndex--
    }
    val normalizedSize = if (result[resultSize - 1] != 0UL) resultSize else resultSize - 1
    return if (normalizedSize == 0) ZERO else BigInteger(1, normalizedSize, result)
}

internal fun hasDiscardedShiftBits(value: BigInteger, digitShift: Int, bitShift: Int): Boolean {
    var index = 0
    while (index < digitShift) {
        if (value.limbs[index] != 0UL) return true
        index++
    }
    if (bitShift == 0) return false
    val lowMask = (1UL shl bitShift) - 1UL
    return (value.limbs[digitShift] and lowMask) != 0UL
}