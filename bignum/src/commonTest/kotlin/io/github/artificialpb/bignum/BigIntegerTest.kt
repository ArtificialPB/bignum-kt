package io.github.artificialpb.bignum

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe

// -- Helper to construct expected BigInteger values --

private fun bi(value: String) = BigInteger(value)

// -- Data classes for data-driven tests --

data class StringParseCase(val input: String, val expected: String) {
    override fun toString() = "\"$input\" -> \"$expected\""
}

data class RadixParseCase(val input: String, val radix: Int, val expected: String) {
    override fun toString() = "\"$input\" (radix $radix) -> \"$expected\""
}

data class BinaryOpCase(val a: String, val b: String, val expected: String) {
    override fun toString() = "$a op $b = $expected"
}

data class UnaryOpCase(val input: String, val expected: String) {
    override fun toString() = "$input -> $expected"
}

data class IntResultCase(val input: String, val expected: Int) {
    override fun toString() = "$input -> $expected"
}

data class BoolCase(val input: String, val expected: Boolean) {
    override fun toString() = "$input -> $expected"
}

data class BitTestCase(val input: String, val bit: Int, val expected: Boolean) {
    override fun toString() = "$input.testBit($bit) = $expected"
}

data class BitOpCase(val input: String, val bit: Int, val expected: String) {
    override fun toString() = "$input op bit $bit = $expected"
}

data class ShiftCase(val input: String, val n: Int, val expected: String) {
    override fun toString() = "$input shift $n = $expected"
}

data class ByteArrayCase(val input: String, val bytes: List<Int>) {
    override fun toString() = "$input -> [${bytes.joinToString(", ") { "0x${it.and(0xFF).toString(16).padStart(2, '0')}" }}]"
}

data class RadixStringCase(val input: String, val radix: Int, val expected: String) {
    override fun toString() = "$input (radix $radix) -> \"$expected\""
}

data class ModPowCase(val base: String, val exp: String, val mod: String, val expected: String) {
    override fun toString() = "$base^$exp mod $mod = $expected"
}

data class DivRemCase(val a: String, val b: String, val q: String, val r: String) {
    override fun toString() = "$a / $b = $q rem $r"
}

data class PrimeCase(val input: String, val expected: String) {
    override fun toString() = "next prime after $input = $expected"
}

