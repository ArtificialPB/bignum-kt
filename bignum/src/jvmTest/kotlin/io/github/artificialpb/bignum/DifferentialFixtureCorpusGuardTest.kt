package io.github.artificialpb.bignum

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DifferentialFixtureCorpusGuardTest : FunSpec({
    test("checked-in differential JSON corpus matches the generator") {
        val seed = DifferentialFixtureGenerator.configuredSeed()
        val fixtures = DifferentialFixtureRepository.loadAll()
        val generated = DifferentialFixtureGenerator.generateCasesByOperation(seed)
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

        val duplicates = DifferentialOperation.entries.mapNotNull { operation ->
            val cases = generated.getValue(operation)
            operation.takeIf { cases.distinctBy(DifferentialCase::args).size != cases.size }
        }
        duplicates shouldBe emptyList()
    }

    test("generator seed parsing accepts decimal and hex values") {
        DifferentialFixtureGenerator.configuredSeed("42") shouldBe 42L
        DifferentialFixtureGenerator.configuredSeed("0x2A") shouldBe 42L
        DifferentialFixtureGenerator.configuredSeed("1_000") shouldBe 1_000L
        DifferentialFixtureGenerator.configuredSeed("0xc20f4ab1e7e9506e") shouldBe
            java.lang.Long.parseUnsignedLong("c20f4ab1e7e9506e", 16)
        shouldThrow<IllegalArgumentException> {
            DifferentialFixtureGenerator.configuredSeed("not-a-seed")
        }
    }
})
