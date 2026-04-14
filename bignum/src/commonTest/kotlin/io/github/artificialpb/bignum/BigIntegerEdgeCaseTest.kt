package io.github.artificialpb.bignum

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Edge case tests that cross-verify native BigInteger against known-correct values.
 * These target areas where LibTomMath's sign-magnitude representation could diverge
 * from Java's two's complement semantics.
 */

// -- Bitwise operations on negative numbers --

data class BitwiseBinaryCase(val a: String, val b: String, val expected: String, val op: String) {
    override fun toString() = "$a $op $b = $expected"
}

data class BitwiseUnaryCase(val input: String, val bit: Int, val expected: String, val op: String) {
    override fun toString() = "$input.$op($bit) = $expected"
}

class NegativeBitwiseEdgeCaseTest : FunSpec({

    context("and with negative operands") {
        withData(
            BitwiseBinaryCase("-1", "255", "255", "and"),          // all 1s & 0xFF = 0xFF
            BitwiseBinaryCase("-1", "-1", "-1", "and"),
            BitwiseBinaryCase("-256", "255", "0", "and"),          // 0xFFFFFF00 & 0xFF = 0
            BitwiseBinaryCase("-2", "3", "2", "and"),              // ...1110 & 0011 = 0010
            BitwiseBinaryCase("-128", "-128", "-128", "and"),
            BitwiseBinaryCase("-1", "0", "0", "and"),
            BitwiseBinaryCase("-4", "-4", "-4", "and"),
            BitwiseBinaryCase("-3", "5", "5", "and"),              // ...11111101 & 00000101 = 00000101
        ) { (a, b, expected, _) ->
            BigInteger(a).and(BigInteger(b)) shouldBe BigInteger(expected)
        }
    }

    context("or with negative operands") {
        withData(
            BitwiseBinaryCase("-1", "0", "-1", "or"),
            BitwiseBinaryCase("-1", "255", "-1", "or"),
            BitwiseBinaryCase("-256", "255", "-1", "or"),          // 0xFFFFFF00 | 0xFF = -1
            BitwiseBinaryCase("-2", "1", "-1", "or"),              // ...1110 | 0001 = ...1111
            BitwiseBinaryCase("-128", "127", "-1", "or"),
            BitwiseBinaryCase("-4", "3", "-1", "or"),              // ...11111100 | 00000011 = -1
        ) { (a, b, expected, _) ->
            BigInteger(a).or(BigInteger(b)) shouldBe BigInteger(expected)
        }
    }

    context("xor with negative operands") {
        withData(
            BitwiseBinaryCase("-1", "0", "-1", "xor"),
            BitwiseBinaryCase("-1", "-1", "0", "xor"),
            BitwiseBinaryCase("-1", "255", "-256", "xor"),         // all 1s ^ 0xFF = ...1100000000
            BitwiseBinaryCase("-256", "255", "-1", "xor"),
            BitwiseBinaryCase("-2", "1", "-1", "xor"),
            BitwiseBinaryCase("-128", "-1", "127", "xor"),
        ) { (a, b, expected, _) ->
            BigInteger(a).xor(BigInteger(b)) shouldBe BigInteger(expected)
        }
    }

    context("andNot with negative operands") {
        withData(
            BitwiseBinaryCase("255", "-1", "0", "andNot"),         // 0xFF & ~(-1) = 0xFF & 0 = 0
            BitwiseBinaryCase("-1", "0", "-1", "andNot"),          // -1 & ~0 = -1 & -1 = -1
            BitwiseBinaryCase("-1", "255", "-256", "andNot"),      // -1 & ~0xFF = ...11100000000
            BitwiseBinaryCase("-1", "-256", "255", "andNot"),      // -1 & ~(-256) = -1 & 0xFF = 255
            BitwiseBinaryCase("0", "-1", "0", "andNot"),
        ) { (a, b, expected, _) ->
            BigInteger(a).andNot(BigInteger(b)) shouldBe BigInteger(expected)
        }
    }

    context("flipBit on negative numbers") {
        withData(
            BitwiseUnaryCase("-1", 0, "-2", "flipBit"),    // ...1111 -> ...1110
            BitwiseUnaryCase("-1", 7, "-129", "flipBit"),  // flip bit 7: ...11111111 -> ...01111111 = -(128+1)
            BitwiseUnaryCase("-2", 0, "-1", "flipBit"),    // ...1110 -> ...1111
            BitwiseUnaryCase("-128", 7, "-256", "flipBit"),
        ) { (input, bit, expected, _) ->
            BigInteger(input).flipBit(bit) shouldBe BigInteger(expected)
        }
    }

    context("setBit on negative numbers") {
        withData(
            BitwiseUnaryCase("-2", 0, "-1", "setBit"),     // ...1110 -> ...1111
            BitwiseUnaryCase("-1", 0, "-1", "setBit"),     // already set
            BitwiseUnaryCase("-129", 7, "-1", "setBit"),   // ...01111111 -> ...11111111
            BitwiseUnaryCase("-256", 0, "-255", "setBit"),
        ) { (input, bit, expected, _) ->
            BigInteger(input).setBit(bit) shouldBe BigInteger(expected)
        }
    }

    context("clearBit on negative numbers") {
        withData(
            BitwiseUnaryCase("-1", 0, "-2", "clearBit"),    // ...1111 -> ...1110
            BitwiseUnaryCase("-2", 0, "-2", "clearBit"),    // already clear
            BitwiseUnaryCase("-1", 7, "-129", "clearBit"),  // ...11111111 -> ...01111111
            BitwiseUnaryCase("-255", 0, "-256", "clearBit"),
        ) { (input, bit, expected, _) ->
            BigInteger(input).clearBit(bit) shouldBe BigInteger(expected)
        }
    }

    context("testBit on negative numbers — exhaustive") {
        // -128 in two's complement = ...11111111 10000000
        test("-128: bits 0-6 are 0, bit 7 is 1, bits 8+ are 1") {
            val v = BigInteger("-128")
            for (i in 0..6) v.testBit(i) shouldBe false
            v.testBit(7) shouldBe true
            for (i in 8..20) v.testBit(i) shouldBe true
        }

        // -127 in two's complement = ...11111111 10000001
        test("-127: bit 0 is 1, bits 1-6 are 0, bit 7 is 1, bits 8+ are 1") {
            val v = BigInteger("-127")
            v.testBit(0) shouldBe true
            for (i in 1..6) v.testBit(i) shouldBe false
            v.testBit(7) shouldBe true
            for (i in 8..20) v.testBit(i) shouldBe true
        }

        // -256 in two's complement = ...11111111 00000000
        test("-256: bits 0-7 are 0, bit 8 is 1, bits 9+ are 1") {
            val v = BigInteger("-256")
            for (i in 0..7) v.testBit(i) shouldBe false
            v.testBit(8) shouldBe true
            for (i in 9..20) v.testBit(i) shouldBe true
        }
    }
})