class BigIntegerConstructionTest : FunSpec({

    context("fromString round-trips") {
        withData(
            StringParseCase("0", "0"),
            StringParseCase("1", "1"),
            StringParseCase("-1", "-1"),
            StringParseCase("42", "42"),
            StringParseCase("-42", "-42"),
            StringParseCase("127", "127"),
            StringParseCase("128", "128"),
            StringParseCase("255", "255"),
            StringParseCase("256", "256"),
            StringParseCase("999999999999999999", "999999999999999999"),
            StringParseCase("-999999999999999999", "-999999999999999999"),
            StringParseCase("123456789012345678901234567890", "123456789012345678901234567890"),
            StringParseCase("-123456789012345678901234567890", "-123456789012345678901234567890"),
            StringParseCase("18446744073709551616", "18446744073709551616"),
            StringParseCase("340282366920938463463374607431768211456", "340282366920938463463374607431768211456"),
            StringParseCase("+0", "0"),
            StringParseCase("+1", "1"),
            StringParseCase("+42", "42"),
            StringParseCase("+999999999999999999", "999999999999999999"),
        ) { (input, expected) ->
            BigInteger(input).toString() shouldBe expected
        }
    }

    context("fromString with radix") {
        withData(
            RadixParseCase("FF", 16, "255"),
            RadixParseCase("ff", 16, "255"),
            RadixParseCase("100", 16, "256"),
            RadixParseCase("111", 2, "7"),
            RadixParseCase("11111111", 2, "255"),
            RadixParseCase("123", 8, "83"),
            RadixParseCase("777", 8, "511"),
            RadixParseCase("ZZ", 36, "1295"),
            RadixParseCase("-FF", 16, "-255"),
            RadixParseCase("10", 2, "2"),
            RadixParseCase("10", 8, "8"),
            RadixParseCase("10", 10, "10"),
            RadixParseCase("10", 16, "16"),
            RadixParseCase("+FF", 16, "255"),
            RadixParseCase("+111", 2, "7"),
        ) { (input, radix, expected) ->
            BigInteger(input, radix) shouldBe bi(expected)
        }
    }

    context("fromLong") {
        withData(
            0L to "0",
            1L to "1",
            -1L to "-1",
            42L to "42",
            Long.MAX_VALUE to "9223372036854775807",
            Long.MIN_VALUE to "-9223372036854775808",
        ) { (input, expected) ->
            bigIntegerOf(input) shouldBe bi(expected)
        }
    }

    context("fromInt") {
        withData(
            0 to "0",
            1 to "1",
            2 to "2",
            10 to "10",
            100 to "100",
            -1 to "-1",
            42 to "42",
            Int.MAX_VALUE to "2147483647",
            Int.MIN_VALUE to "-2147483648",
        ) { (input, expected) ->
            bigIntegerOf(input) shouldBe bi(expected)
        }
    }

    context("fromByteArray") {
        withData(
            nameFn = { "bytes ${it.first.joinToString(",") { b -> "0x${b.and(0xFF).toString(16).padStart(2, '0')}" }} -> ${it.second}" },
            listOf(0x00) to "0",
            listOf(0x01) to "1",
            listOf(0x7F) to "127",
            listOf(0x00, 0x80) to "128",
            listOf(0x01, 0x00) to "256",
            listOf(0xFF) to "-1",
            listOf(0x80) to "-128",
            listOf(0xFF, 0x7F) to "-129",
            listOf(0x00, 0xFF, 0xFF) to "65535",
            listOf(0xFF, 0x00, 0x01) to "-65535",
        ) { (bytes, expected) ->
            BigInteger(bytes.map { it.toByte() }.toByteArray()) shouldBe bi(expected)
        }
    }

    test("constants") {
        bigIntegerOf(0L) shouldBe bi("0")
        bigIntegerOf(1L) shouldBe bi("1")
        bigIntegerOf(2L) shouldBe bi("2")
        bigIntegerOf(10L) shouldBe bi("10")
    }

    test("string factory covers cached constants and general values") {
        bigIntegerOf("0") shouldBe bi("0")
        bigIntegerOf("1") shouldBe bi("1")
        bigIntegerOf("2") shouldBe bi("2")
        bigIntegerOf("10") shouldBe bi("10")
        bigIntegerOf("100") shouldBe bi("100")
        bigIntegerOf("12345678901234567890") shouldBe bi("12345678901234567890")
    }
})

