package io.github.artificialpb.bignum

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BigDecimalDoubleFactoryJsTest : FunSpec({
    val cases = listOf(
        Double.MIN_VALUE to "4.9E-324",
        0.001 to "0.001",
        Double.fromBits(4_873_065_150_638_901_229L) to "5.9826604177856678E+17",
        Double.fromBits(-4_349_324_378_817_811_967L) to "-7.2402698873058112E+17",
    )

    cases.forEach { (value, expected) ->
        test("factory renders ${value.toBits()} with JVM 17 semantics") {
            bigDecimalOf(value).toString() shouldBe expected
            value.toBigDecimal().toString() shouldBe expected
        }
    }
})