// -- Shift operations on negative numbers --

class NegativeShiftEdgeCaseTest : FunSpec({

    context("shiftRight of negative numbers (arithmetic shift)") {
        withData(
            nameFn = { "${it.first} >> ${it.second} = ${it.third}" },
            Triple("-1", 1, "-1"),       // arithmetic: -1 >> n = -1 always
            Triple("-1", 100, "-1"),
            Triple("-2", 1, "-1"),       // ...1110 >> 1 = ...1111
            Triple("-4", 1, "-2"),       // ...11100 >> 1 = ...1110
            Triple("-4", 2, "-1"),
            Triple("-128", 7, "-1"),
            Triple("-129", 1, "-65"),    // ...01111111 >> 1 = ...0111111(1) -> ...10111111(1)
            Triple("-256", 8, "-1"),
            Triple("-257", 1, "-129"),
            Triple("-1024", 10, "-1"),
        ) { (input, n, expected) ->
            BigInteger(input).shiftRight(n) shouldBe BigInteger(expected)
        }
    }

    context("shiftLeft of negative numbers") {
        withData(
            nameFn = { "${it.first} << ${it.second} = ${it.third}" },
            Triple("-1", 1, "-2"),
            Triple("-1", 8, "-256"),
            Triple("-2", 1, "-4"),
            Triple("-128", 1, "-256"),
        ) { (input, n, expected) ->
            BigInteger(input).shiftLeft(n) shouldBe BigInteger(expected)
        }
    }

    test("shiftLeft(Int.MIN_VALUE) — arithmetic right shift by 2^31") {
        // Positive/zero → 0
        BigIntegers.ZERO.shiftLeft(Int.MIN_VALUE) shouldBe BigIntegers.ZERO
        BigIntegers.ONE.shiftLeft(Int.MIN_VALUE) shouldBe BigIntegers.ZERO
        BigInteger("255").shiftLeft(Int.MIN_VALUE) shouldBe BigIntegers.ZERO
        // Negative → -1
        BigInteger("-1").shiftLeft(Int.MIN_VALUE) shouldBe BigInteger("-1")
        BigInteger("-2").shiftLeft(Int.MIN_VALUE) shouldBe BigInteger("-1")
        BigInteger("-255").shiftLeft(Int.MIN_VALUE) shouldBe BigInteger("-1")
    }

    test("shiftRight(Int.MIN_VALUE) — shiftLeft by 2^31") {
        // Zero is fine
        BigIntegers.ZERO.shiftRight(Int.MIN_VALUE) shouldBe BigIntegers.ZERO
        // Nonzero throws
        shouldThrow<ArithmeticException> {
            BigIntegers.ONE.shiftRight(Int.MIN_VALUE)
        }
        shouldThrow<ArithmeticException> {
            BigInteger("-1").shiftRight(Int.MIN_VALUE)
        }
    }
})

