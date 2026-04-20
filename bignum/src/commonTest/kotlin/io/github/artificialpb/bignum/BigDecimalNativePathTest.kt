package io.github.artificialpb.bignum

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe

private fun bd(value: String) = BigDecimal(value)
private fun bi(value: String) = BigInteger(value)

// ── Multiplication path coverage ──────────────────────────────────────────────
// Native multiply() has 4 paths:
//   1. Zero check
//   2. Compact (both magnitudes fit in ULong, < ~2^64)
//   3. Single-limb optimization (one magnitude < 2^60)
//   4. General BigInteger multiplication (both multi-limb)

class BigDecimalMultiplyPathTest : FunSpec({

    test("zero operand returns zero with combined scale") {
        (bd("0.00") * bd("123.4")).toString() shouldBe "0.000"
        (bd("123.4") * bd("0.00")).toString() shouldBe "0.000"
        (bd("0") * bd("0")).toString() shouldBe "0"
    }

    test("compact path - both operands single limb") {
        // 17-digit values fit in a single 60-bit limb
        (bd("10000000000000000") * bd("10000000000000000")).toString() shouldBe "100000000000000000000000000000000"
        (bd("99999999999999999") * bd("2")).toString() shouldBe "199999999999999998"
    }

    test("single-limb optimization - one operand multi-limb other single") {
        // 21-digit unscaled value is multi-limb (>60 bits), 6-digit is single limb
        val multiLimb = "100000000000000000000" // 10^20, ~67 bits
        val singleLimb = "100000"               // 10^5
        (bd(multiLimb) * bd(singleLimb)).toString() shouldBe "10000000000000000000000000"
        // Symmetric: single-limb on left
        (bd(singleLimb) * bd(multiLimb)).toString() shouldBe "10000000000000000000000000"
    }

    test("general BigInteger multiplication - both operands multi-limb") {
        val a = "100000000000000000000" // 10^20
        val b = "200000000000000000000" // 2*10^20
        (bd(a) * bd(b)).toString() shouldBe "20000000000000000000000000000000000000000"
    }

    test("multiply paths preserve scale correctly") {
        // scale(a) + scale(b)
        val a = BigDecimal(bi("10000000000000000000"), 5)   // multi-limb, scale 5
        val b = BigDecimal(bi("200000000000000000000"), 7)  // multi-limb, scale 7
        val result = a * b
        result.scale() shouldBeExactly 12
    }

    test("multiply sign combinations across paths") {
        val pos = "100000000000000000000"
        val neg = "-100000000000000000000"
        // pos * pos
        (bd(pos) * bd("2")).signum() shouldBeExactly 1
        // pos * neg
        (bd(pos) * bd("-2")).signum() shouldBeExactly -1
        // neg * pos
        (bd(neg) * bd("2")).signum() shouldBeExactly -1
        // neg * neg
        (bd(neg) * bd("-2")).signum() shouldBeExactly 1
    }

    test("multiply with MathContext rounds across paths") {
        val mc = MathContext(5, RoundingMode.HALF_UP)
        // General path with rounding
        val a = bd("100000000000000000000")
        val b = bd("200000000000000000000")
        a.multiply(b, mc).toString() shouldBe "2.0000E+40"
    }
})

// ── Division path coverage ────────────────────────────────────────────────────
// Native divide(other) has 5 paths:
//   1. Zero dividend
//   2. fastTerminatingDivide (factor 2s and 5s)
//   3. Digit division (divisor fits in single limb, exact)
//   4. Full GCD reduction
//   5. Non-terminating exception

