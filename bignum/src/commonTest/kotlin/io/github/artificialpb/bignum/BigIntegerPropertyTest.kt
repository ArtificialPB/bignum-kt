package io.github.artificialpb.bignum

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

// -- Custom generators --

/** Generates BigIntegers from random Long values */
fun Arb.Companion.bigInteger(): Arb<BigInteger> =
    Arb.long().map { BigIntegers.of(it) }

/** Generates non-zero BigIntegers */
fun Arb.Companion.nonZeroBigInteger(): Arb<BigInteger> =
    Arb.long().filter { it != 0L }.map { BigIntegers.of(it) }

/** Generates positive BigIntegers (> 0) */
fun Arb.Companion.positiveBigInteger(): Arb<BigInteger> =
    Arb.long(1L..Long.MAX_VALUE).map { BigIntegers.of(it) }

/** Generates large BigIntegers from string concatenation of random longs */
fun Arb.Companion.largeBigInteger(): Arb<BigInteger> =
    Arb.long().map { seed ->
        // Create a number larger than Long.MAX_VALUE by combining digits
        val abs = if (seed == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(seed)
        val sign = if (seed < 0) "-" else ""
        BigInteger("${sign}${abs}${abs.toString().takeLast(10)}")
    }

// -- Immutability helper --
// Captures snapshots of all inputs, runs the block, and asserts none changed.

private inline fun assertImmutable(vararg values: BigInteger, block: () -> Unit) {
    val snapshots = values.map { it.toString() }
    block()
    values.zip(snapshots).forEach { (value, snapshot) ->
        value.toString() shouldBe snapshot
    }
}

class BigIntegerArithmeticPropertyTest : FunSpec({

    test("addition is commutative: a + b == b + a") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            assertImmutable(a, b) { (a + b) shouldBe (b + a) }
        }
    }

    test("addition is associative: (a + b) + c == a + (b + c)") {
        checkAll(Arb.bigInteger(), Arb.bigInteger(), Arb.bigInteger()) { a, b, c ->
            ((a + b) + c) shouldBe (a + (b + c))
        }
    }

    test("addition identity: a + 0 == a") {
        checkAll(Arb.bigInteger()) { a ->
            (a + BigIntegers.ZERO) shouldBe a
        }
    }

    test("additive inverse: a + (-a) == 0") {
        checkAll(Arb.bigInteger()) { a ->
            assertImmutable(a) { (a + (-a)) shouldBe BigIntegers.ZERO }
        }
    }

    test("subtraction: a - b == a + (-b)") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            assertImmutable(a, b) { (a - b) shouldBe (a + (-b)) }
        }
    }

    test("multiplication is commutative: a * b == b * a") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            assertImmutable(a, b) { (a * b) shouldBe (b * a) }
        }
    }

    test("multiplication is associative: (a * b) * c == a * (b * c)") {
        checkAll(Arb.bigInteger(), Arb.bigInteger(), Arb.bigInteger()) { a, b, c ->
            ((a * b) * c) shouldBe (a * (b * c))
        }
    }

    test("multiplication identity: a * 1 == a") {
        checkAll(Arb.bigInteger()) { a ->
            (a * BigIntegers.ONE) shouldBe a
        }
    }

    test("multiplication by zero: a * 0 == 0") {
        checkAll(Arb.bigInteger()) { a ->
            (a * BigIntegers.ZERO) shouldBe BigIntegers.ZERO
        }
    }

    test("distributive: a * (b + c) == a * b + a * c") {
        checkAll(Arb.bigInteger(), Arb.bigInteger(), Arb.bigInteger()) { a, b, c ->
            (a * (b + c)) shouldBe ((a * b) + (a * c))
        }
    }

    test("division-remainder identity: a == (a / b) * b + (a % b)") {
        checkAll(Arb.bigInteger(), Arb.nonZeroBigInteger()) { a, b ->
            assertImmutable(a, b) {
                val q = a / b
                val r = a % b
                ((q * b) + r) shouldBe a
            }
        }
    }

    test("divideAndRemainder matches division and remainder") {
        checkAll(Arb.bigInteger(), Arb.nonZeroBigInteger()) { a, b ->
            assertImmutable(a, b) {
                val result = a.divideAndRemainder(b)
                result[0] shouldBe (a / b)
                result[1] shouldBe (a % b)
            }
        }
    }

    test("negation is self-inverse: -(-a) == a") {
        checkAll(Arb.bigInteger()) { a ->
            assertImmutable(a) { (-(-a)) shouldBe a }
        }
    }

    test("abs is non-negative and preserves magnitude") {
        checkAll(Arb.bigInteger()) { a ->
            assertImmutable(a) {
                val absA = a.abs()
                absA.signum() shouldBe if (a.signum() == 0) 0 else 1
                absA shouldBe (-a).abs()
            }
        }
    }
})