// -- Two's complement byte array edge cases --

class ByteArrayEdgeCaseTest : FunSpec({

    context("toByteArray/fromByteArray round-trip at power-of-two boundaries") {
        withData(
            "127", "-128",               // 1-byte boundary
            "128", "-129",               // crosses to 2 bytes
            "255", "-256",
            "256", "-257",
            "32767", "-32768",           // 2-byte boundary
            "32768", "-32769",           // crosses to 3 bytes
            "65535", "-65536",
            "8388607", "-8388608",       // 3-byte boundary
            "8388608", "-8388609",
            "2147483647", "-2147483648", // 4-byte boundary (Int.MAX/MIN)
        ) { value ->
            val original = BigInteger(value)
            val bytes = original.toByteArray()
            BigInteger(bytes) shouldBe BigInteger(value)
        }
    }

    context("toByteArray produces minimal representation") {
        // Positive: no unnecessary leading 0x00 bytes (except when high bit is set)
        test("positive values have minimal byte length") {
            BigInteger("1").toByteArray().size shouldBeExactly 1        // [0x01]
            BigInteger("127").toByteArray().size shouldBeExactly 1      // [0x7F]
            BigInteger("128").toByteArray().size shouldBeExactly 2      // [0x00, 0x80]
            BigInteger("255").toByteArray().size shouldBeExactly 2      // [0x00, 0xFF]
            BigInteger("256").toByteArray().size shouldBeExactly 2      // [0x01, 0x00]
            BigInteger("32767").toByteArray().size shouldBeExactly 2    // [0x7F, 0xFF]
            BigInteger("32768").toByteArray().size shouldBeExactly 3    // [0x00, 0x80, 0x00]
        }

        // Negative: no unnecessary leading 0xFF bytes (except when high bit is clear)
        test("negative values have minimal byte length") {
            BigInteger("-1").toByteArray().size shouldBeExactly 1       // [0xFF]
            BigInteger("-128").toByteArray().size shouldBeExactly 1     // [0x80]
            BigInteger("-129").toByteArray().size shouldBeExactly 2     // [0xFF, 0x7F]
            BigInteger("-256").toByteArray().size shouldBeExactly 2     // [0xFF, 0x00]
            BigInteger("-32768").toByteArray().size shouldBeExactly 2   // [0x80, 0x00]
            BigInteger("-32769").toByteArray().size shouldBeExactly 3   // [0xFF, 0x7F, 0xFF]
        }
    }

    test("fromByteArray with single byte 0x80 is -128, not +128") {
        BigInteger(byteArrayOf(0x80.toByte())) shouldBe BigInteger("-128")
    }

    test("fromByteArray all-ones bytes") {
        BigInteger(byteArrayOf(0xFF.toByte())) shouldBe BigInteger("-1")
        BigInteger(byteArrayOf(0xFF.toByte(), 0xFF.toByte())) shouldBe BigInteger("-1")
        BigInteger(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) shouldBe BigInteger("-1")
    }

    test("constructor(bytes, off, len) slices correctly") {
        val bytes = byteArrayOf(0x00, 0x01, 0x00, 0xFF.toByte())
        BigInteger(bytes, 1, 2) shouldBe BigInteger("256")  // [0x01, 0x00]
        BigInteger(bytes, 0, 1) shouldBe BigIntegers.ZERO    // [0x00]
        BigInteger(bytes, 3, 1) shouldBe BigInteger("-1")   // [0xFF]
    }
})