class BigDecimalDivisionPathTest : FunSpec({

    test("zero dividend returns zero with preferred scale") {
        (bd("0") / bd("5")).toString() shouldBe "0"
        (bd("0.00") / bd("5")).toString() shouldBe "0.00"
        BigDecimal(bi("0"), 3).divide(BigDecimal(bi("1"), 1)).toString() shouldBe "0.00"
    }

    test("fastTerminatingDivide - power of 2 divisor") {
        (bd("1") / bd("8")).toString() shouldBe "0.125"
        (bd("1") / bd("4")).toString() shouldBe "0.25"
        (bd("1") / bd("2")).toString() shouldBe "0.5"
        (bd("1") / bd("16")).toString() shouldBe "0.0625"
        (bd("1") / bd("32")).toString() shouldBe "0.03125"
    }

    test("fastTerminatingDivide - power of 5 divisor") {
        (bd("3") / bd("25")).toString() shouldBe "0.12"
        (bd("1") / bd("5")).toString() shouldBe "0.2"
        (bd("7") / bd("125")).toString() shouldBe "0.056"
    }

    test("fastTerminatingDivide - mixed 2 and 5 factors") {
        (bd("7") / bd("40")).toString() shouldBe "0.175"   // 40 = 2^3 * 5
        (bd("3") / bd("200")).toString() shouldBe "0.015"  // 200 = 2^3 * 5^2
        (bd("1") / bd("50")).toString() shouldBe "0.02"    // 50 = 2 * 5^2
    }

    test("digit division path - prime single-limb divisor divides evenly") {
        (bd("39") / bd("13")).toString() shouldBe "3"
        (bd("77") / bd("7")).toString() shouldBe "11"
        (bd("143") / bd("11")).toString() shouldBe "13"
    }

    test("full GCD path - multi-limb exact division") {
        val big = "100000000000000000000" // 10^20
        (bd(big) / bd(big)).toString() shouldBe "1"
        // Large dividend / large divisor with exact result
        val a = "246813579246813579246" // multi-limb
        val b = "123406789623406789623" // multi-limb
        // Verify on JVM: a / b should be exact or throw
        // Using a simpler case: 2*10^20 / 10^20 = 2
        (bd("200000000000000000000") / bd("100000000000000000000")).toString() shouldBe "2"
    }

    test("non-terminating division throws") {
        shouldThrow<ArithmeticException> { bd("1") / bd("3") }
        shouldThrow<ArithmeticException> { bd("1") / bd("7") }
        shouldThrow<ArithmeticException> { bd("1") / bd("6") } // 6 = 2 * 3
        shouldThrow<ArithmeticException> { bd("2") / bd("3") }
    }

    test("divide by zero throws") {
        shouldThrow<ArithmeticException> { bd("1") / bd("0") }
    }

    test("divide with negative operands") {
        (bd("-1") / bd("8")).toString() shouldBe "-0.125"
        (bd("1") / bd("-8")).toString() shouldBe "-0.125"
        (bd("-1") / bd("-8")).toString() shouldBe "0.125"
    }

    test("divide with rounding all modes") {
        // 1 / 6 = 0.1666... at scale 2
        bd("1").divide(bd("6"), 2, RoundingMode.UP).toString() shouldBe "0.17"
        bd("1").divide(bd("6"), 2, RoundingMode.DOWN).toString() shouldBe "0.16"
        bd("1").divide(bd("6"), 2, RoundingMode.CEILING).toString() shouldBe "0.17"
        bd("1").divide(bd("6"), 2, RoundingMode.FLOOR).toString() shouldBe "0.16"
        bd("1").divide(bd("6"), 2, RoundingMode.HALF_UP).toString() shouldBe "0.17"
        bd("1").divide(bd("6"), 2, RoundingMode.HALF_DOWN).toString() shouldBe "0.17"
        bd("1").divide(bd("6"), 2, RoundingMode.HALF_EVEN).toString() shouldBe "0.17"

        // Negative: -1 / 6 = -0.1666...
        bd("-1").divide(bd("6"), 2, RoundingMode.UP).toString() shouldBe "-0.17"
        bd("-1").divide(bd("6"), 2, RoundingMode.DOWN).toString() shouldBe "-0.16"
        bd("-1").divide(bd("6"), 2, RoundingMode.CEILING).toString() shouldBe "-0.16"
        bd("-1").divide(bd("6"), 2, RoundingMode.FLOOR).toString() shouldBe "-0.17"
    }
})

