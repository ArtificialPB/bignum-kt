@file:OptIn(ExperimentalObjCRefinement::class)

package io.github.artificialpb.bignum

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

expect enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY,
}

expect class MathContext {
    constructor(precision: Int)
    constructor(precision: Int, roundingMode: RoundingMode)
    constructor(value: String)

    fun getPrecision(): Int
    fun getRoundingMode(): RoundingMode
}

val MathContext.precision: Int
    get() = getPrecision()

val MathContext.roundingMode: RoundingMode
    get() = getRoundingMode()

/**
 * Platform implementations should also implement [Number] for parity with `java.math.BigDecimal`.
 * [Number] is not declared as a common supertype because Kotlin cannot actualize that hierarchy
 * through the JVM `java.math.BigDecimal` typealias.
 */
expect class BigDecimal : Comparable<BigDecimal> {
    constructor(value: String)
    constructor(value: BigInteger)
    constructor(value: BigInteger, scale: Int)
    constructor(value: Int)
    constructor(value: Long)

    fun add(other: BigDecimal): BigDecimal
    fun add(other: BigDecimal, mathContext: MathContext): BigDecimal
    fun subtract(other: BigDecimal): BigDecimal
    fun subtract(other: BigDecimal, mathContext: MathContext): BigDecimal
    fun multiply(other: BigDecimal): BigDecimal
    fun multiply(other: BigDecimal, mathContext: MathContext): BigDecimal
    fun divide(other: BigDecimal): BigDecimal
    fun divide(other: BigDecimal, roundingMode: RoundingMode): BigDecimal
    fun divide(other: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal
    fun divide(other: BigDecimal, mathContext: MathContext): BigDecimal
    fun remainder(other: BigDecimal): BigDecimal
    fun remainder(other: BigDecimal, mathContext: MathContext): BigDecimal
    fun divideAndRemainder(other: BigDecimal): Array<BigDecimal>
    fun divideAndRemainder(other: BigDecimal, mathContext: MathContext): Array<BigDecimal>
    fun divideToIntegralValue(other: BigDecimal): BigDecimal
    fun divideToIntegralValue(other: BigDecimal, mathContext: MathContext): BigDecimal
    fun pow(exponent: Int): BigDecimal
    fun pow(exponent: Int, mathContext: MathContext): BigDecimal
    fun sqrt(mathContext: MathContext): BigDecimal
    fun abs(): BigDecimal
    fun abs(mathContext: MathContext): BigDecimal
    fun negate(): BigDecimal
    fun negate(mathContext: MathContext): BigDecimal
    fun plus(): BigDecimal
    fun plus(mathContext: MathContext): BigDecimal
    fun round(mathContext: MathContext): BigDecimal

    fun signum(): Int
    fun scale(): Int
    fun precision(): Int
    fun unscaledValue(): BigInteger

    fun setScale(newScale: Int): BigDecimal
    fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal
    fun movePointLeft(n: Int): BigDecimal
    fun movePointRight(n: Int): BigDecimal
    fun scaleByPowerOfTen(n: Int): BigDecimal
    fun stripTrailingZeros(): BigDecimal

    fun min(other: BigDecimal): BigDecimal
    fun max(other: BigDecimal): BigDecimal

    fun toEngineeringString(): String
    fun toPlainString(): String
    fun toBigInteger(): BigInteger
    fun toBigIntegerExact(): BigInteger
    fun ulp(): BigDecimal

    override fun compareTo(other: BigDecimal): Int
    override fun toString(): String
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

expect fun bigDecimalOf(value: String): BigDecimal
expect fun bigDecimalOf(value: BigInteger): BigDecimal
expect fun bigDecimalOf(value: BigInteger, scale: Int): BigDecimal
expect fun bigDecimalOf(value: Long): BigDecimal
expect fun bigDecimalOf(value: Int): BigDecimal
expect fun bigDecimalOf(value: Double): BigDecimal

@HiddenFromObjC
fun String.toBigDecimal(): BigDecimal = bigDecimalOf(this)

fun BigInteger.toBigDecimal(scale: Int = 0): BigDecimal = bigDecimalOf(this, scale)

@HiddenFromObjC
fun Long.toBigDecimal(): BigDecimal = bigDecimalOf(this)

@HiddenFromObjC
fun Int.toBigDecimal(): BigDecimal = bigDecimalOf(this)

@HiddenFromObjC
fun Double.toBigDecimal(): BigDecimal = bigDecimalOf(this)

@HiddenFromObjC
operator fun BigDecimal.plus(other: BigDecimal): BigDecimal = add(other)
@HiddenFromObjC
operator fun BigDecimal.minus(other: BigDecimal): BigDecimal = subtract(other)
@HiddenFromObjC
operator fun BigDecimal.times(other: BigDecimal): BigDecimal = multiply(other)
@HiddenFromObjC
operator fun BigDecimal.div(other: BigDecimal): BigDecimal = divide(other)
@HiddenFromObjC
operator fun BigDecimal.rem(other: BigDecimal): BigDecimal = remainder(other)
@HiddenFromObjC
operator fun BigDecimal.unaryMinus(): BigDecimal = negate()
@HiddenFromObjC
operator fun BigDecimal.unaryPlus(): BigDecimal = plus()

expect fun BigDecimal.toInt(): Int
expect fun BigDecimal.toLong(): Long
expect fun BigDecimal.toFloat(): Float
expect fun BigDecimal.toDouble(): Double
expect fun BigDecimal.toIntExact(): Int
expect fun BigDecimal.toLongExact(): Long