// -- bitLength for negative numbers --

class BitLengthEdgeCaseTest : FunSpec({

    // Java's bitLength() returns the number of bits in the minimal two's complement
    // representation, excluding the sign bit.
    // For negative numbers: bitLength(-n) == bitLength(n-1) for n > 0

    context("bitLength cross-check with Long") {
        withData(
            nameFn = { "bitLength($it)" },
            "0", "1", "-1", "2", "-2",
            "127", "-127", "128", "-128",
            "255", "-255", "256", "-256",
            "32767", "-32767", "32768", "-32768",
            "65535", "-65535", "65536", "-65536",
        ) { value ->
            // Verify against the formula: for negative, bitLength = bitLength(|value| - 1)
            val bi = BigInteger(value)
            if (bi.signum() < 0) {
                // bitLength of negative = bitLength of (|value| - 1)
                val absMinusOne = bi.abs() - BigIntegers.ONE
                bi.bitLength() shouldBeExactly absMinusOne.bitLength()
            }
        }
    }
})

// -- equals/hashCode contract --

class EqualsHashCodeTest : FunSpec({

    test("equal values from different constructors have same hashCode") {
        val a = BigInteger("12345678901234567890")
        val b = BigInteger("12345678901234567890")
        a shouldBe b
        a.hashCode() shouldBeExactly b.hashCode()
    }

    test("equal values from different factories have same hashCode") {
        val a = BigIntegers.of(42L)
        val b = BigInteger("42")
        a shouldBe b
        a.hashCode() shouldBeExactly b.hashCode()
    }

    test("equal values from byte array have same hashCode") {
        val a = BigInteger("256")
        val b = BigInteger(byteArrayOf(0x01, 0x00))
        a shouldBe b
        a.hashCode() shouldBeExactly b.hashCode()
    }

    test("hashCode is consistent with equals for random values") {
        checkAll(Arb.long()) { n ->
            val a = BigIntegers.of(n)
            val b = BigInteger(n.toString())
            a shouldBe b
            a.hashCode() shouldBeExactly b.hashCode()
        }
    }

    test("hashCode matches JVM reference values") {
        // These values were verified against java.math.BigInteger.hashCode()
        BigIntegers.ZERO.hashCode() shouldBeExactly 0
        BigIntegers.ONE.hashCode() shouldBeExactly 1
        BigIntegers.TEN.hashCode() shouldBeExactly 10
        BigInteger("-1").hashCode() shouldBeExactly -1
        BigInteger("256").hashCode() shouldBeExactly 256
        BigInteger("2147483647").hashCode() shouldBeExactly 2147483647
        BigInteger("-2147483648").hashCode() shouldBeExactly -2147483648
        // 2^32: magnitude is [1, 0, 0, 0, 0] as bytes → int[] = {1, 0} → 31*1+0 = 31
        BigIntegers.ONE.shiftLeft(32).hashCode() shouldBeExactly 31
        // 2^32+1: int[] = {1, 1} → 31*1+1 = 32
        (BigIntegers.ONE.shiftLeft(32) + BigIntegers.ONE).hashCode() shouldBeExactly 32
    }

    test("hashCode matches JVM for 512-bit numbers") {
        // 2^512 - 1 (all ones, 512 bits)
        val allOnes = BigIntegers.TWO.pow(512) - BigIntegers.ONE
        allOnes.hashCode() shouldBeExactly 813883136

        // 2^512
        val pow512 = BigIntegers.TWO.pow(512)
        pow512.hashCode() shouldBeExactly 1353309697

        // Large 512-bit prime
        val largePrime = BigInteger("13407807929942597099574024998205846127479365820592393377723561443721764030073546976801874298166903427690031858186486050853753882811946569946433649006084171")
        largePrime.hashCode() shouldBeExactly 1353309772

        // Negation of large prime
        (-largePrime).hashCode() shouldBeExactly -1353309772

        // 512-bit hex pattern: 0x123456789ABCDEF0 repeated
        val hexPattern = BigInteger("123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0", 16)
        hexPattern.hashCode() shouldBeExactly -371264576
    }
})