class BigIntegerArithmeticTest : FunSpec({

    context("addition") {
        withData(
            BinaryOpCase("0", "0", "0"),
            BinaryOpCase("1", "0", "1"),
            BinaryOpCase("0", "1", "1"),
            BinaryOpCase("1", "1", "2"),
            BinaryOpCase("999999999999999999", "1", "1000000000000000000"),
            BinaryOpCase("-1", "1", "0"),
            BinaryOpCase("-1", "-1", "-2"),
            BinaryOpCase("123456789", "987654321", "1111111110"),
            BinaryOpCase("18446744073709551616", "18446744073709551616", "36893488147419103232"),
            BinaryOpCase("-999999999999", "999999999999", "0"),
        ) { (a, b, expected) ->
            (bi(a) + bi(b)) shouldBe bi(expected)
        }
    }

    context("subtraction") {
        withData(
            BinaryOpCase("0", "0", "0"),
            BinaryOpCase("1", "1", "0"),
            BinaryOpCase("1000000000000000000", "1", "999999999999999999"),
            BinaryOpCase("0", "1", "-1"),
            BinaryOpCase("-1", "-1", "0"),
            BinaryOpCase("100", "200", "-100"),
            BinaryOpCase("18446744073709551616", "1", "18446744073709551615"),
        ) { (a, b, expected) ->
            (bi(a) - bi(b)) shouldBe bi(expected)
        }
    }

    context("multiplication") {
        withData(
            BinaryOpCase("0", "12345", "0"),
            BinaryOpCase("1", "12345", "12345"),
            BinaryOpCase("-1", "12345", "-12345"),
            BinaryOpCase("2", "3", "6"),
            BinaryOpCase("123456789", "987654321", "121932631112635269"),
            BinaryOpCase("-123", "456", "-56088"),
            BinaryOpCase("-123", "-456", "56088"),
            BinaryOpCase("18446744073709551616", "2", "36893488147419103232"),
            BinaryOpCase("999999999999", "999999999999", "999999999998000000000001"),
        ) { (a, b, expected) ->
            (bi(a) * bi(b)) shouldBe bi(expected)
        }
    }

    context("division") {
        withData(
            BinaryOpCase("0", "1", "0"),
            BinaryOpCase("1", "1", "1"),
            BinaryOpCase("100", "3", "33"),
            BinaryOpCase("100", "10", "10"),
            BinaryOpCase("-100", "3", "-33"),
            BinaryOpCase("100", "-3", "-33"),
            BinaryOpCase("-100", "-3", "33"),
            BinaryOpCase("999999999999999999", "1000000000", "999999999"),
            BinaryOpCase("18446744073709551616", "2", "9223372036854775808"),
        ) { (a, b, expected) ->
            (bi(a) / bi(b)) shouldBe bi(expected)
        }
    }

    context("remainder") {
        withData(
            BinaryOpCase("0", "1", "0"),
            BinaryOpCase("1", "1", "0"),
            BinaryOpCase("100", "3", "1"),
            BinaryOpCase("100", "10", "0"),
            BinaryOpCase("-7", "3", "-1"),
            BinaryOpCase("7", "-3", "1"),
            BinaryOpCase("18446744073709551617", "2", "1"),
        ) { (a, b, expected) ->
            (bi(a) % bi(b)) shouldBe bi(expected)
        }
    }

    context("mod (always non-negative)") {
        withData(
            BinaryOpCase("7", "3", "1"),
            BinaryOpCase("-7", "3", "2"),
            BinaryOpCase("0", "5", "0"),
            BinaryOpCase("100", "7", "2"),
            BinaryOpCase("-1", "7", "6"),
            BinaryOpCase("-100", "7", "5"),
        ) { (a, m, expected) ->
            bi(a).mod(bi(m)) shouldBe bi(expected)
        }
    }

    context("divideAndRemainder") {
        withData(
            DivRemCase("100", "3", "33", "1"),
            DivRemCase("0", "5", "0", "0"),
            DivRemCase("17", "5", "3", "2"),
            DivRemCase("-17", "5", "-3", "-2"),
            DivRemCase("1000000000000", "999999999", "1000", "1000"),
        ) { (a, b, q, r) ->
            val result = bi(a).divideAndRemainder(bi(b))
            result[0] shouldBe bi(q)
            result[1] shouldBe bi(r)
        }
    }

    context("negation") {
        withData(
            UnaryOpCase("0", "0"),
            UnaryOpCase("42", "-42"),
            UnaryOpCase("-42", "42"),
            UnaryOpCase("999999999999999999", "-999999999999999999"),
        ) { (input, expected) ->
            (-bi(input)) shouldBe bi(expected)
        }
    }

    context("abs") {
        withData(
            UnaryOpCase("0", "0"),
            UnaryOpCase("42", "42"),
            UnaryOpCase("-42", "42"),
            UnaryOpCase("-999999999999999999", "999999999999999999"),
        ) { (input, expected) ->
            bi(input).abs() shouldBe bi(expected)
        }
    }

    context("pow") {
        withData(
            nameFn = { "${it.first}^${it.second} = ${it.third}" },
            Triple("2", 0, "1"),
            Triple("2", 1, "2"),
            Triple("2", 10, "1024"),
            Triple("2", 32, "4294967296"),
            Triple("2", 64, "18446744073709551616"),
            Triple("10", 18, "1000000000000000000"),
            Triple("3", 20, "3486784401"),
            Triple("-2", 3, "-8"),
            Triple("-2", 4, "16"),
            Triple("1", 1000, "1"),
            Triple("0", 0, "1"),
        ) { (base, exp, expected) ->
            bi(base).pow(exp) shouldBe bi(expected)
        }
    }

    context("modPow") {
        withData(
            ModPowCase("4", "13", "497", "445"),
            ModPowCase("2", "10", "1000", "24"),
            ModPowCase("3", "100", "97", "81"),
            ModPowCase("7", "256", "13", "9"),
        ) { (base, exp, mod, expected) ->
            bi(base).modPow(bi(exp), bi(mod)) shouldBe bi(expected)
        }
    }

    context("modInverse") {
        withData(
            BinaryOpCase("3", "11", "4"),
            BinaryOpCase("7", "13", "2"),
            BinaryOpCase("2", "17", "9"),
            BinaryOpCase("10", "7", "5"),
            BinaryOpCase("3", "1", "0"),     // x.modInverse(1) == 0
            BinaryOpCase("0", "1", "0"),
            BinaryOpCase("100", "1", "0"),
        ) { (a, m, expected) ->
            bi(a).modInverse(bi(m)) shouldBe bi(expected)
        }
    }

    context("gcd") {
        withData(
            BinaryOpCase("12", "8", "4"),
            BinaryOpCase("100", "75", "25"),
            BinaryOpCase("17", "13", "1"),
            BinaryOpCase("0", "5", "5"),
            BinaryOpCase("1000000007", "999999937", "1"),
            BinaryOpCase("-12", "8", "4"),
            BinaryOpCase("12", "-8", "4"),
        ) { (a, b, expected) ->
            bi(a).gcd(bi(b)) shouldBe bi(expected)
        }
    }

    context("lcm") {
        withData(
            BinaryOpCase("12", "8", "24"),
            BinaryOpCase("3", "5", "15"),
            BinaryOpCase("7", "7", "7"),
            BinaryOpCase("0", "5", "0"),
            BinaryOpCase("100", "75", "300"),
        ) { (a, b, expected) ->
            bi(a).lcm(bi(b)) shouldBe bi(expected)
        }
    }
})

