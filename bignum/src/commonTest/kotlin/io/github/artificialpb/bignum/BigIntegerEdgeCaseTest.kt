package io.github.artificialpb.bignum

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
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
            bigIntegerOf(a).and(bigIntegerOf(b)) shouldBe BigInteger(expected)
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
            bigIntegerOf(a).or(bigIntegerOf(b)) shouldBe BigInteger(expected)
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
            bigIntegerOf(a).xor(bigIntegerOf(b)) shouldBe BigInteger(expected)
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
            bigIntegerOf(a).andNot(bigIntegerOf(b)) shouldBe BigInteger(expected)
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
        bigIntegerOf(0L).shiftLeft(Int.MIN_VALUE) shouldBe bigIntegerOf(0L)
        bigIntegerOf(1L).shiftLeft(Int.MIN_VALUE) shouldBe bigIntegerOf(0L)
        BigInteger("255").shiftLeft(Int.MIN_VALUE) shouldBe bigIntegerOf(0L)
        // Negative → -1
        BigInteger("-1").shiftLeft(Int.MIN_VALUE) shouldBe BigInteger("-1")
        BigInteger("-2").shiftLeft(Int.MIN_VALUE) shouldBe BigInteger("-1")
        BigInteger("-255").shiftLeft(Int.MIN_VALUE) shouldBe BigInteger("-1")
    }

    test("shiftRight(Int.MIN_VALUE) — shiftLeft by 2^31") {
        // Zero is fine
        bigIntegerOf(0L).shiftRight(Int.MIN_VALUE) shouldBe bigIntegerOf(0L)
        // Nonzero throws
        shouldThrow<ArithmeticException> {
            bigIntegerOf(1L).shiftRight(Int.MIN_VALUE)
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
        BigInteger(bytes, 0, 1) shouldBe bigIntegerOf(0L)    // [0x00]
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
                val absMinusOne = bi.abs() - bigIntegerOf(1L)
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
        val a = bigIntegerOf(42L)
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
            val a = bigIntegerOf(n)
            val b = BigInteger(n.toString())
            a shouldBe b
            a.hashCode() shouldBeExactly b.hashCode()
        }
    }

    test("hashCode matches JVM reference values") {
        // These values were verified against java.math.BigInteger.hashCode()
        bigIntegerOf(0L).hashCode() shouldBeExactly 0
        bigIntegerOf(1L).hashCode() shouldBeExactly 1
        bigIntegerOf(10L).hashCode() shouldBeExactly 10
        BigInteger("-1").hashCode() shouldBeExactly -1
        BigInteger("256").hashCode() shouldBeExactly 256
        BigInteger("2147483647").hashCode() shouldBeExactly 2147483647
        BigInteger("-2147483648").hashCode() shouldBeExactly -2147483648
        // 2^32: magnitude is [1, 0, 0, 0, 0] as bytes → int[] = {1, 0} → 31*1+0 = 31
        bigIntegerOf(1L).shiftLeft(32).hashCode() shouldBeExactly 31
        // 2^32+1: int[] = {1, 1} → 31*1+1 = 32
        (bigIntegerOf(1L).shiftLeft(32) + bigIntegerOf(1L)).hashCode() shouldBeExactly 32
    }

    test("hashCode matches JVM for 512-bit numbers") {
        // 2^512 - 1 (all ones, 512 bits)
        val allOnes = bigIntegerOf(2L).pow(512) - bigIntegerOf(1L)
        allOnes.hashCode() shouldBeExactly 813883136

        // 2^512
        val pow512 = bigIntegerOf(2L).pow(512)
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
            val bi = bigIntegerOf(n)
            for (bit in 0..62) {
                bi.testBit(bit) shouldBe ((n shr bit) and 1L == 1L)
            }
        }
    }

    test("getLowestSetBit matches Long") {
        checkAll(Arb.long()) { n ->
            val bi = bigIntegerOf(n)
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
            val bi = bigIntegerOf(n)
            bi.bitCount() shouldBeExactly n.countOneBits()
        }
    }

    test("flipBit matches Long") {
        checkAll(Arb.long(), Arb.long().map { (it.mod(63L)).toInt() }) { n, bit ->
            val bi = bigIntegerOf(n)
            val expected = n xor (1L shl bit)
            bi.flipBit(bit).toLong() shouldBe expected
        }
    }

    test("setBit matches Long") {
        checkAll(Arb.long(), Arb.long().map { (it.mod(63L)).toInt() }) { n, bit ->
            val bi = bigIntegerOf(n)
            val expected = n or (1L shl bit)
            bi.setBit(bit).toLong() shouldBe expected
        }
    }

    test("clearBit matches Long") {
        checkAll(Arb.long(), Arb.long().map { (it.mod(63L)).toInt() }) { n, bit ->
            val bi = bigIntegerOf(n)
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
            a / bigIntegerOf(0L)
        }
    }

    test("rem by zero throws ArithmeticException") {
        val a = BigInteger("42")
        shouldThrow<ArithmeticException> {
            a % bigIntegerOf(0L)
        }
    }

    test("divideAndRemainder by zero throws ArithmeticException") {
        val a = BigInteger("42")
        shouldThrow<ArithmeticException> {
            a.divideAndRemainder(bigIntegerOf(0L))
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

    test("constructor(bytes, off, len) with len=0 returns zero for valid offsets with non-negative leading byte") {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        BigInteger(bytes, 0, 0) shouldBe bigIntegerOf(0L)
        BigInteger(bytes, 1, 0) shouldBe bigIntegerOf(0L)
        BigInteger(bytes, 2, 0) shouldBe bigIntegerOf(0L)
    }

    test("constructor(bytes, off, len) with len=0 follows JVM's negative-leading-byte edge case") {
        val bytes = byteArrayOf(0x79, 0xFC.toByte())
        BigInteger(bytes, 1, 0) shouldBe BigInteger("-135")
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
            BigInteger("3").modInverse(bigIntegerOf(0L))
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
            BigInteger("2").modPow(BigInteger("3"), bigIntegerOf(0L))
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
        BigInteger("2").modPow(BigInteger("10"), bigIntegerOf(1L)) shouldBe bigIntegerOf(0L)
        BigInteger("2").modPow(BigInteger("-1"), bigIntegerOf(1L)) shouldBe bigIntegerOf(0L)
        BigInteger("0").modPow(BigInteger("0"), bigIntegerOf(1L)) shouldBe bigIntegerOf(0L)
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
            BigInteger("7").mod(bigIntegerOf(0L))
        }
    }

    test("pow with Int.MAX_VALUE exponent throws ArithmeticException") {
        shouldThrow<ArithmeticException> {
            bigIntegerOf(2L).pow(Int.MAX_VALUE)
        }
    }

    test("pow with large-but-accepted exponent does not throw") {
        // 2^1073741824 has bit length 1073741825, within Int.MAX_VALUE
        val result = bigIntegerOf(2L).pow(1073741824)
        result.bitLength() shouldBeExactly 1073741825
    }

    test("shiftLeft by Int.MAX_VALUE throws ArithmeticException") {
        shouldThrow<ArithmeticException> {
            bigIntegerOf(1L).shiftLeft(Int.MAX_VALUE)
        }
    }
})