class BigIntegerBitwisePropertyTest : FunSpec({

    test("not is self-inverse: ~~a == a") {
        checkAll(Arb.bigInteger()) { a ->
            assertImmutable(a) { a.not().not() shouldBe a }
        }
    }

    test("not identity: ~a == -(a + 1)") {
        checkAll(Arb.bigInteger()) { a ->
            a.not() shouldBe (-(a + BigIntegers.ONE))
        }
    }

    test("and is commutative: a & b == b & a") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            assertImmutable(a, b) { a.and(b) shouldBe b.and(a) }
        }
    }

    test("or is commutative: a | b == b | a") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            assertImmutable(a, b) { a.or(b) shouldBe b.or(a) }
        }
    }

    test("xor is commutative: a ^ b == b ^ a") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            assertImmutable(a, b) { a.xor(b) shouldBe b.xor(a) }
        }
    }

    test("xor self-cancels: a ^ a == 0") {
        checkAll(Arb.bigInteger()) { a ->
            a.xor(a) shouldBe BigIntegers.ZERO
        }
    }

    test("and with self: a & a == a") {
        checkAll(Arb.bigInteger()) { a ->
            a.and(a) shouldBe a
        }
    }

    test("or with self: a | a == a") {
        checkAll(Arb.bigInteger()) { a ->
            a.or(a) shouldBe a
        }
    }

    test("andNot identity: a & ~b == a andNot b") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            assertImmutable(a, b) { a.and(b.not()) shouldBe a.andNot(b) }
        }
    }

    test("shiftLeft then shiftRight recovers value for positive") {
        checkAll(Arb.positiveBigInteger(), Arb.int(0..30)) { a, n ->
            assertImmutable(a) { a.shiftLeft(n).shiftRight(n) shouldBe a }
        }
    }

    test("shiftLeft by n == multiply by 2^n") {
        checkAll(Arb.bigInteger(), Arb.int(0..30)) { a, n ->
            assertImmutable(a) { a.shiftLeft(n) shouldBe (a * BigInteger("2").pow(n)) }
        }
    }
})

class BigIntegerConversionPropertyTest : FunSpec({

    test("toString round-trips through constructor") {
        checkAll(Arb.bigInteger()) { a ->
            BigInteger(a.toString()) shouldBe a
        }
    }

    test("toString is never empty") {
        checkAll(Arb.bigInteger()) { a ->
            a.toString().shouldNotBeEmpty()
        }
    }

    test("toByteArray round-trips through constructor") {
        checkAll(Arb.bigInteger()) { a ->
            assertImmutable(a) { BigInteger(a.toByteArray()) shouldBe a }
        }
    }

    test("toByteArray round-trips with large values") {
        checkAll(Arb.largeBigInteger()) { a ->
            BigInteger(a.toByteArray()) shouldBe a
        }
    }

    test("toLong round-trips for values that fit in Long") {
        checkAll(Arb.long()) { n ->
            BigIntegers.of(n).toLong() shouldBe n
        }
    }

    test("toInt round-trips for values that fit in Int") {
        checkAll(Arb.int()) { n ->
            BigIntegers.of(n.toLong()).toInt() shouldBeExactly n
        }
    }

    test("toString with radix round-trips") {
        checkAll(Arb.positiveBigInteger()) { a ->
            for (radix in listOf(2, 8, 10, 16, 36)) {
                BigInteger(a.toString(radix), radix) shouldBe a
            }
        }
    }

    test("signum matches sign of value") {
        checkAll(Arb.long()) { n ->
            val bi = BigIntegers.of(n)
            val expected = when {
                n > 0 -> 1
                n < 0 -> -1
                else -> 0
            }
            bi.signum() shouldBeExactly expected
        }
    }
})

