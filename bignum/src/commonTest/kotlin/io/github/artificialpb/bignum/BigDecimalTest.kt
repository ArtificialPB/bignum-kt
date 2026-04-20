package io.github.artificialpb.bignum

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private fun bd(value: String) = BigDecimal(value)
private fun bi(value: String) = BigInteger(value)
private fun mc(value: String) = MathContext(value)

data class BigDecimalStringCase(val input: String, val expected: String) {
    override fun toString(): String = "\"$input\" -> \"$expected\""
}

data class BigDecimalFromBigIntCase(val unscaled: String, val scale: Int, val expected: String) {
    override fun toString(): String = "[$unscaled, $scale] -> $expected"
}

data class BigDecimalBinaryCase(val left: String, val right: String, val expected: String) {
    override fun toString(): String = "$left op $right = $expected"
}

data class BigDecimalPowCase(val input: String, val exponent: Int, val expected: String) {
    override fun toString(): String = "$input^$exponent = $expected"
}

data class BigDecimalIntCase(val input: String, val expected: Int) {
    override fun toString(): String = "$input -> $expected"
}

data class BigDecimalLongCase(val input: String, val expected: Long) {
    override fun toString(): String = "$input -> $expected"
}

data class BigDecimalInvalidInputCase(val input: String) {
    override fun toString(): String = "\"$input\""
}

class BigDecimalConstructionTest : FunSpec({
    context("fromString round-trips") {
        withData(
            BigDecimalStringCase("0", "0"),
            BigDecimalStringCase("-0", "0"),
            BigDecimalStringCase("1", "1"),
            BigDecimalStringCase("-1", "-1"),
            BigDecimalStringCase("1.20", "1.20"),
            BigDecimalStringCase("-1.20", "-1.20"),
            BigDecimalStringCase("12345678901234567890.1234", "12345678901234567890.1234"),
            BigDecimalStringCase("1E+3", "1E+3"),
            BigDecimalStringCase("1E-3", "0.001"),
            BigDecimalStringCase("-4.321E+7", "-4.321E+7"),
            BigDecimalStringCase("1000E-2", "10.00"),
        ) { (input, expected) ->
            BigDecimal(input).toString() shouldBe expected
        }
    }

    context("fromBigInteger constructors") {
        withData(
            BigDecimalFromBigIntCase("0", 0, "0"),
            BigDecimalFromBigIntCase("12345", 0, "12345"),
            BigDecimalFromBigIntCase("12345", 2, "123.45"),
            BigDecimalFromBigIntCase("-12345", 2, "-123.45"),
            BigDecimalFromBigIntCase("12345", -2, "1.2345E+6"),
            BigDecimalFromBigIntCase("0", -2, "0E+2"),
        ) { (unscaled, scale, expected) ->
            BigDecimal(bi(unscaled), scale).toString() shouldBe expected
        }
    }

    test("primitive constructors") {
        BigDecimal(42).toString() shouldBe "42"
        BigDecimal(-42).toString() shouldBe "-42"
        BigDecimal(42L).toString() shouldBe "42"
        BigDecimal(Long.MIN_VALUE).toString() shouldBe Long.MIN_VALUE.toString()
    }

    context("factories") {
        test("string factory") {
            bigDecimalOf("1.25").toString() shouldBe "1.25"
        }

        test("big integer factories") {
            bigDecimalOf(bi("12345")).toString() shouldBe "12345"
            bigDecimalOf(bi("12345"), 3).toString() shouldBe "12.345"
        }

        test("primitive factories") {
            bigDecimalOf(42).toString() shouldBe "42"
            bigDecimalOf(42L).toString() shouldBe "42"
        }
    }

    context("invalid inputs") {
        withData(
            BigDecimalInvalidInputCase(""),
            BigDecimalInvalidInputCase("+"),
            BigDecimalInvalidInputCase("-"),
            BigDecimalInvalidInputCase("."),
            BigDecimalInvalidInputCase("1_"),
            BigDecimalInvalidInputCase("0x10"),
            BigDecimalInvalidInputCase("NaN"),
            BigDecimalInvalidInputCase("Infinity"),
            BigDecimalInvalidInputCase(" 1"),
            BigDecimalInvalidInputCase("1 "),
        ) { (input) ->
            shouldThrow<NumberFormatException> {
                BigDecimal(input)
            }
        }
    }

    test("lowercase e in scientific notation") {
        BigDecimal("1.5e2").toString() shouldBe "1.5E+2"
        BigDecimal("1e3").toString() shouldBe "1E+3"
        BigDecimal("-4.321e+7").toString() shouldBe "-4.321E+7"
        BigDecimal("1e-3").toString() shouldBe "0.001"
    }

    test("explicit positive sign parses correctly") {
        BigDecimal("+123.45").toString() shouldBe "123.45"
        BigDecimal("+1E+3").toString() shouldBe "1E+3"
    }
})