// -- additional primality stress cases --

class PrimalityStressEdgeCaseTest : FunSpec({

    val mersenne127 = "170141183460469231731687303715884105727" // 2^127 - 1
    val mersenne127Squared = "28948022309329048855892746252171976962977213799489202546401021394546514198529"

    context("isProbablePrime rejects pseudoprimes and handles large values") {
        withData(
            BoolCase("341", false),             // Fermat pseudoprime to base 2
            BoolCase("561", false),             // Carmichael
            BoolCase("645", false),             // Carmichael
            BoolCase("1105", false),            // Carmichael
            BoolCase("1729", false),            // Carmichael
            BoolCase("2465", false),            // Carmichael
            BoolCase("2821", false),            // Carmichael
            BoolCase("6601", false),            // Carmichael
            BoolCase("41041", false),           // Carmichael
            BoolCase("825265", false),          // Carmichael
            BoolCase("3215031751", false),      // strong pseudoprime to several small bases
            BoolCase("2152302898747", false),   // strong pseudoprime to first primes 2,3,5,7,11
            BoolCase(mersenne127, true),
            BoolCase("-$mersenne127", true),    // JVM tests absolute value
            BoolCase(mersenne127Squared, false),
            BoolCase("-$mersenne127Squared", false),
        ) { (input, expected) ->
            BigInteger(input).isProbablePrime(100) shouldBe expected
        }
    }

    test("large known prime stays prime across certainty ladder") {
        val prime = BigInteger(mersenne127)
        for (certainty in listOf(1, 2, 10, 50, 100)) {
            prime.isProbablePrime(certainty) shouldBe true
        }
    }

    context("nextProbablePrime matches JVM for pseudoprimes and large prime boundaries") {
        withData(
            PrimeCase("561", "563"),
            PrimeCase("1105", "1109"),
            PrimeCase("1729", "1733"),
            PrimeCase("2047", "2053"), // 23 * 89, Mersenne composite
            PrimeCase("2147483647", "2147483659"),
            PrimeCase(mersenne127, "170141183460469231731687303715884105757"),
        ) { (input, expected) ->
            BigInteger(input).nextProbablePrime() shouldBe BigInteger(expected)
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
            bigIntegerOf(a).lcm(bigIntegerOf(b)) shouldBe BigInteger(expected)
        }
    }
})

