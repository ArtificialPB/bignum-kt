package io.github.artificialpb.bignum

/** Schoolbook multiply when total result limbs (left.size + right.size) is at or below this. */
internal const val SCHOOLBOOK_MUL_THRESHOLD = 14

/** Use Kotlin division (Algorithm D) when both operands have at most this many 60-bit limbs. */
internal const val SCHOOLBOOK_DIV_THRESHOLD = 7

private const val HALF_LIMB_BITS = 30
private const val HALF_LIMB_MASK = 0x3FFFFFFFUL

internal fun addAbsolute(sign: Int, left: BigInteger, right: BigInteger): BigInteger {
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

internal fun subtractAbsolute(sign: Int, larger: BigInteger, smaller: BigInteger): BigInteger {
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

internal fun addMagnitudeOne(sign: Int, value: BigInteger): BigInteger {
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

internal fun subtractMagnitudeOne(sign: Int, value: BigInteger): BigInteger {
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

private fun BigInteger.digit(index: Int): ULong {
    return if (index in 0 until size) limbs[index] else 0UL
}

/**
 * Divides two magnitudes in pure Kotlin using Knuth's Algorithm D at base 2^30.
 * Returns (quotient, remainder) as positive-magnitude BigIntegers.
 * Caller applies signs.
 */
internal fun divRemMagnitude(dividend: BigInteger, divisor: BigInteger): Pair<BigInteger, BigInteger> {
    val cmp = compareMagnitudes(dividend, divisor)
    if (cmp < 0) return Pair(ZERO, BigInteger(1, dividend.size, dividend.limbs))
    if (cmp == 0) return Pair(ONE, ZERO)

    if (divisor.size == 1 && dividend.size == 1) {
        val a = dividend.limbs[0]
        val d = divisor.limbs[0]
        val q = a / d
        val r = a % d
        return Pair(
            if (q == 0UL) ZERO else BigInteger(1, 1, ulongArrayOf(q)),
            if (r == 0UL) ZERO else BigInteger(1, 1, ulongArrayOf(r)),
        )
    }

    // Convert to base 2^30 half-limbs and use long division
    return divRemHalfLimbs(dividend, divisor)
}

/**
 * Long division at base 2^30 using Knuth's Algorithm D (TAOCP 4.3.1).
 * All arrays are MSB-first (index 0 is the most significant digit).
 */
private fun divRemHalfLimbs(dividend: BigInteger, divisor: BigInteger): Pair<BigInteger, BigInteger> {
    val B: ULong = 1UL shl HALF_LIMB_BITS
    val MASK: ULong = B - 1UL

    val uOrig = toHalfLimbs(dividend) // MSB first, leading zeros stripped
    val vOrig = toHalfLimbs(divisor)  // MSB first, leading zeros stripped
    val n = vOrig.size
    val m = uOrig.size - n

    if (n == 1) {
        // Single-digit divisor: simple long division
        val d = vOrig[0]
        var rem = 0UL
        val q = ULongArray(uOrig.size)
        for (i in uOrig.indices) {
            val cur = rem * B + uOrig[i]
            q[i] = cur / d
            rem = cur % d
        }
        val remBigInt = if (rem == 0UL) ZERO else BigInteger(1, 1, ulongArrayOf(rem))
        return Pair(halfLimbsToBigInteger(q), remBigInt)
    }

    // Step D1: Normalize — left-shift so vOrig[0] has its high bit set (bit 29)
    val s = countLeadingZeroBits30(vOrig[0])

    val v = ULongArray(n)
    val u = ULongArray(uOrig.size + 1) // one extra leading digit

    if (s > 0) {
        // Left-shift divisor
        for (i in 0 until n - 1) {
            v[i] = ((vOrig[i] shl s) or (vOrig[i + 1] shr (HALF_LIMB_BITS - s))) and MASK
        }
        v[n - 1] = (vOrig[n - 1] shl s) and MASK

        // Left-shift dividend
        u[0] = uOrig[0] shr (HALF_LIMB_BITS - s)
        for (i in 0 until uOrig.size - 1) {
            u[i + 1] = ((uOrig[i] shl s) or (uOrig[i + 1] shr (HALF_LIMB_BITS - s))) and MASK
        }
        u[uOrig.size] = (uOrig[uOrig.size - 1] shl s) and MASK
    } else {
        vOrig.copyInto(v)
        u[0] = 0UL
        uOrig.copyInto(u, 1)
    }

    val q = ULongArray(m + 1)

    // Steps D2–D7: main loop — compute one quotient digit per iteration
    for (j in 0..m) {
        // D3: estimate qhat from top two dividend digits / top divisor digit
        val twoDigit = u[j] * B + u[j + 1]
        var qhat = twoDigit / v[0]
        var rhat = twoDigit % v[0]

        // Refine: decrease qhat while it would produce too large a product
        while (qhat >= B || qhat * v[1] > B * rhat + u[j + 2]) {
            qhat--
            rhat += v[0]
            if (rhat >= B) break
        }

        // D4: multiply v by qhat and subtract from u[j..j+n]
        // Combine product + incoming borrow before subtracting to avoid double-borrow underflow
        var borrow = 0UL
        for (i in n - 1 downTo 0) {
            val t = qhat * v[i] + borrow // product + carry, fits in ULong (< B^2)
            val sub = u[j + 1 + i].toLong() - (t and MASK).toLong()
            borrow = t shr HALF_LIMB_BITS
            if (sub < 0) {
                u[j + 1 + i] = (sub + B.toLong()).toULong()
                borrow++
            } else {
                u[j + 1 + i] = sub.toULong()
            }
        }
        val topSub = u[j].toLong() - borrow.toLong()
        u[j] = if (topSub < 0) (topSub + B.toLong()).toULong() else topSub.toULong()

        q[j] = qhat

        // D6: add back if we subtracted too much
        if (topSub < 0) {
            q[j]--
            var carry = 0UL
            for (i in n - 1 downTo 0) {
                val sum = u[j + 1 + i] + v[i] + carry
                u[j + 1 + i] = sum and MASK
                carry = sum shr HALF_LIMB_BITS
            }
            u[j] = (u[j] + carry) and MASK
        }
    }

    // D8: un-normalize remainder — right-shift u[m+1..m+n] by s bits
    val rem = ULongArray(n)
    if (s > 0) {
        // Right-shift: bits flow from MSB (lower index) to LSB (higher index)
        rem[0] = u[m + 1] shr s
        for (i in 1 until n) {
            rem[i] = ((u[m + i] shl (HALF_LIMB_BITS - s)) or (u[m + 1 + i] shr s)) and MASK
        }
    } else {
        for (i in 0 until n) {
            rem[i] = u[m + 1 + i]
        }
    }

    return Pair(halfLimbsToBigInteger(q), halfLimbsToBigInteger(rem))
}

/** Convert BigInteger magnitude to MSB-first array of 30-bit half-limbs, stripping leading zeros. */
private fun toHalfLimbs(value: BigInteger): ULongArray {
    val raw = ULongArray(value.size * 2)
    for (i in 0 until value.size) {
        raw[raw.size - 1 - 2 * i] = value.limbs[i] and HALF_LIMB_MASK
        raw[raw.size - 2 - 2 * i] = value.limbs[i] shr HALF_LIMB_BITS
    }
    // Strip leading zeros
    var start = 0
    while (start < raw.size - 1 && raw[start] == 0UL) start++
    return if (start == 0) raw else raw.copyOfRange(start, raw.size)
}

/** Convert MSB-first 30-bit half-limb array to BigInteger (positive magnitude). */
private fun halfLimbsToBigInteger(halfLimbs: ULongArray): BigInteger {
    // Strip leading zeros
    var start = 0
    while (start < halfLimbs.size && halfLimbs[start] == 0UL) start++
    if (start == halfLimbs.size) return ZERO

    val significantCount = halfLimbs.size - start
    val limbCount = (significantCount + 1) / 2
    val limbs = ULongArray(limbCount)

    // Fill from LSB
    var halfIdx = halfLimbs.size - 1
    for (i in 0 until limbCount) {
        val low = halfLimbs[halfIdx--]
        val high = if (halfIdx >= start) halfLimbs[halfIdx--] else 0UL
        limbs[i] = low or (high shl HALF_LIMB_BITS)
    }

    val normalizedSize = normalizeMagnitudeSize(limbCount, limbs)
    return if (normalizedSize == 0) ZERO else BigInteger(1, normalizedSize, limbs)
}

/** Count leading zero bits within a 30-bit value. */
private fun countLeadingZeroBits30(value: ULong): Int {
    if (value == 0UL) return HALF_LIMB_BITS
    var count = 0
    var bit = HALF_LIMB_BITS - 1 // bit 29
    while ((value and (1UL shl bit)) == 0UL) {
        count++
        bit--
    }
    return count
}

internal fun divideSmall(resultSign: Int, dividend: BigInteger, divisor: BigInteger): BigInteger {
    val (q, _) = divRemMagnitude(dividend, divisor)
    return if (q.sign == 0) ZERO else BigInteger(resultSign, q.size, q.limbs)
}

internal fun remainderSmall(dividendSign: Int, dividend: BigInteger, divisor: BigInteger): BigInteger {
    val (_, r) = divRemMagnitude(dividend, divisor)
    return if (r.sign == 0) ZERO else BigInteger(dividendSign, r.size, r.limbs)
}

internal fun multiplySmall(sign: Int, left: BigInteger, right: BigInteger): BigInteger {
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