class MathContextConstructionTest : FunSpec({
    test("constructors and string parsing mirror JDK shape") {
        MathContext(3).toString() shouldBe "precision=3 roundingMode=HALF_UP"
        MathContext(7, RoundingMode.HALF_EVEN).toString() shouldBe "precision=7 roundingMode=HALF_EVEN"
        mc("precision=16 roundingMode=DOWN").precision shouldBeExactly 16
        mc("precision=16 roundingMode=DOWN").roundingMode shouldBe RoundingMode.DOWN
        mc("precision=16 roundingMode=DOWN") shouldBe MathContext(16, RoundingMode.DOWN)
    }

    test("invalid MathContext inputs throw") {
        shouldThrow<IllegalArgumentException> {
            MathContext(-1)
        }
        shouldThrow<IllegalArgumentException> {
            mc("precision=x roundingMode=HALF_UP")
        }
        shouldThrow<IllegalArgumentException> {
            mc("precision=1 mode=HALF_UP")
        }
        shouldThrow<IllegalArgumentException> {
            mc("precision=-1 roundingMode=HALF_UP")
        }
        shouldThrow<IllegalArgumentException> {
            mc("precision=1 roundingMode=NOT_A_MODE")
        }
    }

    test("MathContext equals and hashCode contract") {
        val a = MathContext(5, RoundingMode.HALF_UP)
        val b = MathContext(5, RoundingMode.HALF_UP)
        val c = MathContext(5, RoundingMode.DOWN)
        val d = MathContext(6, RoundingMode.HALF_UP)

        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
        a shouldNotBe c
        a shouldNotBe d
        a.equals(null) shouldBe false
        a.equals("not a MathContext") shouldBe false
    }
})