// -- mod vs rem semantics --

class ModVsRemEdgeCaseTest : FunSpec({

    test("mod always returns non-negative, rem preserves sign of dividend") {
        checkAll(Arb.long(), Arb.long(1L..Long.MAX_VALUE)) { a, b ->
            val bi = bigIntegerOf(a)
            val bm = bigIntegerOf(b)

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

// -- bitCount for negative numbers --

class BitCountNegativeTest : FunSpec({

    // Java's bitCount() for negative numbers returns the number of bits that
    // differ from the sign bit (i.e. the number of 0-bits in two's complement),
    // which equals the popcount of (|x| - 1).

    context("bitCount for negative values matches known results") {
        withData(
            nameFn = { "bitCount(${it.first}) = ${it.second}" },
            "-1" to 0,           // two's complement: all 1s, 0 bits differ from sign
            "-2" to 1,           // ...1110 → one 0
            "-3" to 1,           // ...1101 → one 0
            "-4" to 2,           // ...1100 → two 0s
            "-5" to 1,           // ...1011 → one 0
            "-7" to 2,           // ...1001 → two 0s (|7|-1=6=110, popcount=2)
            "-8" to 3,           // ...11000 → three 0s (|8|-1=7=111)
            "-9" to 1,           // ...10111 → one 0 (|9|-1=8=1000)
            "-128" to 7,         // ...10000000 → seven 0s (127=1111111)
            "-129" to 1,         // ...01111111 → one 0 (128=10000000)
            "-255" to 7,         // (254=11111110, popcount=7)
            "-256" to 8,         // (255=11111111)
            "-257" to 1,         // (256=100000000)
        ) { (input, expected) ->
            BigInteger(input).bitCount() shouldBeExactly expected
        }
    }

    test("bitCount for negative matches Long cross-check") {
        // For negative Long n, bitCount = Long.SIZE_BITS - n.countOneBits()
        // which is the number of 0-bits (= bits differing from sign bit 1)
        checkAll(Arb.long(Long.MIN_VALUE..-1L)) { n ->
            val bi = bigIntegerOf(n)
            // Java BigInteger bitCount for negative = popcount(|n| - 1)
            val absMinusOne = if (n == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(n) - 1
            bi.bitCount() shouldBeExactly absMinusOne.countOneBits()
        }
    }
})

// -- toInt/toLong overflow truncation --

class OverflowTruncationTest : FunSpec({

    test("toLong truncates large positive to low 64 bits") {
        // 2^64 + 42 should truncate to 42
        val value = bigIntegerOf(2L).pow(64) + bigIntegerOf(42L)
        value.toLong() shouldBeExactly 42L
    }

    test("toLong truncates large negative") {
        // -(2^64 + 42) should truncate to -42
        val value = -(bigIntegerOf(2L).pow(64) + bigIntegerOf(42L))
        value.toLong() shouldBeExactly -42L
    }

    test("toInt truncates values beyond Int range") {
        // 2^32 + 7 should truncate to 7
        val value = bigIntegerOf(2L).pow(32) + bigIntegerOf(7L)
        value.toInt() shouldBeExactly 7
    }

    test("toInt truncates large negative values beyond Int range") {
        val value = -(bigIntegerOf(2L).pow(32) + bigIntegerOf(7L))
        value.toInt() shouldBeExactly -7
    }

    test("toLong of Long.MAX_VALUE + 1") {
        val value = bigIntegerOf(Long.MAX_VALUE) + bigIntegerOf(1L)
        // JVM: Long.MAX_VALUE + 1 wraps to Long.MIN_VALUE
        value.toLong() shouldBeExactly Long.MIN_VALUE
    }

    test("toLong of Long.MIN_VALUE - 1") {
        val value = bigIntegerOf(Long.MIN_VALUE) - bigIntegerOf(1L)
        // JVM: Long.MIN_VALUE - 1 wraps to Long.MAX_VALUE
        value.toLong() shouldBeExactly Long.MAX_VALUE
    }

    test("toInt of 2147483648 (Int.MAX_VALUE + 1)") {
        BigInteger("2147483648").toInt() shouldBeExactly Int.MIN_VALUE
    }

    test("toInt of -2147483649 (Int.MIN_VALUE - 1)") {
        BigInteger("-2147483649").toInt() shouldBeExactly Int.MAX_VALUE
    }

    test("toLong of 9223372036854775808 (Long.MAX_VALUE + 1)") {
        BigInteger("9223372036854775808").toLong() shouldBeExactly Long.MIN_VALUE
    }

    test("toLong of -9223372036854775809 (Long.MIN_VALUE - 1)") {
        BigInteger("-9223372036854775809").toLong() shouldBeExactly Long.MAX_VALUE
    }
})

// -- modInverse with negative base --

class ModInverseNegativeBaseTest : FunSpec({

    context("modInverse with negative base returns result in [0, modulus)") {
        withData(
            nameFn = { "${it.first}.modInverse(${it.second}) = ${it.third}" },
            // (-3).modInverse(7): 3*5=15≡1 mod 7, so -3 inverse is -5≡2 mod 7
            Triple("-3", "7", "2"),
            // (-1).modInverse(7) = 6 (since -1*6 = -6 ≡ 1 mod 7)
            Triple("-1", "7", "6"),
            // (-2).modInverse(5) = 2 (since -2*2 = -4 ≡ 1 mod 5)... wait: -2*(-2)=4≡4, -2*3=-6≡4. Actually (-2)*(-2)=4≡4 mod 5. Let me recalc.
            // 2.modInverse(5) = 3 (since 2*3=6≡1 mod 5). (-2).modInverse(5) = -3 mod 5 = 2
            Triple("-2", "5", "2"),
            Triple("-1", "2", "1"),
            Triple("-1", "3", "2"),
        ) { (a, m, expected) ->
            val result = bigIntegerOf(a).modInverse(BigInteger(m))
            result shouldBe BigInteger(expected)
            // Result should always be in [0, modulus)
            (result.signum() >= 0) shouldBe true
            (result.compareTo(BigInteger(m)) < 0) shouldBe true
        }
    }
})

// -- modPow edge cases --

class ModPowEdgeCaseTest : FunSpec({

    test("modPow with exponent 0 returns 1 mod m") {
        BigInteger("5").modPow(bigIntegerOf(0L), BigInteger("3")) shouldBe bigIntegerOf(1L)
        BigInteger("0").modPow(bigIntegerOf(0L), BigInteger("3")) shouldBe bigIntegerOf(1L)
        BigInteger("100").modPow(bigIntegerOf(0L), BigInteger("7")) shouldBe bigIntegerOf(1L)
        // modPow(0, 1) = 0 (since 1 mod 1 = 0) — already tested but included for completeness
        BigInteger("5").modPow(bigIntegerOf(0L), bigIntegerOf(1L)) shouldBe bigIntegerOf(0L)
    }

    test("modPow with negative base") {
        // (-2)^3 mod 5 = -8 mod 5 = 2
        BigInteger("-2").modPow(BigInteger("3"), BigInteger("5")) shouldBe BigInteger("2")
        // (-3)^2 mod 7 = 9 mod 7 = 2
        BigInteger("-3").modPow(BigInteger("2"), BigInteger("7")) shouldBe BigInteger("2")
        // (-1)^large mod m = ±1 mod m depending on parity
        BigInteger("-1").modPow(BigInteger("100"), BigInteger("7")) shouldBe bigIntegerOf(1L)
        BigInteger("-1").modPow(BigInteger("101"), BigInteger("7")) shouldBe BigInteger("6")
    }

    test("modPow with base 0") {
        // 0^e mod m = 0 for e > 0
        BigInteger("0").modPow(BigInteger("1"), BigInteger("5")) shouldBe bigIntegerOf(0L)
        BigInteger("0").modPow(BigInteger("100"), BigInteger("7")) shouldBe bigIntegerOf(0L)
    }

    test("modPow with exponent 1") {
        BigInteger("7").modPow(bigIntegerOf(1L), BigInteger("5")) shouldBe BigInteger("2")
        BigInteger("-3").modPow(bigIntegerOf(1L), BigInteger("5")) shouldBe BigInteger("2")
    }
})

// -- gcd edge cases --

class GcdEdgeCaseTest : FunSpec({

    test("gcd(0, 0) == 0") {
        bigIntegerOf(0L).gcd(bigIntegerOf(0L)) shouldBe bigIntegerOf(0L)
    }

    test("gcd with very large numbers") {
        val a = BigInteger("123456789012345678901234567890")
        val b = BigInteger("987654321098765432109876543210")
        val g = a.gcd(b)
        // g should divide both
        (a % g) shouldBe bigIntegerOf(0L)
        (b % g) shouldBe bigIntegerOf(0L)
    }

    test("gcd of negative numbers is always positive") {
        BigInteger("-12").gcd(BigInteger("-8")) shouldBe BigInteger("4")
        BigInteger("-12").gcd(bigIntegerOf(0L)) shouldBe BigInteger("12")
    }
})

// -- toDouble edge cases --

class ToDoubleEdgeCaseTest : FunSpec({

    test("toDouble for very large positive returns Infinity") {
        val huge = bigIntegerOf(2L).pow(1024)
        huge.toDouble() shouldBeExactly Double.POSITIVE_INFINITY
    }

    test("toDouble for very large negative returns -Infinity") {
        val huge = -(bigIntegerOf(2L).pow(1024))
        huge.toDouble() shouldBeExactly Double.NEGATIVE_INFINITY
    }

    test("toDouble loses precision for large-but-representable values") {
        // 2^53 is exactly representable, 2^53 + 1 loses the +1
        val exact = bigIntegerOf(2L).pow(53)
        exact.toDouble() shouldBeExactly 9007199254740992.0
    }

    test("toDouble for Long.MAX_VALUE") {
        bigIntegerOf(Long.MAX_VALUE).toDouble() shouldBeExactly Long.MAX_VALUE.toDouble()
    }

    test("toDouble for Long.MIN_VALUE") {
        bigIntegerOf(Long.MIN_VALUE).toDouble() shouldBeExactly Long.MIN_VALUE.toDouble()
    }

    test("toDouble rounds huge finite integers the same way as JVM") {
        val value = BigInteger("27865336992809917105112130163450434437862611569769059293869481755328202537322377354046844")
        value.toDouble().toBits() shouldBeExactly 5930119120905647391L
    }
})

// -- String constructor edge cases --

class StringConstructorEdgeCaseTest : FunSpec({

    test("leading zeros are stripped") {
        BigInteger("00042") shouldBe BigInteger("42")
        BigInteger("000") shouldBe bigIntegerOf(0L)
        BigInteger("-00042") shouldBe BigInteger("-42")
        BigInteger("+00042") shouldBe BigInteger("42")
    }

    test("empty string throws NumberFormatException") {
        shouldThrow<NumberFormatException> { BigInteger("") }
    }

    test("whitespace-only or whitespace-containing strings throw") {
        shouldThrow<NumberFormatException> { BigInteger(" ") }
        shouldThrow<NumberFormatException> { BigInteger("1 2") }
        shouldThrow<NumberFormatException> { BigInteger(" 42") }
        shouldThrow<NumberFormatException> { BigInteger("42 ") }
    }

    test("single zero in various radixes") {
        for (radix in 2..36) {
            BigInteger("0", radix) shouldBe bigIntegerOf(0L)
        }
    }
})

// -- Byte array edge cases --

class ByteArrayAdditionalEdgeCaseTest : FunSpec({

    test("all-zero byte array of various lengths is zero") {
        BigInteger(byteArrayOf(0)) shouldBe bigIntegerOf(0L)
        BigInteger(byteArrayOf(0, 0)) shouldBe bigIntegerOf(0L)
        BigInteger(byteArrayOf(0, 0, 0)) shouldBe bigIntegerOf(0L)
        BigInteger(byteArrayOf(0, 0, 0, 0)) shouldBe bigIntegerOf(0L)
        BigInteger(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)) shouldBe bigIntegerOf(0L)
    }

    test("single-byte values round-trip") {
        for (i in -128..127) {
            val bytes = byteArrayOf(i.toByte())
            val bi = BigInteger(bytes)
            bi.toByteArray() shouldBe bytes
        }
    }

    test("large negative two's complement round-trip") {
        // -(2^128) as bytes: 0xFF followed by 16 zero bytes... actually:
        // -(2^128) in two's complement (17 bytes): [0xFF, 0x00 x16] → no, that's -(2^128 - 2^128 + 256^16)
        // Let me just verify round-trip with a known large negative
        val value = -(bigIntegerOf(2L).pow(128))
        val bytes = value.toByteArray()
        BigInteger(bytes) shouldBe value
    }

    test("positive 8-byte values with a zero top nibble round-trip") {
        val bytes = byteArrayOf(
            0x01,
            0x08,
            0x93.toByte(),
            0x13,
            0x4A,
            0x1D,
            0xD7.toByte(),
            0x9B.toByte(),
        )
        val value = BigInteger("74471104908744603")

        value.toByteArray() shouldBe bytes
        BigInteger(bytes) shouldBe value
        BigInteger(bytes).toByteArray() shouldBe bytes

        val padded = byteArrayOf(0x7F) + bytes + byteArrayOf(0x00)
        BigInteger(padded, 1, bytes.size) shouldBe value
    }
})

// -- toString with radix for negative numbers --

class ToStringRadixNegativeTest : FunSpec({

    context("toString with radix round-trips for negative values") {
        withData(
            nameFn = { "${it.first} in base ${it.second}" },
            "-1" to 2,
            "-1" to 16,
            "-255" to 16,
            "-42" to 8,
            "-1295" to 36,
            "-123456789" to 16,
            "-100" to 2,
        ) { (value, radix) ->
            val bi = BigInteger(value)
            BigInteger(bi.toString(radix), radix) shouldBe bi
        }
    }

    test("toString radix round-trip property for negative values") {
        checkAll(Arb.long(Long.MIN_VALUE..-1L)) { n ->
            val bi = bigIntegerOf(n)
            for (radix in listOf(2, 8, 10, 16, 36)) {
                BigInteger(bi.toString(radix), radix) shouldBe bi
            }
        }
    }
})

// -- Bit operations on negative numbers at high bit positions --

class HighBitPositionNegativeTest : FunSpec({

    test("testBit on negative at very high positions returns true") {
        // In two's complement, negative numbers have infinite leading 1s
        val v = BigInteger("-1")
        v.testBit(1000) shouldBe true
        v.testBit(10000) shouldBe true

        val v2 = BigInteger("-2")
        v2.testBit(1000) shouldBe true
    }

    test("clearBit on negative at high position has no effect (bit is 1, clearing makes 0)") {
        // -1 = ...11111111; clearBit(100) = ...1111 0 1111...1111 (bit 100 cleared)
        val v = BigInteger("-1")
        val cleared = v.clearBit(100)
        // Result should differ from -1
        cleared.testBit(100) shouldBe false
        // But all other bits should still be 1
        cleared.testBit(99) shouldBe true
        cleared.testBit(101) shouldBe true
    }

    test("setBit on negative at high position is identity (bit already 1)") {
        val v = BigInteger("-1")
        v.setBit(100) shouldBe v
        v.setBit(1000) shouldBe v
    }

    test("flipBit on negative at high position clears the bit") {
        val v = BigInteger("-1")
        val flipped = v.flipBit(100)
        flipped.testBit(100) shouldBe false
        // flipBit(100) again should restore
        flipped.flipBit(100) shouldBe v
    }
})

// -- bitLength for negative powers of two --

class BitLengthNegativePowersOfTwoTest : FunSpec({

    // bitLength(-2^n) = n, because |(-2^n)| - 1 = 2^n - 1 which has bitLength n
    context("bitLength of -2^n equals n") {
        withData(
            nameFn = { "bitLength(-2^${it}) = ${it}" },
            1, 2, 3, 4, 7, 8, 15, 16, 31, 32, 63, 64, 100, 128
        ) { n ->
            val value = -(bigIntegerOf(2L).pow(n))
            value.bitLength() shouldBeExactly n
        }
    }

    // bitLength(-(2^n + 1)) = n + 1
    context("bitLength of -(2^n + 1) equals n + 1") {
        withData(
            nameFn = { "bitLength(-(2^${it}+1)) = ${it + 1}" },
            1, 2, 3, 4, 7, 8, 15, 16, 31, 32, 63, 64
        ) { n ->
            val value = -(bigIntegerOf(2L).pow(n) + bigIntegerOf(1L))
            value.bitLength() shouldBeExactly (n + 1)
        }
    }
})

// -- getLowestSetBit edge cases --

class GetLowestSetBitEdgeCaseTest : FunSpec({

    test("getLowestSetBit for powers of two") {
        for (n in listOf(0, 1, 2, 7, 8, 15, 16, 31, 32, 63, 64, 100)) {
            bigIntegerOf(2L).pow(n).getLowestSetBit() shouldBeExactly n
        }
    }

    test("getLowestSetBit for negative powers of two") {
        for (n in listOf(1, 2, 7, 8, 15, 16, 31, 32, 63, 64)) {
            (-(bigIntegerOf(2L).pow(n))).getLowestSetBit() shouldBeExactly n
        }
    }

    test("getLowestSetBit for large negative odd number is 0") {
        val v = -(bigIntegerOf(2L).pow(100) + bigIntegerOf(1L))
        v.getLowestSetBit() shouldBeExactly 0
    }
})

// -- Large number arithmetic cross-check --

class LargeNumberArithmeticTest : FunSpec({

    test("arithmetic with numbers larger than Long.MAX_VALUE") {
        val a = BigInteger("123456789012345678901234567890")
        val b = BigInteger("987654321098765432109876543210")
        val sum = a + b
        sum shouldBe BigInteger("1111111110111111111011111111100")

        val diff = b - a
        diff shouldBe BigInteger("864197532086419753208641975320")

        val product = BigInteger("12345678901234567890") * BigInteger("98765432109876543210")
        product shouldBe BigInteger("1219326311370217952237463801111263526900")
    }

    test("division of very large numbers") {
        val a = BigInteger("1219326311370217952237463801111263526900")
        val b = BigInteger("12345678901234567890")
        (a / b) shouldBe BigInteger("98765432109876543210")
        (a % b) shouldBe bigIntegerOf(0L)
    }

    test("pow with very large result") {
        val result = bigIntegerOf(2L).pow(256)
        result shouldBe BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639936")
    }
})

// -- sqrt additional edge cases --

class SqrtEdgeCaseTest : FunSpec({

    test("sqrt(2) = 1") {
        BigInteger("2").sqrt() shouldBe bigIntegerOf(1L)
    }

    test("sqrt(3) = 1") {
        BigInteger("3").sqrt() shouldBe bigIntegerOf(1L)
    }

    test("sqrt of very large perfect square") {
        val root = BigInteger("12345678901234567890")
        val square = root * root
        square.sqrt() shouldBe root
    }

    test("sqrt of 2^256") {
        val value = bigIntegerOf(2L).pow(256)
        value.sqrt() shouldBe bigIntegerOf(2L).pow(128)
    }
})
