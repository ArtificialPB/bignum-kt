package io.github.artificialpb.bignum

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NumberNativeTest : FunSpec({
    test("BigInteger implements Number") {
        val number: Number = bigIntegerOf(258)

        number.toByte() shouldBe 2.toByte()
        number.toShort() shouldBe 258.toShort()
        number.toInt() shouldBe 258
        number.toLong() shouldBe 258L
        number.toFloat() shouldBe 258.0f
        number.toDouble() shouldBe 258.0
    }

    test("BigDecimal implements Number") {
        val number: Number = bigDecimalOf("258.75")

        number.toByte() shouldBe 2.toByte()
        number.toShort() shouldBe 258.toShort()
        number.toInt() shouldBe 258
        number.toLong() shouldBe 258L
        number.toFloat() shouldBe 258.75f
        number.toDouble() shouldBe 258.75
    }
})