class BigDecimalArithmeticTest : FunSpec({
    context("addition") {
        withData(
            BigDecimalBinaryCase("0", "0", "0"),
            BigDecimalBinaryCase("1.20", "3.4", "4.60"),
            BigDecimalBinaryCase("-1.20", "3.4", "2.20"),
            BigDecimalBinaryCase("999999999999.999", "0.001", "1000000000000.000"),
        ) { (left, right, expected) ->
            (bd(left) + bd(right)).toString() shouldBe expected
        }
    }

    context("subtraction") {
        withData(
            BigDecimalBinaryCase("1.20", "0.2", "1.00"),
            BigDecimalBinaryCase("0", "1.5", "-1.5"),
            BigDecimalBinaryCase("-1.2", "-3.40", "2.20"),
        ) { (left, right, expected) ->
            (bd(left) - bd(right)).toString() shouldBe expected
        }
    }

    context("multiplication") {
        withData(
            BigDecimalBinaryCase("1.20", "3.4", "4.080"),
            BigDecimalBinaryCase("-1.20", "3.4", "-4.080"),
            BigDecimalBinaryCase("0.125", "8", "1.000"),
        ) { (left, right, expected) ->
            (bd(left) * bd(right)).toString() shouldBe expected
        }

        test("multiply with MathContext rounds to the requested precision") {
            bd("1.20").multiply(bd("3.4"), MathContext(2, RoundingMode.HALF_UP)).toString() shouldBe "4.1"
        }
    }

    context("division") {
        withData(
            BigDecimalBinaryCase("1.00", "2", "0.50"),
            BigDecimalBinaryCase("19", "100", "0.19"),
            BigDecimalBinaryCase("0", "2", "0"),
        ) { (left, right, expected) ->
            (bd(left) / bd(right)).toString() shouldBe expected
        }

        test("non-terminating divide throws") {
            shouldThrow<ArithmeticException> {
                bd("21") / bd("110")
            }
        }

        test("divide overloads with rounding mode and MathContext") {
            bd("21").divide(bd("110"), RoundingMode.HALF_UP).toString() shouldBe "0"
            bd("21").divide(bd("110"), 3, RoundingMode.HALF_UP).toString() shouldBe "0.191"
            bd("21").divide(bd("110"), MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "0.191"
        }
    }

    context("remainder and divideAndRemainder") {
        test("remainder") {
            (bd("10.5") % bd("1.0")).toString() shouldBe "0.5"
        }

        test("divideAndRemainder") {
            val result = bd("1").divideAndRemainder(bd("0.01"))
            result[0].toString() shouldBe "1E+2"
            result[1].toString() shouldBe "0"
        }

        test("MathContext overloads share quotient precision checks") {
            bd("10").remainder(bd("3"), MathContext(1, RoundingMode.DOWN)).toString() shouldBe "1"
            bd("10").divideAndRemainder(bd("3"), MathContext(1, RoundingMode.DOWN)).map { it.toString() } shouldBe listOf("3", "1")
            bd("10").divideToIntegralValue(bd("3"), MathContext(1, RoundingMode.DOWN)).toString() shouldBe "3"
        }

        test("divideToIntegralValue with insufficient precision throws") {
            shouldThrow<ArithmeticException> {
                bd("123").divideToIntegralValue(bd("1"), MathContext(2, RoundingMode.DOWN))
            }
        }
    }

    context("pow/abs/negate/plus") {
        withData(
            BigDecimalPowCase("1.20", 3, "1.728000"),
            BigDecimalPowCase("-2", 3, "-8"),
            BigDecimalPowCase("2", 0, "1"),
        ) { (input, exponent, expected) ->
            bd(input).pow(exponent).toString() shouldBe expected
        }

        test("negative exponent throws") {
            shouldThrow<ArithmeticException> {
                bd("2").pow(-1)
            }
        }

        bd("-1.25").abs().toString() shouldBe "1.25"
        bd("1.25").negate().toString() shouldBe "-1.25"
        (+bd("1.25")).toString() shouldBe "1.25"
        (-bd("1.25")).toString() shouldBe "-1.25"

        test("MathContext overloads round through the unary helpers") {
            bd("1.239").round(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "1.24"
            bd("-1.239").abs(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "1.24"
            bd("1.239").negate(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "-1.24"
            bd("1.239").plus(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "1.24"
            bd("1.01").pow(3, MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "1.03"
        }
    }

    context("square root") {
        test("sqrt matches JDK preferred-scale behavior") {
            bd("4").sqrt(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "2"
            bd("2").sqrt(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "1.41"
            bd("0.00").sqrt(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "0.0"
        }

        test("sqrt throws for negative values and inexact unlimited precision") {
            shouldThrow<ArithmeticException> {
                bd("-1").sqrt(MathContext(3, RoundingMode.HALF_UP))
            }
            shouldThrow<ArithmeticException> {
                bd("2").sqrt(MathContext(0, RoundingMode.HALF_UP))
            }
        }
    }
})

class BigDecimalScaleTest : FunSpec({
    test("signum scale precision and unscaledValue") {
        val value = bd("123.4500")
        value.signum() shouldBeExactly 1
        value.scale() shouldBeExactly 4
        value.precision() shouldBeExactly 7
        value.unscaledValue() shouldBe bi("1234500")
    }

    test("precision for negative powers of two matches the positive magnitude") {
        listOf(
            "-8",
            "-128",
            "-1024",
            "-1048576",
            "-9223372036854775808",
            "-18446744073709551616",
        ).forEach { unscaled ->
            BigDecimal(bi(unscaled), 0).precision() shouldBeExactly BigDecimal(bi(unscaled.drop(1)), 0).precision()
            BigDecimal(bi(unscaled), 7).precision() shouldBeExactly BigDecimal(bi(unscaled.drop(1)), 7).precision()
        }
    }

    test("setScale exact") {
        bd("1.23").setScale(4).toString() shouldBe "1.2300"
        bd("1.2300").setScale(2).toString() shouldBe "1.23"
    }

    test("setScale with rounding") {
        bd("2.5").setScale(0, RoundingMode.HALF_UP).toString() shouldBe "3"
        bd("2.5").setScale(0, RoundingMode.HALF_DOWN).toString() shouldBe "2"
        bd("2.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "2"
        bd("-2.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "-2"
    }

    test("setScale exact throws when rounding is required") {
        shouldThrow<ArithmeticException> {
            bd("1.23").setScale(1)
        }
    }

    test("movePointLeft and movePointRight") {
        bd("123").movePointLeft(5).toString() shouldBe "0.00123"
        bd("1.23").movePointRight(5).toString() shouldBe "123000"
    }

    test("scaleByPowerOfTen") {
        val value = bd("1.23").scaleByPowerOfTen(3)
        value.compareTo(bd("1230")) shouldBeExactly 0
        value.scale() shouldBeExactly -1
    }

    test("stripTrailingZeros") {
        bd("1.2300").stripTrailingZeros().toString() shouldBe "1.23"
        bd("0.00").stripTrailingZeros().toString() shouldBe "0"
    }

    test("ulp") {
        bd("1.23").ulp().toString() shouldBe "0.01"
        BigDecimal(bi("1"), -2).ulp().toString() shouldBe "1E+2"
    }
})

class BigDecimalConversionTest : FunSpec({
    test("string conversions") {
        val negativeScaleZero = BigDecimal(bi("0"), -2)
        negativeScaleZero.toString() shouldBe "0E+2"
        negativeScaleZero.toPlainString() shouldBe "0"
        negativeScaleZero.toEngineeringString() shouldBe "0.0E+3"
    }

    test("integer conversions") {
        bd("123.9").toBigInteger() shouldBe bi("123")
        bd("-123.9").toBigInteger() shouldBe bi("-123")
        bd("123").toBigIntegerExact() shouldBe bi("123")
    }

    test("exact integer conversions throw on fractional values") {
        shouldThrow<ArithmeticException> { bd("123.1").toBigIntegerExact() }
        shouldThrow<ArithmeticException> { bd("123.1").toIntExact() }
        shouldThrow<ArithmeticException> { bd("123.1").toLongExact() }
    }

    context("primitive conversions") {
        withData(
            BigDecimalIntCase("123.9", 123),
            BigDecimalIntCase("-123.9", -123),
        ) { (input, expected) ->
            bd(input).toInt() shouldBeExactly expected
        }

        withData(
            BigDecimalLongCase("1234567890123.9", 1234567890123L),
            BigDecimalLongCase("-1234567890123.9", -1234567890123L),
        ) { (input, expected) ->
            bd(input).toLong() shouldBeExactly expected
        }

        bd("1.5").toFloat() shouldBeExactly 1.5f
        bd("1.5").toDouble() shouldBe 1.5
    }
})

class BigDecimalComparisonTest : FunSpec({
    test("compareTo is cohort-aware but equals is representation-aware") {
        val a = bd("2.0")
        val b = bd("2.00")
        a.compareTo(b) shouldBeExactly 0
        a.equals(b) shouldBe false
    }

    test("compareTo ordering") {
        bd("1.9") shouldBeLessThan bd("2.0")
        bd("2.1") shouldBeGreaterThan bd("2.0")
    }

    test("min and max") {
        bd("2.0").min(bd("2.00")).toString() shouldBe "2.0"
        bd("2.0").max(bd("2.00")).toString() shouldBe "2.0"
    }

    test("hashCode is representation-sensitive") {
        bd("2.0").hashCode() shouldBe bd("2.0").hashCode()
        (bd("2.0").hashCode() == bd("2.00").hashCode()) shouldBe false
    }
})
