package io.github.artificialpb.bignum

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun verifyBigDecimalDifferentialCase(case: BigDecimalDifferentialCase) {
    withClue(case.id) {
        BigDecimalDifferentialExecutor.evaluate(case) shouldBe case.expected
    }
}

private fun FunSpec.addBigDecimalDifferentialOperation(operation: BigDecimalDifferentialOperation) {
    test(operation.name.lowercase()) {
        BigDecimalDifferentialFixtureRepository.loadCases(operation).forEach(::verifyBigDecimalDifferentialCase)
    }
}

class BigDecimalConstructionFuzzTest : FunSpec({
    BigDecimalDifferentialGroup.CONSTRUCTION.let { group ->
        BigDecimalDifferentialOperation.entries.filter { it.group == group }.forEach(::addBigDecimalDifferentialOperation)
    }
})

class BigDecimalFactoryFuzzTest : FunSpec({
    BigDecimalDifferentialGroup.FACTORY.let { group ->
        BigDecimalDifferentialOperation.entries.filter { it.group == group }.forEach(::addBigDecimalDifferentialOperation)
    }
})

class BigDecimalArithmeticFuzzTest : FunSpec({
    BigDecimalDifferentialGroup.ARITHMETIC.let { group ->
        BigDecimalDifferentialOperation.entries.filter { it.group == group }.forEach(::addBigDecimalDifferentialOperation)
    }
})

class BigDecimalScaleFuzzTest : FunSpec({
    BigDecimalDifferentialGroup.SCALE.let { group ->
        BigDecimalDifferentialOperation.entries.filter { it.group == group }.forEach(::addBigDecimalDifferentialOperation)
    }
})

class BigDecimalConversionFuzzTest : FunSpec({
    BigDecimalDifferentialGroup.CONVERSION.let { group ->
        BigDecimalDifferentialOperation.entries.filter { it.group == group }.forEach(::addBigDecimalDifferentialOperation)
    }
})

class BigDecimalComparisonFuzzTest : FunSpec({
    BigDecimalDifferentialGroup.COMPARISON.let { group ->
        BigDecimalDifferentialOperation.entries.filter { it.group == group }.forEach(::addBigDecimalDifferentialOperation)
    }
})
