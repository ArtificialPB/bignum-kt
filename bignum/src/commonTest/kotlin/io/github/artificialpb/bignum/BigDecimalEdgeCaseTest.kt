package io.github.artificialpb.bignum

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BigDecimalRepresentationEdgeCaseTest : FunSpec({
    test("zero with negative scale keeps JVM string semantics") {
        val value = BigDecimal(bigIntegerOf(0), -2)
        value.toString() shouldBe "0E+2"
        value.toPlainString() shouldBe "0"
        value.toEngineeringString() shouldBe "0.0E+3"
    }

    test("compareTo and equals diverge across a cohort") {
        val a = BigDecimal("2.0")
        val b = BigDecimal("2.00")
        a.compareTo(b) shouldBeExactly 0
        a.equals(b) shouldBe false
        (a.hashCode() == b.hashCode()) shouldBe false
    }

    test("stripTrailingZeros on zero normalizes to scale zero") {
        val stripped = BigDecimal("0.000").stripTrailingZeros()
        stripped.toString() shouldBe "0"
        stripped.scale() shouldBeExactly 0
    }
})

class BigDecimalDivisionEdgeCaseTest : FunSpec({
    test("divideAndRemainder keeps JVM preferred quotient scale for exact powers of ten") {
        val result = BigDecimal("100").divideAndRemainder(BigDecimal("0.01"))
        result[0].toString() shouldBe "1.00E+4"
        result[1].toString() shouldBe "0"
    }

    test("divide by zero throws") {
        shouldThrow<ArithmeticException> {
            BigDecimal("1").divide(BigDecimal("0"))
        }
    }

    test("remainder by zero throws") {
        shouldThrow<ArithmeticException> {
            BigDecimal("1").remainder(BigDecimal("0"))
        }
    }

    test("non-terminating exact divide throws") {
        shouldThrow<ArithmeticException> {
            BigDecimal("1").divide(BigDecimal("3"))
        }
    }

    test("divideToIntegralValue with MathContext fails when quotient precision is too large") {
        shouldThrow<ArithmeticException> {
            BigDecimal("999").divideToIntegralValue(BigDecimal("1"), MathContext(2, RoundingMode.DOWN))
        }
    }
})