class BigIntegerComparisonPropertyTest : FunSpec({

    test("compareTo is consistent with equals") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            if (a == b) {
                a.compareTo(b) shouldBeExactly 0
            }
            if (a.compareTo(b) == 0) {
                a shouldBe b
            }
        }
    }

    test("compareTo is antisymmetric: sign(a.compareTo(b)) == -sign(b.compareTo(a))") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            Integer.signum(a.compareTo(b)) shouldBeExactly -Integer.signum(b.compareTo(a))
        }
    }

    test("min/max are consistent: min(a,b) <= max(a,b)") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            val mn = a.min(b)
            val mx = a.max(b)
            (mn.compareTo(mx) <= 0) shouldBe true
        }
    }

    test("min/max cover both values: {min, max} == {a, b}") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            val mn = a.min(b)
            val mx = a.max(b)
            setOf(mn.toString(), mx.toString()) shouldBe setOf(a.toString(), b.toString())
        }
    }
})

class BigIntegerGcdPropertyTest : FunSpec({

    test("gcd is commutative: gcd(a, b) == gcd(b, a)") {
        checkAll(Arb.bigInteger(), Arb.bigInteger()) { a, b ->
            assertImmutable(a, b) { a.gcd(b) shouldBe b.gcd(a) }
        }
    }

    test("gcd divides both operands") {
        checkAll(Arb.nonZeroBigInteger(), Arb.nonZeroBigInteger()) { a, b ->
            val g = a.gcd(b)
            (a % g) shouldBe BigIntegers.ZERO
            (b % g) shouldBe BigIntegers.ZERO
        }
    }

    test("gcd(a, 0) == |a|") {
        checkAll(Arb.bigInteger()) { a ->
            a.gcd(BigIntegers.ZERO) shouldBe a.abs()
        }
    }

    test("|lcm(a, b)| * gcd(a, b) == |a * b|") {
        checkAll(Arb.nonZeroBigInteger(), Arb.nonZeroBigInteger()) { a, b ->
            assertImmutable(a, b) { (a.lcm(b).abs() * a.gcd(b)) shouldBe (a * b).abs() }
        }
    }
})

class BigIntegerPowPropertyTest : FunSpec({

    test("pow(0) == 1 for any base") {
        checkAll(Arb.bigInteger()) { a ->
            assertImmutable(a) { a.pow(0) shouldBe BigIntegers.ONE }
        }
    }

    test("pow(1) == self") {
        checkAll(Arb.bigInteger()) { a ->
            a.pow(1) shouldBe a
        }
    }

    test("pow(2) == a * a") {
        checkAll(Arb.bigInteger()) { a ->
            a.pow(2) shouldBe (a * a)
        }
    }

    test("pow(n+1) == pow(n) * a for small exponents") {
        checkAll(Arb.long(-100L..100L).map { BigIntegers.of(it) }, Arb.int(1..8)) { a, n ->
            a.pow(n + 1) shouldBe (a.pow(n) * a)
        }
    }

    test("modPow is consistent with pow and mod for small values") {
        checkAll(
            Arb.long(1L..100L).map { BigIntegers.of(it) },
            Arb.int(1..20),
            Arb.long(2L..1000L).map { BigIntegers.of(it) },
        ) { base, exp, modulus ->
            val expBi = BigIntegers.of(exp.toLong())
            assertImmutable(base, expBi, modulus) {
                base.modPow(expBi, modulus) shouldBe base.pow(exp).mod(modulus)
            }
        }
    }
})