// -- Cross-verify bitwise ops against Long for negative values --

class NegativeBitwiseVsLongTest : FunSpec({

    test("testBit matches Long for full range of bit positions") {
        checkAll(Arb.long()) { n ->
            val bi = BigIntegers.of(n)
            for (bit in 0..62) {
                bi.testBit(bit) shouldBe ((n shr bit) and 1L == 1L)
            }
        }
    }

    test("getLowestSetBit matches Long") {
        checkAll(Arb.long()) { n ->
            val bi = BigIntegers.of(n)
            if (n == 0L) {
                bi.getLowestSetBit() shouldBeExactly -1
            } else {
                // Find lowest set bit of n via Long
                var expected = 0
                var v = n
                while (v and 1L == 0L) {
                    expected++
                    v = v shr 1
                }
                bi.getLowestSetBit() shouldBeExactly expected
            }
        }
    }

    test("bitCount matches Long.countOneBits for positive") {
        checkAll(Arb.long(0L..Long.MAX_VALUE)) { n ->
            val bi = BigIntegers.of(n)
            bi.bitCount() shouldBeExactly n.countOneBits()
        }
    }

    test("flipBit matches Long") {
        checkAll(Arb.long(), Arb.long().map { (it.mod(63L)).toInt() }) { n, bit ->
            val bi = BigIntegers.of(n)
            val expected = n xor (1L shl bit)
            bi.flipBit(bit).toLong() shouldBe expected
        }
    }

    test("setBit matches Long") {
        checkAll(Arb.long(), Arb.long().map { (it.mod(63L)).toInt() }) { n, bit ->
            val bi = BigIntegers.of(n)
            val expected = n or (1L shl bit)
            bi.setBit(bit).toLong() shouldBe expected
        }
    }

    test("clearBit matches Long") {
        checkAll(Arb.long(), Arb.long().map { (it.mod(63L)).toInt() }) { n, bit ->
            val bi = BigIntegers.of(n)
            val expected = n and (1L shl bit).inv()
            bi.clearBit(bit).toLong() shouldBe expected
        }
    }
})

// -- Error handling: divide-by-zero, invalid arguments --

