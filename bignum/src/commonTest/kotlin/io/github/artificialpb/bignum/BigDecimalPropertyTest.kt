package io.github.artificialpb.bignum

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlin.math.abs

fun Arb.Companion.bigDecimal(): Arb<BigDecimal> = arbitrary { rs ->
    when (rs.random.nextInt(5)) {
        1 -> {
            // Negative scale
            val seed = rs.random.nextLong()
            val scale = -(abs(seed) % 20).toInt()
            bigDecimalOf(bigIntegerOf(seed), scale)
        }
        2 -> {
            // Multi-limb: 20+ digit unscaled value (>60 bits per limb)
            val hi = abs(rs.random.nextLong()).toString()
            val lo = abs(rs.random.nextLong()).toString().padStart(5, '0')
            val sign = if (rs.random.nextBoolean()) "" else "-"
            val unscaled = BigInteger("$sign$hi$lo")
            val scale = rs.random.nextInt(0, 10)
            bigDecimalOf(unscaled, scale)
        }
        3 -> {
            // Higher scale range (up to 20)
            val seed = rs.random.nextLong()
            val scale = rs.random.nextInt(0, 21)
            bigDecimalOf(bigIntegerOf(seed), scale)
        }
        else -> {
            // Original: Long range, scale 0-5
            bigDecimalFromSeed(rs.random.nextLong())
        }
    }
}

fun Arb.Companion.nonZeroBigDecimal(): Arb<BigDecimal> = Arb.bigDecimal().filter { it.signum() != 0 }

private fun bigDecimalFromSeed(seed: Long): BigDecimal {
    val magnitude = when {
        seed == Long.MIN_VALUE -> Long.MAX_VALUE
        seed < 0L -> -seed
        else -> seed
    }
    val scale = (magnitude % 6L).toInt()
    return bigDecimalOf(bigIntegerOf(seed), scale)
}

private inline fun assertImmutable(vararg values: BigDecimal, block: () -> Unit) {
    val snapshots = values.map { it.toString() }
    block()
    values.zip(snapshots).forEach { (value, snapshot) ->
        value.toString() shouldBe snapshot
    }
}

class BigDecimalArithmeticPropertyTest : FunSpec({
    test("addition is commutative") {
        checkAll(Arb.bigDecimal(), Arb.bigDecimal()) { a, b ->
            assertImmutable(a, b) {
                (a + b) shouldBe (b + a)
            }
        }
    }

    test("subtraction is addition with negation up to cohort equality") {
        checkAll(Arb.bigDecimal(), Arb.bigDecimal()) { a, b ->
            assertImmutable(a, b) {
                (a - b).compareTo(a + (-b)) shouldBeExactly 0
            }
        }
    }

    test("multiplication is commutative") {
        checkAll(Arb.bigDecimal(), Arb.bigDecimal()) { a, b ->
            assertImmutable(a, b) {
                (a * b) shouldBe (b * a)
            }
        }
    }

    test("negation is self-inverse") {
        checkAll(Arb.bigDecimal()) { a ->
            assertImmutable(a) {
                (-(-a)) shouldBe a
            }
        }
    }

    test("abs is non-negative and magnitude-stable") {
        checkAll(Arb.bigDecimal()) { a ->
            assertImmutable(a) {
                val absA = a.abs()
                absA.signum() shouldBe if (a.signum() == 0) 0 else 1
                absA.compareTo((-a).abs()) shouldBeExactly 0
            }
        }
    }

    test("divideAndRemainder reconstructs the dividend") {
        checkAll(Arb.bigDecimal(), Arb.nonZeroBigDecimal()) { a, b ->
            assertImmutable(a, b) {
                val result = a.divideAndRemainder(b)
                ((result[0] * b) + result[1]).compareTo(a) shouldBeExactly 0
            }
        }
    }

    test("MathContext divideAndRemainder reconstructs the dividend when it succeeds") {
        val mathContext = MathContext(6, RoundingMode.DOWN)
        checkAll(Arb.bigDecimal(), Arb.nonZeroBigDecimal()) { a, b ->
            assertImmutable(a, b) {
                runCatching { a.divideAndRemainder(b, mathContext) }.getOrNull()?.let { result ->
                    ((result[0] * b) + result[1]).compareTo(a) shouldBeExactly 0
                }
            }
        }
    }

    test("multiplication is associative up to cohort equality") {
        checkAll(Arb.bigDecimal(), Arb.bigDecimal(), Arb.bigDecimal()) { a, b, c ->
            assertImmutable(a, b, c) {
                ((a * b) * c).compareTo(a * (b * c)) shouldBeExactly 0
            }
        }
    }

    test("distributivity holds up to cohort equality") {
        checkAll(Arb.bigDecimal(), Arb.bigDecimal(), Arb.bigDecimal()) { a, b, c ->
            assertImmutable(a, b, c) {
                (a * (b + c)).compareTo(a * b + a * c) shouldBeExactly 0
            }
        }
    }

    test("division by self yields one for non-zero terminating values") {
        checkAll(Arb.nonZeroBigDecimal()) { a ->
            assertImmutable(a) {
                runCatching { a / a }.getOrNull()?.let { result ->
                    result.compareTo(BigDecimal("1")) shouldBeExactly 0
                }
            }
        }
    }
})