class BigIntegerVsLongPropertyTest : FunSpec({

    test("addition matches Long") {
        checkAll(Arb.long(), Arb.long()) { a, b ->
            val expected = a + b
            if ((a xor b) < 0 || (a xor expected) >= 0) {
                (BigIntegers.of(a) + BigIntegers.of(b)).toLong() shouldBe expected
            }
        }
    }

    test("subtraction matches Long") {
        checkAll(Arb.long(), Arb.long()) { a, b ->
            val expected = a - b
            if ((a xor b) >= 0 || (a xor expected) >= 0) {
                (BigIntegers.of(a) - BigIntegers.of(b)).toLong() shouldBe expected
            }
        }
    }

    test("multiplication matches Long") {
        checkAll(Arb.long(), Arb.long()) { a, b ->
            val expected = a * b
            if (a == 0L || expected / a == b) {
                (BigIntegers.of(a) * BigIntegers.of(b)).toLong() shouldBe expected
            }
        }
    }

    test("division matches Long") {
        checkAll(Arb.long(), Arb.long().filter { it != 0L }) { a, b ->
            if (!(a == Long.MIN_VALUE && b == -1L)) {
                (BigIntegers.of(a) / BigIntegers.of(b)).toLong() shouldBe (a / b)
            }
        }
    }

    test("remainder matches Long") {
        checkAll(Arb.long(), Arb.long().filter { it != 0L }) { a, b ->
            if (!(a == Long.MIN_VALUE && b == -1L)) {
                (BigIntegers.of(a) % BigIntegers.of(b)).toLong() shouldBe (a % b)
            }
        }
    }

    test("negation matches Long") {
        checkAll(Arb.long().filter { it != Long.MIN_VALUE }) { a ->
            (-BigIntegers.of(a)).toLong() shouldBe (-a)
        }
    }

    test("abs matches Long") {
        checkAll(Arb.long().filter { it != Long.MIN_VALUE }) { a ->
            BigIntegers.of(a).abs().toLong() shouldBe kotlin.math.abs(a)
        }
    }

    test("compareTo matches Long") {
        checkAll(Arb.long(), Arb.long()) { a, b ->
            Integer.signum(BigIntegers.of(a).compareTo(BigIntegers.of(b))) shouldBeExactly Integer.signum(a.compareTo(b))
        }
    }

    test("min matches Long") {
        checkAll(Arb.long(), Arb.long()) { a, b ->
            BigIntegers.of(a).min(BigIntegers.of(b)).toLong() shouldBe kotlin.math.min(a, b)
        }
    }

    test("max matches Long") {
        checkAll(Arb.long(), Arb.long()) { a, b ->
            BigIntegers.of(a).max(BigIntegers.of(b)).toLong() shouldBe kotlin.math.max(a, b)
        }
    }

    test("signum matches Long") {
        checkAll(Arb.long()) { a ->
            BigIntegers.of(a).signum() shouldBeExactly when {
                a > 0 -> 1
                a < 0 -> -1
                else -> 0
            }
        }
    }

    test("toInt matches Long.toInt") {
        checkAll(Arb.int()) { a ->
            BigIntegers.of(a.toLong()).toInt() shouldBeExactly a
        }
    }

    test("toString matches Long.toString") {
        checkAll(Arb.long()) { a ->
            BigIntegers.of(a).toString() shouldBe a.toString()
        }
    }

    test("bitwise and matches Long") {
        checkAll(Arb.long(), Arb.long()) { a, b ->
            BigIntegers.of(a).and(BigIntegers.of(b)).toLong() shouldBe (a and b)
        }
    }

    test("bitwise or matches Long") {
        checkAll(Arb.long(), Arb.long()) { a, b ->
            BigIntegers.of(a).or(BigIntegers.of(b)).toLong() shouldBe (a or b)
        }
    }

    test("bitwise xor matches Long") {
        checkAll(Arb.long(), Arb.long()) { a, b ->
            BigIntegers.of(a).xor(BigIntegers.of(b)).toLong() shouldBe (a xor b)
        }
    }

    test("bitwise not matches Long") {
        checkAll(Arb.long()) { a ->
            BigIntegers.of(a).not().toLong() shouldBe a.inv()
        }
    }

    test("shiftLeft matches Long for small shifts") {
        checkAll(Arb.long(-1_000_000L..1_000_000L), Arb.int(0..30)) { a, n ->
            BigIntegers.of(a).shiftLeft(n).toLong() shouldBe (a shl n)
        }
    }

    test("shiftRight matches Long") {
        checkAll(Arb.long(), Arb.int(0..62)) { a, n ->
            BigIntegers.of(a).shiftRight(n).toLong() shouldBe (a shr n)
        }
    }

    test("inc matches Long") {
        checkAll(Arb.long().filter { it != Long.MAX_VALUE }) { a ->
            var bi = BigIntegers.of(a)
            bi++
            bi.toLong() shouldBe (a + 1)
        }
    }

    test("dec matches Long") {
        checkAll(Arb.long().filter { it != Long.MIN_VALUE }) { a ->
            var bi = BigIntegers.of(a)
            bi--
            bi.toLong() shouldBe (a - 1)
        }
    }

    test("divideAndRemainder matches Long") {
        checkAll(Arb.long(), Arb.long().filter { it != 0L }) { a, b ->
            if (!(a == Long.MIN_VALUE && b == -1L)) {
                val result = BigIntegers.of(a).divideAndRemainder(BigIntegers.of(b))
                result[0].toLong() shouldBe (a / b)
                result[1].toLong() shouldBe (a % b)
            }
        }
    }
})

/** Helper to get the sign of an int, since Integer.signum is JVM-only */
private object Integer {
    fun signum(value: Int): Int = when {
        value > 0 -> 1
        value < 0 -> -1
        else -> 0
    }
}