class BigIntegerBitwiseTest : FunSpec({

    context("and") {
        withData(
            BinaryOpCase("12", "10", "8"),
            BinaryOpCase("255", "15", "15"),
            BinaryOpCase("0", "12345", "0"),
            BinaryOpCase("255", "255", "255"),
            BinaryOpCase("65535", "255", "255"),
        ) { (a, b, expected) ->
            bi(a).and(bi(b)) shouldBe bi(expected)
        }
    }

    context("or") {
        withData(
            BinaryOpCase("12", "10", "14"),
            BinaryOpCase("240", "15", "255"),
            BinaryOpCase("0", "12345", "12345"),
            BinaryOpCase("255", "255", "255"),
        ) { (a, b, expected) ->
            bi(a).or(bi(b)) shouldBe bi(expected)
        }
    }

    context("xor") {
        withData(
            BinaryOpCase("12", "10", "6"),
            BinaryOpCase("255", "255", "0"),
            BinaryOpCase("0", "12345", "12345"),
            BinaryOpCase("240", "15", "255"),
        ) { (a, b, expected) ->
            bi(a).xor(bi(b)) shouldBe bi(expected)
        }
    }

    context("not (bitwise complement)") {
        withData(
            UnaryOpCase("0", "-1"),
            UnaryOpCase("1", "-2"),
            UnaryOpCase("-1", "0"),
            UnaryOpCase("42", "-43"),
            UnaryOpCase("-42", "41"),
            UnaryOpCase("255", "-256"),
            UnaryOpCase("-256", "255"),
        ) { (input, expected) ->
            bi(input).not() shouldBe bi(expected)
        }
    }

    context("andNot") {
        withData(
            BinaryOpCase("15", "6", "9"),
            BinaryOpCase("255", "240", "15"),
            BinaryOpCase("255", "0", "255"),
            BinaryOpCase("255", "255", "0"),
        ) { (a, b, expected) ->
            bi(a).andNot(bi(b)) shouldBe bi(expected)
        }
    }

    context("shiftLeft") {
        withData(
            ShiftCase("1", 0, "1"),
            ShiftCase("1", 1, "2"),
            ShiftCase("1", 3, "8"),
            ShiftCase("1", 10, "1024"),
            ShiftCase("1", 32, "4294967296"),
            ShiftCase("1", 64, "18446744073709551616"),
            ShiftCase("255", 8, "65280"),
            ShiftCase("-1", 3, "-8"),
        ) { (input, n, expected) ->
            bi(input).shiftLeft(n) shouldBe bi(expected)
        }
    }

    context("shiftRight") {
        withData(
            ShiftCase("1", 0, "1"),
            ShiftCase("2", 1, "1"),
            ShiftCase("32", 3, "4"),
            ShiftCase("1024", 10, "1"),
            ShiftCase("255", 4, "15"),
            ShiftCase("4294967296", 32, "1"),
            ShiftCase("-8", 2, "-2"),
            ShiftCase("-1", 1, "-1"),
        ) { (input, n, expected) ->
            bi(input).shiftRight(n) shouldBe bi(expected)
        }
    }

    context("testBit") {
        withData(
            BitTestCase("0", 0, false),
            BitTestCase("1", 0, true),
            BitTestCase("1", 1, false),
            BitTestCase("2", 0, false),
            BitTestCase("2", 1, true),
            BitTestCase("10", 0, false),
            BitTestCase("10", 1, true),
            BitTestCase("10", 2, false),
            BitTestCase("10", 3, true),
            BitTestCase("255", 7, true),
            BitTestCase("256", 7, false),
            BitTestCase("256", 8, true),
            BitTestCase("-1", 0, true),
            BitTestCase("-1", 100, true),
            BitTestCase("-2", 0, false),
            BitTestCase("-2", 1, true),
        ) { (input, bit, expected) ->
            bi(input).testBit(bit) shouldBe expected
        }
    }

    context("setBit") {
        withData(
            BitOpCase("0", 0, "1"),
            BitOpCase("0", 3, "8"),
            BitOpCase("10", 0, "11"),
            BitOpCase("10", 1, "10"),
            BitOpCase("10", 2, "14"),
        ) { (input, bit, expected) ->
            bi(input).setBit(bit) shouldBe bi(expected)
        }
    }

    context("clearBit") {
        withData(
            BitOpCase("1", 0, "0"),
            BitOpCase("10", 1, "8"),
            BitOpCase("10", 0, "10"),
            BitOpCase("255", 7, "127"),
            BitOpCase("255", 0, "254"),
        ) { (input, bit, expected) ->
            bi(input).clearBit(bit) shouldBe bi(expected)
        }
    }

    context("flipBit") {
        withData(
            BitOpCase("0", 0, "1"),
            BitOpCase("1", 0, "0"),
            BitOpCase("10", 0, "11"),
            BitOpCase("10", 1, "8"),
            BitOpCase("255", 8, "511"),
        ) { (input, bit, expected) ->
            bi(input).flipBit(bit) shouldBe bi(expected)
        }
    }

    context("getLowestSetBit") {
        withData(
            IntResultCase("0", -1),
            IntResultCase("1", 0),
            IntResultCase("2", 1),
            IntResultCase("4", 2),
            IntResultCase("8", 3),
            IntResultCase("12", 2),
            IntResultCase("16", 4),
            IntResultCase("1024", 10),
            IntResultCase("-1", 0),
            IntResultCase("-2", 1),
            IntResultCase("-8", 3),
        ) { (input, expected) ->
            bi(input).getLowestSetBit() shouldBeExactly expected
        }
    }

    context("bitLength") {
        withData(
            IntResultCase("0", 0),
            IntResultCase("1", 1),
            IntResultCase("2", 2),
            IntResultCase("3", 2),
            IntResultCase("4", 3),
            IntResultCase("127", 7),
            IntResultCase("128", 8),
            IntResultCase("255", 8),
            IntResultCase("256", 9),
            IntResultCase("65535", 16),
            IntResultCase("65536", 17),
        ) { (input, expected) ->
            bi(input).bitLength() shouldBeExactly expected
        }
    }

    context("bitCount") {
        withData(
            IntResultCase("0", 0),
            IntResultCase("1", 1),
            IntResultCase("2", 1),
            IntResultCase("3", 2),
            IntResultCase("7", 3),
            IntResultCase("15", 4),
            IntResultCase("255", 8),
            IntResultCase("256", 1),
            IntResultCase("1023", 10),
        ) { (input, expected) ->
            bi(input).bitCount() shouldBeExactly expected
        }
    }
})

