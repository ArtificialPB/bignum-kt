package io.github.artificialpb.bignum

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SwiftMembersNativeTest : FunSpec({
    test("fixed-width integer constructors") {
        BigInteger(0L) shouldBe bigIntegerOf(0)
        BigInteger(Long.MIN_VALUE) shouldBe bigIntegerOf(Long.MIN_VALUE)
        BigInteger(Long.MAX_VALUE) shouldBe bigIntegerOf(Long.MAX_VALUE)
        BigInteger(0UL) shouldBe bigIntegerOf(0)
        BigInteger(ULong.MAX_VALUE) shouldBe bigIntegerOf("18446744073709551615")
        BigInteger(1UL shl 60) shouldBe bigIntegerOf(1L shl 60)
    }

    test("NSData round trip") {
        for (value in listOf("0", "-1", "127", "128", "-129", "-98765432109876543210")) {
            val bigInteger = bigIntegerOf(value)
            val data = bigInteger.toData()
            data.length shouldBe bigInteger.toByteArray().size.toULong()
            BigInteger(data) shouldBe bigInteger
        }
        shouldThrow<NumberFormatException> { BigInteger(byteArrayOf().toNSData()) }
    }

    test("companion constants") {
        BigInteger.zero shouldBe bigIntegerOf(0)
        BigInteger.one shouldBe bigIntegerOf(1)
        BigInteger.two shouldBe bigIntegerOf(2)
        BigInteger.ten shouldBe bigIntegerOf(10)
    }
})