// ── divideAndRemainder path coverage ──────────────────────────────────────────
// Native divideAndRemainder(other) has 7 branches:
//   1. Zero dividend
//   2. Negative preferredScale + unit divisor (±1)
//   3. Negative preferredScale + exact quotient
//   4. Non-negative preferredScale + digit-based scaled quotient
//   5. Non-negative preferredScale + digit division
//   6. Non-negative preferredScale + full BigInteger division
//   7. Negative preferredScale + remainder reduction

class BigDecimalDivideAndRemainderPathTest : FunSpec({

    test("zero dividend returns zero pair with correct scales") {
        val result = bd("0.00").divideAndRemainder(bd("5"))
        result[0].toString() shouldBe "0.00"
        result[1].toString() shouldBe "0.00"
    }

    test("negative preferredScale with unit divisor") {
        // preferredScale = scale(100) - scale(0.01) = 0 - 2 = -2, divisor unscaled = 1
        val result = bd("100").divideAndRemainder(bd("0.01"))
        result[0].toString() shouldBe "1.00E+4"
        result[1].toString() shouldBe "0"

        // Negative unit divisor
        val result2 = bd("100").divideAndRemainder(bd("-0.01"))
        result2[0].toString() shouldBe "-1.00E+4"
        result2[1].toString() shouldBe "0"
    }

    test("negative preferredScale with exact non-unit quotient") {
        // preferredScale < 0, |dividend| >= |divisor|, divides evenly
        val result = bd("200").divideAndRemainder(bd("0.04"))
        result[0].toString() shouldBe "5.0E+3"
        result[1].toString() shouldBe "0"
    }

    test("positive preferredScale with digit-based division") {
        // preferredScale = 1, small divisor triggers digit path
        val result = bd("10.5").divideAndRemainder(bd("3"))
        result[0].toString() shouldBe "3.0"
        result[1].toString() shouldBe "1.5"
    }

    test("positive preferredScale with larger divisor") {
        val result = bd("100.00").divideAndRemainder(bd("30"))
        result[0].toString() shouldBe "3.00"
        result[1].toString() shouldBe "10.00"
    }

    test("positive preferredScale with full BigInteger division") {
        // Use multi-limb divisor to force full BigInteger path
        val largeDivisor = bd("100000000000000000000") // 10^20
        val largeDividend = bd("300000000000000000000.00")
        val result = largeDividend.divideAndRemainder(largeDivisor)
        result[0].toString() shouldBe "3.00"
        result[1].compareTo(bd("0")) shouldBeExactly 0
    }

    test("negative preferredScale with nonzero remainder") {
        // Forces the remainder reduction path
        val result = bd("10").divideAndRemainder(bd("0.3"))
        // 10 / 0.3: quotient = 33, remainder = 10 - 33*0.3 = 0.1
        result[0].compareTo(bd("33")) shouldBeExactly 0
        result[1].compareTo(bd("0.1")) shouldBeExactly 0
    }

    test("divideAndRemainder reconstruction invariant for all branch shapes") {
        // (quotient * divisor) + remainder == dividend
        val cases = listOf(
            "0.00" to "5",
            "100" to "0.01",
            "200" to "0.04",
            "10.5" to "3",
            "100.00" to "30",
            "10" to "0.3",
            "7" to "2",
            "-7" to "2",
            "7" to "-2",
        )
        for ((a, b) in cases) {
            val result = bd(a).divideAndRemainder(bd(b))
            val reconstructed = result[0].multiply(bd(b)).add(result[1])
            reconstructed.compareTo(bd(a)) shouldBeExactly 0
        }
    }

    test("divideAndRemainder with MathContext") {
        val mc = MathContext(2, RoundingMode.DOWN)
        val result = bd("10").divideAndRemainder(bd("3"), mc)
        result[0].toString() shouldBe "3"
        result[1].toString() shouldBe "1"
    }
})