class ErrorHandlingTest : FunSpec({

    test("div by zero throws ArithmeticException") {
        val a = BigInteger("42")
        shouldThrow<ArithmeticException> {
            a / BigIntegers.ZERO
        }
    }

    test("rem by zero throws ArithmeticException") {
        val a = BigInteger("42")
        shouldThrow<ArithmeticException> {
            a % BigIntegers.ZERO
        }
    }

    test("divideAndRemainder by zero throws ArithmeticException") {
        val a = BigInteger("42")
        shouldThrow<ArithmeticException> {
            a.divideAndRemainder(BigIntegers.ZERO)
        }
    }

    test("negative bit address throws ArithmeticException") {
        val a = BigInteger("42")
        shouldThrow<ArithmeticException> { a.testBit(-1) }
        shouldThrow<ArithmeticException> { a.setBit(-1) }
        shouldThrow<ArithmeticException> { a.clearBit(-1) }
        shouldThrow<ArithmeticException> { a.flipBit(-1) }
    }

    test("invalid radix in constructor throws NumberFormatException") {
        shouldThrow<NumberFormatException> { BigInteger("10", 1) }
        shouldThrow<NumberFormatException> { BigInteger("10", 37) }
        shouldThrow<NumberFormatException> { BigInteger("10", 0) }
        shouldThrow<NumberFormatException> { BigInteger("10", -1) }
    }

    test("invalid radix in toString falls back to decimal (JVM semantics)") {
        val a = BigInteger("42")
        a.toString(1) shouldBe "42"
        a.toString(37) shouldBe "42"
    }

    test("constructor(bytes, off, len) validates bounds") {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        shouldThrow<IndexOutOfBoundsException> { BigInteger(bytes, -1, 2) }
        shouldThrow<IndexOutOfBoundsException> { BigInteger(bytes, 0, 4) }
        shouldThrow<IndexOutOfBoundsException> { BigInteger(bytes, 2, 2) }
    }

    test("constructor(bytes, off, len) validates bounds when off + len overflows Int") {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        shouldThrow<IndexOutOfBoundsException> { BigInteger(bytes, Int.MAX_VALUE, Int.MAX_VALUE) }
        shouldThrow<IndexOutOfBoundsException> { BigInteger(bytes, Int.MAX_VALUE, 2) }
        shouldThrow<IndexOutOfBoundsException> { BigInteger(bytes, 1, Int.MAX_VALUE) }
    }

    test("constructor(bytes, off, len) with len=0 returns zero for valid offsets") {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        BigInteger(bytes, 0, 0) shouldBe BigIntegers.ZERO
        BigInteger(bytes, 1, 0) shouldBe BigIntegers.ZERO
        BigInteger(bytes, 2, 0) shouldBe BigIntegers.ZERO
    }

    test("constructor(bytes, off, len) with len=0 at off==size throws IndexOutOfBoundsException") {
        // JVM throws ArrayIndexOutOfBoundsException (subclass of IndexOutOfBoundsException)
        // Kotlin/Native deprecates ArrayIndexOutOfBoundsException(String), so we assert the supertype
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        shouldThrow<IndexOutOfBoundsException> {
            BigInteger(bytes, 3, 0)
        }
    }

    test("constructor(bytes, off, len) with empty array and len=0 throws") {
        shouldThrow<NumberFormatException> {
            BigInteger(byteArrayOf(), 0, 0)
        }
    }

    test("malformed leading plus throws NumberFormatException with JVM-matching message") {
        shouldThrow<NumberFormatException> { BigInteger("+") }.message shouldBe "Zero length BigInteger"
        shouldThrow<NumberFormatException> { BigInteger("+-1") }.message shouldBe "Illegal embedded sign character"
        shouldThrow<NumberFormatException> { BigInteger("++1") }.message shouldBe "Illegal embedded sign character"
        shouldThrow<NumberFormatException> { BigInteger("+-FF", 16) }.message shouldBe "Illegal embedded sign character"
    }

    test("malformed leading minus throws NumberFormatException with JVM-matching message") {
        shouldThrow<NumberFormatException> { BigInteger("-") }.message shouldBe "Zero length BigInteger"
        shouldThrow<NumberFormatException> { BigInteger("--1") }.message shouldBe "Illegal embedded sign character"
        shouldThrow<NumberFormatException> { BigInteger("-+1") }.message shouldBe "Illegal embedded sign character"
        shouldThrow<NumberFormatException> { BigInteger("--FF", 16) }.message shouldBe "Illegal embedded sign character"
        shouldThrow<NumberFormatException> { BigInteger("-+FF", 16) }.message shouldBe "Illegal embedded sign character"
    }

    test("invalid digits throw NumberFormatException with JVM-matching message") {
        shouldThrow<NumberFormatException> { BigInteger("12a") }.message shouldBe "For input string: \"12a\""
        shouldThrow<NumberFormatException> { BigInteger("abc") }.message shouldBe "For input string: \"abc\""
        shouldThrow<NumberFormatException> { BigInteger("hello") }.message shouldBe "For input string: \"hello\""
        shouldThrow<NumberFormatException> { BigInteger("2", 2) }.message shouldBe "For input string: \"2\" under radix 2"
        shouldThrow<NumberFormatException> { BigInteger("1G", 16) }.message shouldBe "For input string: \"1G\" under radix 16"
    }

    test("modInverse with non-positive modulus throws ArithmeticException") {
        shouldThrow<ArithmeticException> {
            BigInteger("3").modInverse(BigIntegers.ZERO)
        }
        shouldThrow<ArithmeticException> {
            BigInteger("3").modInverse(BigInteger("-5"))
        }
    }

    test("empty byte array throws") {
        shouldThrow<NumberFormatException> {
            BigInteger(byteArrayOf())
        }
    }

    test("nextProbablePrime on negative throws") {
        shouldThrow<ArithmeticException> {
            BigInteger("-1").nextProbablePrime()
        }
    }

    test("isProbablePrime with certainty <= 0 returns true (JVM semantics)") {
        BigInteger("4").isProbablePrime(0) shouldBe true
        BigInteger("4").isProbablePrime(-1) shouldBe true
        BigInteger("100").isProbablePrime(0) shouldBe true
    }

    test("modPow with non-positive modulus throws ArithmeticException") {
        shouldThrow<ArithmeticException> {
            BigInteger("2").modPow(BigInteger("3"), BigIntegers.ZERO)
        }
        shouldThrow<ArithmeticException> {
            BigInteger("2").modPow(BigInteger("3"), BigInteger("-5"))
        }
    }

    test("modPow with negative exponent uses modular inverse") {
        // 3^-1 mod 7 = 5 (since 3*5=15≡1 mod 7)
        BigInteger("3").modPow(BigInteger("-1"), BigInteger("7")) shouldBe BigInteger("5")
        // 3^-2 mod 7 = 5^2 mod 7 = 25 mod 7 = 4
        BigInteger("3").modPow(BigInteger("-2"), BigInteger("7")) shouldBe BigInteger("4")
    }

    test("modPow with negative exponent throws when inverse does not exist") {
        // 2 has no inverse mod 4 (gcd(2,4)=2≠1)
        shouldThrow<ArithmeticException> {
            BigInteger("2").modPow(BigInteger("-1"), BigInteger("4"))
        }
    }

    test("modPow with modulus == 1 always returns 0") {
        BigInteger("2").modPow(BigInteger("10"), BigIntegers.ONE) shouldBe BigIntegers.ZERO
        BigInteger("2").modPow(BigInteger("-1"), BigIntegers.ONE) shouldBe BigIntegers.ZERO
        BigInteger("0").modPow(BigInteger("0"), BigIntegers.ONE) shouldBe BigIntegers.ZERO
    }

    test("sqrt of negative throws ArithmeticException") {
        shouldThrow<ArithmeticException> {
            BigInteger("-1").sqrt()
        }
    }

    test("pow with negative exponent throws ArithmeticException") {
        shouldThrow<ArithmeticException> {
            BigInteger("2").pow(-1)
        }
    }

    test("mod with zero modulus throws ArithmeticException") {
        shouldThrow<ArithmeticException> {
            BigInteger("7").mod(BigIntegers.ZERO)
        }
    }
})

