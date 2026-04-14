package io.github.artificialpb.bignum

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun verifyDifferentialCase(case: DifferentialCase) {
    withClue(case.id) {
        DifferentialExecutor.evaluate(case) shouldBe case.expected
    }
}

private fun FunSpec.addDifferentialOperation(operation: DifferentialOperation) {
    test(operation.name.lowercase()) {
        DifferentialFixtureRepository.loadCases(operation).forEach(::verifyDifferentialCase)
    }
}

class DifferentialConstructionFuzzTest : FunSpec({
    DifferentialGroup.CONSTRUCTION.operations.forEach(::addDifferentialOperation)
})

class DifferentialFactoryFuzzTest : FunSpec({
    DifferentialGroup.FACTORY.operations.forEach(::addDifferentialOperation)
})

class DifferentialArithmeticFuzzTest : FunSpec({
    DifferentialGroup.ARITHMETIC.operations.forEach(::addDifferentialOperation)
})

class DifferentialBitwiseFuzzTest : FunSpec({
    DifferentialGroup.BITWISE.operations.forEach(::addDifferentialOperation)
})

class DifferentialNumberTheoryFuzzTest : FunSpec({
    DifferentialGroup.NUMBER_THEORY.operations.forEach(::addDifferentialOperation)
})

class DifferentialConversionFuzzTest : FunSpec({
    DifferentialGroup.CONVERSION.operations.forEach(::addDifferentialOperation)
})

class DifferentialComparisonFuzzTest : FunSpec({
    DifferentialGroup.COMPARISON.operations.forEach(::addDifferentialOperation)
})

class DifferentialRangeFuzzTest : FunSpec({
    DifferentialGroup.RANGE.operations.forEach(::addDifferentialOperation)
})