class BigIntegerPredicatesTest : FunSpec({

    context("isProbablePrime") {
        withData(
            BoolCase("2", true),
            BoolCase("3", true),
            BoolCase("5", true),
            BoolCase("7", true),
            BoolCase("11", true),
            BoolCase("13", true),
            BoolCase("104729", true),
            BoolCase("1000000007", true),
            BoolCase("0", false),
            BoolCase("1", false),
            BoolCase("4", false),
            BoolCase("100", false),
            BoolCase("1000000006", false),
            BoolCase("-7", true),       // JVM tests absolute value
        ) { (input, expected) ->
            bi(input).isProbablePrime(10) shouldBe expected
        }
    }

    context("nextProbablePrime") {
        withData(
            PrimeCase("0", "2"),
            PrimeCase("1", "2"),
            PrimeCase("2", "3"),
            PrimeCase("3", "5"),
            PrimeCase("4", "5"),
            PrimeCase("5", "7"),
            PrimeCase("8", "11"),
            PrimeCase("10", "11"),
            PrimeCase("13", "17"),
            PrimeCase("100", "101"),
            PrimeCase("1000", "1009"),
        ) { (input, expected) ->
            bi(input).nextProbablePrime() shouldBe bi(expected)
        }
    }
})

class BigIntegerRootsTest : FunSpec({

    context("sqrt") {
        withData(
            UnaryOpCase("0", "0"),
            UnaryOpCase("1", "1"),
            UnaryOpCase("4", "2"),
            UnaryOpCase("9", "3"),
            UnaryOpCase("10", "3"),
            UnaryOpCase("15", "3"),
            UnaryOpCase("16", "4"),
            UnaryOpCase("100", "10"),
            UnaryOpCase("10000", "100"),
            UnaryOpCase("1000000", "1000"),
            UnaryOpCase("152399025", "12345"),
        ) { (input, expected) ->
            bi(input).sqrt() shouldBe bi(expected)
        }
    }
})