// ── sqrt path coverage ────────────────────────────────────────────────────────
// Native sqrt(mathContext) has these paths:
//   1. Negative → exception
//   2. Zero → scale/2
//   3. Power-of-ten fast path
//   4. Newton-Raphson + UNNECESSARY mode check
//   5. Newton-Raphson + DOWN/FLOOR adjustment
//   6. Newton-Raphson + UP/CEILING adjustment
//   7. Newton-Raphson + HALF modes (no special adjustment)

class BigDecimalSqrtPathTest : FunSpec({

    test("negative throws") {
        shouldThrow<ArithmeticException> {
            bd("-1").sqrt(MathContext(3, RoundingMode.HALF_UP))
        }
        shouldThrow<ArithmeticException> {
            bd("-100").sqrt(MathContext(3, RoundingMode.HALF_UP))
        }
    }

    test("zero returns zero with scale divided by 2") {
        bd("0").sqrt(MathContext(3, RoundingMode.HALF_UP)).let {
            it.toString() shouldBe "0"
            it.scale() shouldBeExactly 0
        }
        bd("0.00").sqrt(MathContext(3, RoundingMode.HALF_UP)).let {
            it.toString() shouldBe "0.0"
            it.scale() shouldBeExactly 1
        }
        bd("0.0000").sqrt(MathContext(3, RoundingMode.HALF_UP)).let {
            it.toString() shouldBe "0.00"
            it.scale() shouldBeExactly 2
        }
        // Zero with negative scale
        BigDecimal(bi("0"), -2).sqrt(MathContext(3, RoundingMode.HALF_UP)).let {
            it.toString() shouldBe "0E+1"
            it.scale() shouldBeExactly -1
        }
    }

    test("power-of-ten fast path") {
        bd("100").sqrt(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "10"
        bd("10000").sqrt(MathContext(5, RoundingMode.HALF_UP)).toString() shouldBe "100"
        bd("0.01").sqrt(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "0.1"
        bd("0.0001").sqrt(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "0.01"
        bd("1").sqrt(MathContext(3, RoundingMode.HALF_UP)).toString() shouldBe "1"
    }

    test("power-of-ten fast path restores preferred scale after stripping zeros") {
        bd("100.00").sqrt(MathContext(5, RoundingMode.HALF_UP)).let {
            it.toString() shouldBe "10.0"
            it.scale() shouldBeExactly 1
        }
    }

    test("DOWN rounding mode") {
        // sqrt(2) ≈ 1.41421356..., DOWN at 3 digits = 1.41
        bd("2").sqrt(MathContext(3, RoundingMode.DOWN)).toString() shouldBe "1.41"
        // sqrt(3) ≈ 1.73205080..., DOWN at 3 digits = 1.73
        bd("3").sqrt(MathContext(3, RoundingMode.DOWN)).toString() shouldBe "1.73"
    }

    test("FLOOR rounding mode") {
        bd("2").sqrt(MathContext(3, RoundingMode.FLOOR)).toString() shouldBe "1.41"
        bd("3").sqrt(MathContext(3, RoundingMode.FLOOR)).toString() shouldBe "1.73"
    }

    test("UP rounding mode") {
        // sqrt(2) ≈ 1.41421..., UP at 3 digits = 1.42
        bd("2").sqrt(MathContext(3, RoundingMode.UP)).toString() shouldBe "1.42"
        bd("3").sqrt(MathContext(3, RoundingMode.UP)).toString() shouldBe "1.74"
    }

    test("CEILING rounding mode") {
        bd("2").sqrt(MathContext(3, RoundingMode.CEILING)).toString() shouldBe "1.42"
        bd("3").sqrt(MathContext(3, RoundingMode.CEILING)).toString() shouldBe "1.74"
    }

    test("HALF_UP HALF_DOWN HALF_EVEN rounding modes") {
        // sqrt(2) ≈ 1.41421356..., at 4 digits the 5th digit is 2 → all agree on 1.414
        bd("2").sqrt(MathContext(4, RoundingMode.HALF_UP)).toString() shouldBe "1.414"
        bd("2").sqrt(MathContext(4, RoundingMode.HALF_DOWN)).toString() shouldBe "1.414"
        bd("2").sqrt(MathContext(4, RoundingMode.HALF_EVEN)).toString() shouldBe "1.414"
    }

    test("UNNECESSARY with exact square root succeeds") {
        bd("4").sqrt(MathContext(1, RoundingMode.UNNECESSARY)).toString() shouldBe "2"
        bd("9").sqrt(MathContext(1, RoundingMode.UNNECESSARY)).toString() shouldBe "3"
        bd("0.25").sqrt(MathContext(2, RoundingMode.UNNECESSARY)).toString() shouldBe "0.5"
    }

    test("UNNECESSARY with inexact square root throws") {
        shouldThrow<ArithmeticException> {
            bd("2").sqrt(MathContext(10, RoundingMode.UNNECESSARY))
        }
        shouldThrow<ArithmeticException> {
            bd("3").sqrt(MathContext(10, RoundingMode.UNNECESSARY))
        }
    }

    test("unlimited precision with inexact square root throws") {
        shouldThrow<ArithmeticException> {
            bd("2").sqrt(MathContext(0, RoundingMode.HALF_UP))
        }
    }

    test("unlimited precision with exact square root succeeds") {
        bd("4").sqrt(MathContext(0, RoundingMode.HALF_UP)).toString() shouldBe "2"
    }

    test("sqrt with high precision") {
        val result = bd("2").sqrt(MathContext(20, RoundingMode.HALF_UP))
        result.precision() shouldBeExactly 20
        // Verify it's close to known value
        result.toString() shouldBe "1.4142135623730950488"
    }

    test("sqrt preferred scale adjustment") {
        // scale of result should be ceil(scale/2) adjusted to fit precision
        bd("4.00").sqrt(MathContext(3, RoundingMode.HALF_UP)).let {
            it.toString() shouldBe "2.0"
            it.scale() shouldBeExactly 1
        }
    }
})

// ── Add/Subtract path coverage ───────────────────────────────────────────────
// Native add/subtract (lines 116-134 of BigDecimal.native.kt) calls
// rescaleUnscaled when scales differ, which multiplies by power of ten. Paths:
//   1. Same scale — no rescaling
//   2. Different scales — rescaleUnscaled called
//   3. Large scale difference — stresses power-of-ten cache
//   4. MathContext rounding
//   5. Cancellation to zero

class BigDecimalAddSubtractPathTest : FunSpec({

    test("same scale addition needs no rescaling") {
        (bd("1.23") + bd("4.56")).toString() shouldBe "5.79"
        (bd("1.23") - bd("4.56")).toString() shouldBe "-3.33"
    }

    test("different scales trigger rescaling") {
        // scale 2 + scale 0 → rescale to scale 2
        (bd("1.23") + bd("4")).toString() shouldBe "5.23"
        (bd("4") + bd("1.23")).toString() shouldBe "5.23"
        // scale 5 + scale 1
        (bd("0.00001") + bd("0.1")).toString() shouldBe "0.10001"
    }

    test("large scale difference stresses power-of-ten path") {
        // scale 0 + scale 20
        val small = BigDecimal(bi("1"), 20) // 1E-20
        val big = bd("1")
        val result = big + small
        result.scale() shouldBeExactly 20
        result.compareTo(bd("1")) shouldBeExactly 1
    }

    test("multi-limb operands with carry") {
        val a = bd("99999999999999999999") // 20-digit, multi-limb
        val b = bd("1")
        (a + b).toString() shouldBe "100000000000000000000"
        // multi-limb + multi-limb
        (a + a).toString() shouldBe "199999999999999999998"
    }

    test("subtraction cancellation to zero preserves scale") {
        val a = bd("1.23")
        (a - a).let {
            it.signum() shouldBeExactly 0
            it.scale() shouldBeExactly 2
            it.toString() shouldBe "0.00"
        }
        // Different scale cancellation
        (bd("1.230") - bd("1.23")).let {
            it.signum() shouldBeExactly 0
            it.scale() shouldBeExactly 3
        }
    }

    test("add with MathContext rounds result") {
        val mc = MathContext(3, RoundingMode.HALF_UP)
        bd("1.23").add(bd("4.56"), mc).toString() shouldBe "5.79"
        bd("1.236").add(bd("4.567"), mc).toString() shouldBe "5.80"
    }

    test("subtract with MathContext rounds result") {
        val mc = MathContext(2, RoundingMode.DOWN)
        bd("10.9").subtract(bd("0.1"), mc).toString() shouldBe "10"
        bd("100.99").subtract(bd("0.01"), mc).toString() shouldBe "1.0E+2"
    }

    test("add sign combinations") {
        (bd("5") + bd("-3")).toString() shouldBe "2"
        (bd("-5") + bd("3")).toString() shouldBe "-2"
        (bd("-5") + bd("-3")).toString() shouldBe "-8"
    }
})

// ── divide(other, MathContext) path coverage ─────────────────────────────────
// Native divide(other, MathContext) at lines 226-257 has two division strategies:
//   - Strategy 1: scales up dividend (when mathContext.precision + yscale > xscale)
//   - Strategy 2: scales up divisor (otherwise)
// Also: zero dividend, magnitude normalization branch

class BigDecimalDivideMathContextPathTest : FunSpec({

    test("zero dividend with MathContext returns zero") {
        val mc = MathContext(5, RoundingMode.HALF_UP)
        bd("0").divide(bd("123"), mc).let {
            it.signum() shouldBeExactly 0
        }
        bd("0.00").divide(bd("7"), mc).let {
            it.signum() shouldBeExactly 0
        }
    }

    test("dividend scaling strategy - small dividend large divisor") {
        // precision + yscale > xscale triggers dividend scaling
        val mc = MathContext(10, RoundingMode.HALF_UP)
        bd("1").divide(bd("7"), mc).toString() shouldBe "0.1428571429"
        bd("1").divide(bd("3"), mc).toString() shouldBe "0.3333333333"
    }

    test("divisor scaling strategy - large dividend small divisor") {
        val mc = MathContext(5, RoundingMode.HALF_UP)
        bd("123456789").divide(bd("3"), mc).toString() shouldBe "4.1152E+7"
        bd("999999").divide(bd("7"), mc).toString() shouldBe "1.4286E+5"
    }

    test("multi-limb operands with MathContext") {
        val mc = MathContext(10, RoundingMode.HALF_UP)
        val a = bd("100000000000000000000") // 10^20
        val b = bd("300000000000000000000") // 3*10^20
        a.divide(b, mc).toString() shouldBe "0.3333333333"
    }

    test("magnitude normalization adjusts yscale") {
        // When |dividend| > |divisor| after normalization, yscale is decremented
        val mc = MathContext(5, RoundingMode.HALF_UP)
        bd("9").divide(bd("2"), mc).toString() shouldBe "4.5"
        bd("99").divide(bd("10"), mc).toString() shouldBe "9.9"
    }

    test("divide MathContext with various rounding modes") {
        val a = bd("10")
        val b = bd("3")
        a.divide(b, MathContext(4, RoundingMode.DOWN)).toString() shouldBe "3.333"
        a.divide(b, MathContext(4, RoundingMode.UP)).toString() shouldBe "3.334"
        a.divide(b, MathContext(4, RoundingMode.CEILING)).toString() shouldBe "3.334"
        a.divide(b, MathContext(4, RoundingMode.FLOOR)).toString() shouldBe "3.333"
    }

    test("unlimited precision MathContext delegates to exact divide") {
        val mc = MathContext(0)
        bd("10").divide(bd("5"), mc).toString() shouldBe "2"
        shouldThrow<ArithmeticException> {
            bd("1").divide(bd("3"), mc)
        }
    }
})

// ── divideToScale path coverage ──────────────────────────────────────────────
// Internal divideToScale (lines 740-760) called by divide(other, scale, RoundingMode).
// Two branches based on scaleShift sign.

class BigDecimalDivideToScalePathTest : FunSpec({

    test("zero dividend at various scales") {
        bd("0").divide(bd("5"), 0, RoundingMode.HALF_UP).toString() shouldBe "0"
        bd("0").divide(bd("5"), 3, RoundingMode.HALF_UP).toString() shouldBe "0.000"
        bd("0.00").divide(bd("5"), 0, RoundingMode.HALF_UP).toString() shouldBe "0"
    }

    test("positive scaleShift scales up dividend") {
        // targetScale > (this.scale - other.scale) → scaleShift >= 0
        bd("10").divide(bd("3"), 5, RoundingMode.HALF_UP).toString() shouldBe "3.33333"
        bd("1").divide(bd("7"), 10, RoundingMode.HALF_UP).toString() shouldBe "0.1428571429"
    }

    test("negative scaleShift scales up divisor") {
        // targetScale < (this.scale - other.scale) → scaleShift < 0
        // e.g., scale(1.000000) = 6, scale(3) = 0, diff = 6, targetScale = 2 → shift = -4
        bd("1.000000").divide(bd("3"), 2, RoundingMode.HALF_UP).toString() shouldBe "0.33"
    }

    test("single-limb divisor exercises digit magnitude path") {
        bd("100").divide(bd("7"), 4, RoundingMode.HALF_UP).toString() shouldBe "14.2857"
        bd("100").divide(bd("13"), 4, RoundingMode.HALF_UP).toString() shouldBe "7.6923"
    }

    test("multi-limb divisor forces full division path") {
        val largeDivisor = bd("100000000000000000000") // 10^20
        bd("300000000000000000000").divide(largeDivisor, 5, RoundingMode.HALF_UP).toString() shouldBe "3.00000"
        bd("100000000000000000001").divide(largeDivisor, 5, RoundingMode.HALF_UP).toString() shouldBe "1.00000"
    }

    test("all rounding modes with scale") {
        // 2 / 3 = 0.666... at scale 2
        bd("2").divide(bd("3"), 2, RoundingMode.UP).toString() shouldBe "0.67"
        bd("2").divide(bd("3"), 2, RoundingMode.DOWN).toString() shouldBe "0.66"
        bd("2").divide(bd("3"), 2, RoundingMode.CEILING).toString() shouldBe "0.67"
        bd("2").divide(bd("3"), 2, RoundingMode.FLOOR).toString() shouldBe "0.66"
        bd("2").divide(bd("3"), 2, RoundingMode.HALF_UP).toString() shouldBe "0.67"
        bd("2").divide(bd("3"), 2, RoundingMode.HALF_DOWN).toString() shouldBe "0.67"
        bd("2").divide(bd("3"), 2, RoundingMode.HALF_EVEN).toString() shouldBe "0.67"
    }

    test("negative scale target") {
        bd("12345").divide(bd("1"), -2, RoundingMode.HALF_UP).toString() shouldBe "1.23E+4"
    }
})

// ── pow(exponent, MathContext) path coverage ─────────────────────────────────
// pow(exponent, MathContext) at lines 384-420 uses binary exponentiation.

class BigDecimalPowMathContextPathTest : FunSpec({

    test("exponent zero returns one regardless of base") {
        val mc = MathContext(5, RoundingMode.HALF_UP)
        bd("123.456").pow(0, mc).toString() shouldBe "1"
        bd("-999").pow(0, mc).toString() shouldBe "1"
        bd("0").pow(0, mc).toString() shouldBe "1"
    }

    test("exponent one returns base rounded to MathContext") {
        val mc = MathContext(3, RoundingMode.HALF_UP)
        bd("123.456").pow(1, mc).toString() shouldBe "123"
    }

    test("small positive exponent binary loop") {
        val mc = MathContext(10, RoundingMode.HALF_UP)
        bd("2").pow(10, mc).toString() shouldBe "1024"
        bd("1.5").pow(3, mc).toString() shouldBe "3.375"
    }

    test("multi-limb base with exponent") {
        val mc = MathContext(10, RoundingMode.HALF_UP)
        bd("100000000000000000000").pow(2, mc).toString() shouldBe "1.000000000E+40"
    }

    test("negative exponent computes reciprocal") {
        val mc = MathContext(10, RoundingMode.HALF_UP)
        bd("2").pow(-1, mc).toString() shouldBe "0.5"
        bd("4").pow(-2, mc).toString() shouldBe "0.0625"
        bd("10").pow(-3, mc).toString() shouldBe "0.001"
    }

    test("exponent digits exceed precision throws") {
        // mc.precision = 1, exponent = 10 has 2 digits > 1
        shouldThrow<ArithmeticException> {
            bd("2").pow(10, MathContext(1, RoundingMode.HALF_UP))
        }
    }

    test("large exponent out of range throws") {
        shouldThrow<ArithmeticException> {
            bd("2").pow(-1_000_000_000, MathContext(10, RoundingMode.HALF_UP))
        }
    }

    test("unlimited precision MathContext delegates to plain pow") {
        bd("3").pow(4, MathContext(0)).toString() shouldBe "81"
    }
})

// ── divideToIntegralValue path coverage ──────────────────────────────────────
// Public divideToIntegralValue(other, MathContext) at lines 348-375 and
// private version at lines 762-775.

class BigDecimalDivideToIntegralValuePathTest : FunSpec({

    test("unlimited precision delegates to private version") {
        val mc = MathContext(0)
        bd("10").divideToIntegralValue(bd("3"), mc).toString() shouldBe "3"
        bd("7.5").divideToIntegralValue(bd("2"), mc).toString() shouldBe "3.0"
    }

    test("small dividend less than divisor returns zero") {
        val mc = MathContext(5, RoundingMode.HALF_UP)
        bd("1").divideToIntegralValue(bd("10"), mc).toString() shouldBe "0"
        bd("3").divideToIntegralValue(bd("100"), mc).toString() shouldBe "0"
    }

    test("result with negative scale checks division impossible") {
        // 1000 / 0.001 = 1000000 with negative preferredScale
        val mc = MathContext(10, RoundingMode.DOWN)
        bd("1000").divideToIntegralValue(bd("0.001"), mc).toString() shouldBe "1.000E+6"
    }

    test("division impossible throws with tight precision") {
        shouldThrow<ArithmeticException> {
            bd("999").divideToIntegralValue(bd("1"), MathContext(2, RoundingMode.DOWN))
        }
    }

    test("result with positive scale truncated to zero") {
        val mc = MathContext(10, RoundingMode.DOWN)
        bd("10.5").divideToIntegralValue(bd("3"), mc).toString() shouldBe "3.0"
        bd("100.00").divideToIntegralValue(bd("30"), mc).toString() shouldBe "3.00"
    }

    test("preferred scale with precision headroom adjusts scale") {
        val mc = MathContext(20, RoundingMode.HALF_UP)
        // Large precision headroom allows preferred scale to be honored
        bd("100").divideToIntegralValue(bd("3"), mc).toString() shouldBe "33"
    }

    test("private version preferredScale >= 0 scales divisor") {
        val mc = MathContext(0)
        // scale(10.5) - scale(3) = 1 - 0 = 1 >= 0
        bd("10.5").divideToIntegralValue(bd("3"), mc).toString() shouldBe "3.0"
    }

    test("private version preferredScale < 0 scales dividend") {
        val mc = MathContext(0)
        // scale(100) - scale(0.01) = 0 - 2 = -2 < 0
        val result = bd("100").divideToIntegralValue(bd("0.01"), mc)
        result.compareTo(bd("10000")) shouldBeExactly 0
    }

    test("sign combinations") {
        val mc = MathContext(10, RoundingMode.DOWN)
        bd("10").divideToIntegralValue(bd("3"), mc).compareTo(bd("3")) shouldBeExactly 0
        bd("-10").divideToIntegralValue(bd("3"), mc).compareTo(bd("-3")) shouldBeExactly 0
        bd("10").divideToIntegralValue(bd("-3"), mc).compareTo(bd("-3")) shouldBeExactly 0
        bd("-10").divideToIntegralValue(bd("-3"), mc).compareTo(bd("3")) shouldBeExactly 0
    }
})