class BigDecimalRoundingEdgeCaseTest : FunSpec({
    test("half rounding modes match JVM tie behavior") {
        BigDecimal("5.5").setScale(0, RoundingMode.HALF_UP).toString() shouldBe "6"
        BigDecimal("5.5").setScale(0, RoundingMode.HALF_DOWN).toString() shouldBe "5"
        BigDecimal("5.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "6"

        BigDecimal("2.5").setScale(0, RoundingMode.HALF_UP).toString() shouldBe "3"
        BigDecimal("2.5").setScale(0, RoundingMode.HALF_DOWN).toString() shouldBe "2"
        BigDecimal("2.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "2"

        BigDecimal("-2.5").setScale(0, RoundingMode.HALF_UP).toString() shouldBe "-3"
        BigDecimal("-2.5").setScale(0, RoundingMode.HALF_DOWN).toString() shouldBe "-2"
        BigDecimal("-2.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "-2"
    }

    test("UP rounds away from zero regardless of sign") {
        BigDecimal("2.1").setScale(0, RoundingMode.UP).toString() shouldBe "3"
        BigDecimal("2.9").setScale(0, RoundingMode.UP).toString() shouldBe "3"
        BigDecimal("-2.1").setScale(0, RoundingMode.UP).toString() shouldBe "-3"
        BigDecimal("-2.9").setScale(0, RoundingMode.UP).toString() shouldBe "-3"
        BigDecimal("2.0").setScale(0, RoundingMode.UP).toString() shouldBe "2"
    }

    test("CEILING rounds toward positive infinity") {
        BigDecimal("2.1").setScale(0, RoundingMode.CEILING).toString() shouldBe "3"
        BigDecimal("2.9").setScale(0, RoundingMode.CEILING).toString() shouldBe "3"
        BigDecimal("-2.1").setScale(0, RoundingMode.CEILING).toString() shouldBe "-2"
        BigDecimal("-2.9").setScale(0, RoundingMode.CEILING).toString() shouldBe "-2"
    }

    test("FLOOR rounds toward negative infinity") {
        BigDecimal("2.1").setScale(0, RoundingMode.FLOOR).toString() shouldBe "2"
        BigDecimal("2.9").setScale(0, RoundingMode.FLOOR).toString() shouldBe "2"
        BigDecimal("-2.1").setScale(0, RoundingMode.FLOOR).toString() shouldBe "-3"
        BigDecimal("-2.9").setScale(0, RoundingMode.FLOOR).toString() shouldBe "-3"
    }

    test("UNNECESSARY throws when rounding is required") {
        shouldThrow<ArithmeticException> {
            BigDecimal("1.1").setScale(0, RoundingMode.UNNECESSARY)
        }
    }

    test("HALF_EVEN ties round to even quotient") {
        // Even quotient stays (2 is even)
        BigDecimal("2.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "2"
        BigDecimal("-2.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "-2"
        BigDecimal("4.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "4"
        BigDecimal("-4.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "-4"

        // Odd quotient rounds (3 is odd → 4)
        BigDecimal("3.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "4"
        BigDecimal("-3.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "-4"
        BigDecimal("1.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "2"
        BigDecimal("-1.5").setScale(0, RoundingMode.HALF_EVEN).toString() shouldBe "-2"
    }

    test("rounding tie behavior through divide path") {
        // 5 / 2 with scale 0 = 2.5, tie at exact half
        BigDecimal("5").divide(BigDecimal("2"), 0, RoundingMode.HALF_UP).toString() shouldBe "3"
        BigDecimal("5").divide(BigDecimal("2"), 0, RoundingMode.HALF_DOWN).toString() shouldBe "2"
        BigDecimal("5").divide(BigDecimal("2"), 0, RoundingMode.HALF_EVEN).toString() shouldBe "2"

        // 7 / 2 with scale 0 = 3.5, tie at exact half (odd quotient for HALF_EVEN)
        BigDecimal("7").divide(BigDecimal("2"), 0, RoundingMode.HALF_EVEN).toString() shouldBe "4"

        // Non-tie: all HALF modes agree
        BigDecimal("7").divide(BigDecimal("3"), 0, RoundingMode.HALF_UP).toString() shouldBe "2"
        BigDecimal("7").divide(BigDecimal("3"), 0, RoundingMode.HALF_DOWN).toString() shouldBe "2"
        BigDecimal("7").divide(BigDecimal("3"), 0, RoundingMode.HALF_EVEN).toString() shouldBe "2"
    }

    test("all rounding modes through divide with scale") {
        // 10 / 3 = 3.333... at scale 0
        BigDecimal("10").divide(BigDecimal("3"), 0, RoundingMode.UP).toString() shouldBe "4"
        BigDecimal("10").divide(BigDecimal("3"), 0, RoundingMode.DOWN).toString() shouldBe "3"
        BigDecimal("10").divide(BigDecimal("3"), 0, RoundingMode.CEILING).toString() shouldBe "4"
        BigDecimal("10").divide(BigDecimal("3"), 0, RoundingMode.FLOOR).toString() shouldBe "3"

        // -10 / 3 = -3.333... at scale 0
        BigDecimal("-10").divide(BigDecimal("3"), 0, RoundingMode.UP).toString() shouldBe "-4"
        BigDecimal("-10").divide(BigDecimal("3"), 0, RoundingMode.DOWN).toString() shouldBe "-3"
        BigDecimal("-10").divide(BigDecimal("3"), 0, RoundingMode.CEILING).toString() shouldBe "-3"
        BigDecimal("-10").divide(BigDecimal("3"), 0, RoundingMode.FLOOR).toString() shouldBe "-4"
    }
})

class BigDecimalConversionEdgeCaseTest : FunSpec({
    test("toBigInteger truncates toward zero") {
        BigDecimal("1.9").toBigInteger() shouldBe bigIntegerOf(1)
        BigDecimal("-1.9").toBigInteger() shouldBe bigIntegerOf(-1)
    }

    test("exact primitive conversions throw on overflow") {
        shouldThrow<ArithmeticException> {
            BigDecimal("2147483648").toIntExact()
        }
        shouldThrow<ArithmeticException> {
            BigDecimal("9223372036854775808").toLongExact()
        }
    }

    test("movePointRight clamps positive scale to zero") {
        val value = BigDecimal("1.23").movePointRight(5)
        value.toString() shouldBe "123000"
        value.scale() shouldBeExactly 0
    }

    test("movePointLeft and movePointRight preserve negative scale for zero shift") {
        val scientific = BigDecimal("1E+3")
        val left = scientific.movePointLeft(0)
        val right = scientific.movePointRight(0)

        left.toString() shouldBe "1E+3"
        left.scale() shouldBeExactly -3
        right.toString() shouldBe "1E+3"
        right.scale() shouldBeExactly -3
    }

    test("movePointLeft grows scale") {
        val value = BigDecimal("123").movePointLeft(5)
        value.toString() shouldBe "0.00123"
        value.scale() shouldBeExactly 5
    }

    test("sqrt preserves preferred zero scale for zero cohorts") {
        val value = BigDecimal(bigIntegerOf(0), -2).sqrt(MathContext(3, RoundingMode.HALF_UP))
        value.toString() shouldBe "0E+1"
        value.scale() shouldBeExactly -1
    }
})

class BigDecimalStringConversionEdgeCaseTest : FunSpec({
    test("toString uses plain format at adjustedExponent boundary") {
        // adjustedExponent == -6 → plain string
        BigDecimal(BigInteger("1"), 6).toString() shouldBe "0.000001"
        // adjustedExponent == -7 → scientific notation
        BigDecimal(BigInteger("1"), 7).toString() shouldBe "1E-7"
    }

    test("toEngineeringString aligns exponent to multiple of 3") {
        BigDecimal("3E+1").toEngineeringString() shouldBe "30"
        BigDecimal("3E+2").toEngineeringString() shouldBe "300"
        BigDecimal("1E+6").toEngineeringString() shouldBe "1E+6"
        BigDecimal("1E+7").toEngineeringString() shouldBe "10E+6"
        BigDecimal("1E+8").toEngineeringString() shouldBe "100E+6"
        BigDecimal("1E+9").toEngineeringString() shouldBe "1E+9"
        BigDecimal("1E+10").toEngineeringString() shouldBe "10E+9"
        BigDecimal("1E+11").toEngineeringString() shouldBe "100E+9"
    }

    test("toEngineeringString with negative exponents") {
        // Small negative exponents use plain format (adjustedExponent >= -6)
        BigDecimal("1E-1").toEngineeringString() shouldBe "0.1"
        BigDecimal("1E-2").toEngineeringString() shouldBe "0.01"
        BigDecimal("1E-3").toEngineeringString() shouldBe "0.001"
        BigDecimal("1E-4").toEngineeringString() shouldBe "0.0001"
        // Large negative exponents use engineering notation
        BigDecimal("1E-7").toEngineeringString() shouldBe "100E-9"
        BigDecimal("1E-8").toEngineeringString() shouldBe "10E-9"
        BigDecimal("1E-9").toEngineeringString() shouldBe "1E-9"
    }

    test("zero with various negative scales") {
        BigDecimal(BigInteger("0"), -3).let {
            it.toString() shouldBe "0E+3"
            it.toPlainString() shouldBe "0"
            it.toEngineeringString() shouldBe "0E+3"
        }
        BigDecimal(BigInteger("0"), -4).let {
            it.toString() shouldBe "0E+4"
            it.toPlainString() shouldBe "0"
            it.toEngineeringString() shouldBe "0.00E+6"
        }
        BigDecimal(BigInteger("0"), -5).let {
            it.toString() shouldBe "0E+5"
            it.toPlainString() shouldBe "0"
            it.toEngineeringString() shouldBe "0.0E+6"
        }
    }

    test("toPlainString with large negative scale") {
        BigDecimal(BigInteger("1"), -10).toPlainString() shouldBe "10000000000"
    }

    test("zero with positive scale in all formats") {
        BigDecimal(BigInteger("0"), 5).let {
            it.toString() shouldBe "0.00000"
            it.toPlainString() shouldBe "0.00000"
            it.toEngineeringString() shouldBe "0.00000"
        }
    }
})

class BigDecimalScaleOverflowEdgeCaseTest : FunSpec({
    test("pow with scale overflow throws") {
        shouldThrow<ArithmeticException> {
            BigDecimal(BigInteger("1"), Int.MAX_VALUE / 2).pow(3)
        }
    }

    test("setScale to Int.MIN_VALUE throws") {
        shouldThrow<ArithmeticException> {
            BigDecimal("1.0").setScale(Int.MIN_VALUE)
        }
    }

    test("movePointLeft overflow throws") {
        shouldThrow<ArithmeticException> {
            BigDecimal(BigInteger("1"), Int.MAX_VALUE).movePointLeft(1)
        }
    }

    test("movePointRight overflow throws") {
        shouldThrow<ArithmeticException> {
            BigDecimal(BigInteger("1"), Int.MAX_VALUE).movePointRight(-1)
        }
    }

    test("scaleByPowerOfTen on zero with extreme values does not throw") {
        // Zero uses saturating arithmetic, should not overflow
        val result = BigDecimal(BigInteger("0"), 0).scaleByPowerOfTen(Int.MIN_VALUE)
        result.signum() shouldBeExactly 0
    }
})