// -- lcm sign semantics (JVM: preserves sign of (this/gcd)*other) --

class LcmSignTest : FunSpec({

    context("lcm matches JVM sign semantics") {
        withData(
            nameFn = { "lcm(${it.first}, ${it.second}) = ${it.third}" },
            Triple("2", "3", "6"),
            Triple("-2", "3", "-6"),
            Triple("2", "-3", "-6"),
            Triple("-2", "-3", "6"),
            Triple("12", "8", "24"),
            Triple("-12", "8", "-24"),
            Triple("0", "5", "0"),
            Triple("5", "0", "0"),
        ) { (a, b, expected) ->
            BigInteger(a).lcm(BigInteger(b)) shouldBe BigInteger(expected)
        }
    }
})

// -- mod vs rem semantics --

class ModVsRemEdgeCaseTest : FunSpec({

    test("mod always returns non-negative, rem preserves sign of dividend") {
        checkAll(Arb.long(), Arb.long(1L..Long.MAX_VALUE)) { a, b ->
            val bi = BigIntegers.of(a)
            val bm = BigIntegers.of(b)

            val modResult = bi.mod(bm)
            val remResult = bi % bm

            // mod is always in [0, b)
            (modResult.signum() >= 0) shouldBe true
            (modResult.compareTo(bm) < 0) shouldBe true

            // rem has same sign as dividend (or is zero)
            if (remResult.signum() != 0) {
                (remResult.signum() == bi.signum()) shouldBe true
            }
        }
    }
})
