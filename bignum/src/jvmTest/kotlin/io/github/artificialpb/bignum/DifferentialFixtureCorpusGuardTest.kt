package io.github.artificialpb.bignum

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DifferentialFixtureCorpusGuardTest : FunSpec({
    test("checked-in differential JSON corpus matches the generator") {
        val fixtures = DifferentialFixtureRepository.loadAll()
        val generated = DifferentialFixtureGenerator.generateCasesByOperation()
        val mismatches = DifferentialOperation.entries.mapNotNull { operation ->
            val fixtureCases = fixtures.getValue(operation)
            val generatedCases = generated.getValue(operation)
            if (fixtureCases == generatedCases) {
                return@mapNotNull null
            }

            val firstDifference = fixtureCases.indices.firstOrNull { index ->
                fixtureCases[index] != generatedCases[index]
            }

            buildString {
                append(operation.name)
                append(": ")
                when {
                    fixtureCases.size != generatedCases.size ->
                        append("fixtureCount=${fixtureCases.size}, generatedCount=${generatedCases.size}")

                    firstDifference != null -> {
                        val fixtureCase = fixtureCases[firstDifference]
                        val generatedCase = generatedCases[firstDifference]
                        append("index=$firstDifference, fixtureArgs=${fixtureCase.args}, generatedArgs=${generatedCase.args}, ")
                        append("fixtureExpected=${fixtureCase.expected}, generatedExpected=${generatedCase.expected}")
                    }

                    else -> append("collections differ but no element mismatch was found")
                }
            }
        }

        mismatches shouldBe emptyList()
    }
})