class BigDecimalScalePropertyTest : FunSpec({
    test("stripTrailingZeros preserves numeric value") {
        checkAll(Arb.bigDecimal()) { a ->
            assertImmutable(a) {
                a.stripTrailingZeros().compareTo(a) shouldBeExactly 0
            }
        }
    }

    test("plus preserves representation") {
        checkAll(Arb.bigDecimal()) { a ->
            assertImmutable(a) {
                a.plus() shouldBe a
            }
        }
    }

    test("round and plus with MathContext are identical") {
        val mathContext = MathContext(4, RoundingMode.HALF_EVEN)
        checkAll(Arb.bigDecimal()) { a ->
            assertImmutable(a) {
                a.round(mathContext) shouldBe a.plus(mathContext)
            }
        }
    }

    test("increasing scale and restoring it is identity") {
        checkAll(Arb.bigDecimal(), Arb.int(0..6)) { a, extraScale ->
            assertImmutable(a) {
                a.setScale(a.scale() + extraScale).setScale(a.scale()) shouldBe a
            }
        }
    }

    test("movePointLeft then movePointRight preserves value") {
        checkAll(Arb.bigDecimal(), Arb.int(0..6)) { a, n ->
            assertImmutable(a) {
                a.movePointLeft(n).movePointRight(n).compareTo(a) shouldBeExactly 0
            }
        }
    }

    test("scaleByPowerOfTen adjusts scale by n") {
        checkAll(Arb.bigDecimal(), Arb.int(-100..100)) { a, n ->
            assertImmutable(a) {
                val result = a.scaleByPowerOfTen(n)
                result.scale() shouldBeExactly (a.scale() - n)
                result.unscaledValue() shouldBe a.unscaledValue()
            }
        }
    }

    test("ulp has same scale as operand") {
        checkAll(Arb.bigDecimal()) { a ->
            assertImmutable(a) {
                a.ulp().scale() shouldBeExactly a.scale()
            }
        }
    }

    test("ulp equals one with operand scale") {
        checkAll(Arb.bigDecimal()) { a ->
            assertImmutable(a) {
                a.ulp() shouldBe BigDecimal(BigInteger("1"), a.scale())
            }
        }
    }
})

class BigDecimalConversionPropertyTest : FunSpec({
    test("toString round-trips through constructor") {
        checkAll(Arb.bigDecimal()) { a ->
            BigDecimal(a.toString()) shouldBe a
        }
    }

    test("toPlainString round-trips through constructor with cohort equality") {
        checkAll(Arb.bigDecimal()) { a ->
            BigDecimal(a.toPlainString()).compareTo(a) shouldBeExactly 0
        }
    }

    test("toBigIntegerExact round-trips for scale-zero values") {
        checkAll(Arb.long()) { value ->
            val decimal = bigDecimalOf(value)
            decimal.toBigIntegerExact() shouldBe bigIntegerOf(value)
        }
    }

    test("primitive conversions round-trip for small integral values") {
        checkAll(Arb.int()) { value ->
            val decimal = bigDecimalOf(value)
            decimal.toInt() shouldBeExactly value
            decimal.toIntExact() shouldBeExactly value
        }
    }

    test("toEngineeringString exponent is divisible by 3 when present") {
        checkAll(Arb.bigDecimal()) { a ->
            val str = a.toEngineeringString()
            val eIndex = str.indexOfFirst { it == 'E' || it == 'e' }
            if (eIndex >= 0) {
                val exponent = str.substring(eIndex + 1).replace("+", "").toInt()
                (exponent % 3) shouldBeExactly 0
            }
        }
    }
})

class BigDecimalComparisonPropertyTest : FunSpec({
    test("compareTo is antisymmetric") {
        checkAll(Arb.bigDecimal(), Arb.bigDecimal()) { a, b ->
            a.compareTo(b) shouldBeExactly -b.compareTo(a)
        }
    }

    test("equals implies compareTo zero") {
        checkAll(Arb.bigDecimal()) { a ->
            val copy = BigDecimal(a.toString())
            a shouldBe copy
            a.compareTo(copy) shouldBeExactly 0
        }
    }

    test("min and max return operands from the same cohort") {
        checkAll(Arb.bigDecimal(), Arb.bigDecimal()) { a, b ->
            val min = a.min(b)
            val max = a.max(b)
            (min == a || min == b) shouldBe true
            (max == a || max == b) shouldBe true
            (min.compareTo(max) <= 0) shouldBe true
        }
    }
})

class BigDecimalMathContextPropertyTest : FunSpec({
    test("round result precision does not exceed MathContext precision") {
        val mc = MathContext(4, RoundingMode.HALF_UP)
        checkAll(Arb.bigDecimal()) { a ->
            assertImmutable(a) {
                val rounded = a.round(mc)
                if (rounded.signum() != 0) {
                    (rounded.precision() <= mc.precision) shouldBe true
                }
            }
        }
    }

    test("round is idempotent") {
        val mc = MathContext(4, RoundingMode.HALF_EVEN)
        checkAll(Arb.bigDecimal()) { a ->
            assertImmutable(a) {
                a.round(mc).round(mc) shouldBe a.round(mc)
            }
        }
    }
})