class BigIntegerConversionTest : FunSpec({

    context("toInt") {
        withData(
            nameFn = { "BigInteger(\"${it.first}\").toInt() = ${it.second}" },
            "0" to 0,
            "1" to 1,
            "-1" to -1,
            "42" to 42,
            "127" to 127,
            "-128" to -128,
            "32767" to 32767,
        ) { (input, expected) ->
            bi(input).toInt() shouldBeExactly expected
        }
    }

    context("toLong") {
        withData(
            nameFn = { "BigInteger(\"${it.first}\").toLong() = ${it.second}" },
            "0" to 0L,
            "1" to 1L,
            "-1" to -1L,
            "42" to 42L,
            "3000000000" to 3000000000L,
            "9223372036854775807" to Long.MAX_VALUE,
        ) { (input, expected) ->
            bi(input).toLong() shouldBeExactly expected
        }
    }

    context("toDouble") {
        withData(
            nameFn = { "BigInteger(\"${it.first}\").toDouble() = ${it.second}" },
            "0" to 0.0,
            "1" to 1.0,
            "-1" to -1.0,
            "42" to 42.0,
            "1000000" to 1000000.0,
        ) { (input, expected) ->
            bi(input).toDouble() shouldBeExactly expected
        }
    }

    context("toString with radix") {
        withData(
            RadixStringCase("0", 2, "0"),
            RadixStringCase("0", 16, "0"),
            RadixStringCase("255", 2, "11111111"),
            RadixStringCase("255", 8, "377"),
            RadixStringCase("255", 16, "ff"),
            RadixStringCase("256", 16, "100"),
            RadixStringCase("65535", 16, "ffff"),
            RadixStringCase("1295", 36, "zz"),
            RadixStringCase("-255", 16, "-ff"),
            RadixStringCase("4096", 16, "1000"),
        ) { (input, radix, expected) ->
            bi(input).toString(radix) shouldBe expected
        }
    }

    context("toByteArray") {
        withData(
            ByteArrayCase("0", listOf(0x00)),
            ByteArrayCase("1", listOf(0x01)),
            ByteArrayCase("127", listOf(0x7F)),
            ByteArrayCase("128", listOf(0x00, 0x80)),
            ByteArrayCase("255", listOf(0x00, 0xFF)),
            ByteArrayCase("256", listOf(0x01, 0x00)),
            ByteArrayCase("65535", listOf(0x00, 0xFF, 0xFF)),
            ByteArrayCase("65536", listOf(0x01, 0x00, 0x00)),
            ByteArrayCase("16777216", listOf(0x01, 0x00, 0x00, 0x00)),
            ByteArrayCase("-1", listOf(0xFF)),
            ByteArrayCase("-128", listOf(0x80)),
            ByteArrayCase("-129", listOf(0xFF, 0x7F)),
            ByteArrayCase("-256", listOf(0xFF, 0x00)),
            ByteArrayCase("-65536", listOf(0xFF, 0x00, 0x00)),
        ) { (input, expectedBytes) ->
            val expected = expectedBytes.map { it.toByte() }.toByteArray()
            bi(input).toByteArray() shouldBe expected
        }
    }

    context("byteArray round-trip") {
        withData(
            "0", "1", "-1", "42", "-42",
            "127", "128", "-128", "-129",
            "255", "256", "-256",
            "65535", "65536", "-65535", "-65536",
            "16777215", "16777216",
            "2147483647", "2147483648", "-2147483648",
            "9223372036854775807", "-9223372036854775808",
            "123456789012345678901234567890",
            "-123456789012345678901234567890",
            "18446744073709551616",
        ) { value ->
            val original = bi(value)
            BigInteger(original.toByteArray()) shouldBe original
        }
    }
})

