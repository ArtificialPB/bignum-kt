package io.github.artificialpb.bignum

actual typealias BigDecimal = java.math.BigDecimal
actual typealias MathContext = java.math.MathContext
actual typealias RoundingMode = java.math.RoundingMode

private val BIG_DECIMAL_ZERO = java.math.BigDecimal.ZERO
private val BIG_DECIMAL_ONE = java.math.BigDecimal.ONE
private val BIG_DECIMAL_TEN = java.math.BigDecimal.TEN

actual fun bigDecimalOf(value: String): BigDecimal = when (value) {
    "0" -> BIG_DECIMAL_ZERO
    "1" -> BIG_DECIMAL_ONE
    "10" -> BIG_DECIMAL_TEN
    else -> java.math.BigDecimal(value)
}

actual fun bigDecimalOf(value: BigInteger): BigDecimal = when (value) {
    bigIntegerOf(0) -> BIG_DECIMAL_ZERO
    bigIntegerOf(1) -> BIG_DECIMAL_ONE
    bigIntegerOf(10) -> BIG_DECIMAL_TEN
    else -> java.math.BigDecimal(value)
}

actual fun bigDecimalOf(value: BigInteger, scale: Int): BigDecimal =
    java.math.BigDecimal(value, scale)

actual fun bigDecimalOf(value: Long): BigDecimal = when (value) {
    0L -> BIG_DECIMAL_ZERO
    1L -> BIG_DECIMAL_ONE
    10L -> BIG_DECIMAL_TEN
    else -> java.math.BigDecimal.valueOf(value)
}

actual fun bigDecimalOf(value: Int): BigDecimal = when (value) {
    0 -> BIG_DECIMAL_ZERO
    1 -> BIG_DECIMAL_ONE
    10 -> BIG_DECIMAL_TEN
    else -> java.math.BigDecimal.valueOf(value.toLong())
}

actual fun BigDecimal.toInt(): Int = (this as Number).toInt()

actual fun BigDecimal.toLong(): Long = (this as Number).toLong()

actual fun BigDecimal.toFloat(): Float = (this as Number).toFloat()

actual fun BigDecimal.toDouble(): Double = (this as Number).toDouble()

actual fun BigDecimal.toIntExact(): Int = this.intValueExact()

actual fun BigDecimal.toLongExact(): Long = this.longValueExact()