class BigIntegerComparisonTest : FunSpec({

    context("compareTo") {
        withData(
            nameFn = { "compare(${it.first}, ${it.second}) -> ${it.third}" },
            Triple("0", "0", 0),
            Triple("1", "0", 1),
            Triple("0", "1", -1),
            Triple("-1", "0", -1),
            Triple("-1", "-2", 1),
            Triple("100", "200", -1),
            Triple("200", "100", 1),
            Triple("999999999999999999", "999999999999999999", 0),
            Triple("999999999999999999", "999999999999999998", 1),
        ) { (a, b, expected) ->
            val cmp = bi(a).compareTo(bi(b))
            when {
                expected < 0 -> cmp shouldBeLessThan 0
                expected > 0 -> cmp shouldBeGreaterThan 0
                else -> cmp shouldBeExactly 0
            }
        }
    }

    context("equals") {
        withData(
            nameFn = { "${it.first} == ${it.second}" },
            "0" to "0",
            "1" to "1",
            "-1" to "-1",
            "999999999999999999" to "999999999999999999",
        ) { (a, b) ->
            bi(a) shouldBe bi(b)
        }
    }

    context("min") {
        withData(
            BinaryOpCase("1", "2", "1"),
            BinaryOpCase("2", "1", "1"),
            BinaryOpCase("-1", "1", "-1"),
            BinaryOpCase("100", "100", "100"),
        ) { (a, b, expected) ->
            bi(a).min(bi(b)) shouldBe bi(expected)
        }
    }

    context("max") {
        withData(
            BinaryOpCase("1", "2", "2"),
            BinaryOpCase("2", "1", "2"),
            BinaryOpCase("-1", "1", "1"),
            BinaryOpCase("100", "100", "100"),
        ) { (a, b, expected) ->
            bi(a).max(bi(b)) shouldBe bi(expected)
        }
    }

    context("signum") {
        withData(
            IntResultCase("0", 0),
            IntResultCase("1", 1),
            IntResultCase("-1", -1),
            IntResultCase("42", 1),
            IntResultCase("-42", -1),
            IntResultCase("999999999999999999", 1),
            IntResultCase("-999999999999999999", -1),
        ) { (input, expected) ->
            bi(input).signum() shouldBeExactly expected
        }
    }
})
