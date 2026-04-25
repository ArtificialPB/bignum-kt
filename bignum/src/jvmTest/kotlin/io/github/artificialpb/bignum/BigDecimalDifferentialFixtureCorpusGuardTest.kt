package io.github.artificialpb.bignum

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun roundingModesCovered(cases: List<BigDecimalDifferentialCase>): Set<RoundingMode> = cases.mapNotNull { case ->
    case.args.filterIsInstance<RoundingModeArg>().firstOrNull()?.let { RoundingMode.valueOf(it.value) }
        ?: case.args.filterIsInstance<MathContextArg>().firstOrNull()?.let { MathContext(it.value).roundingMode }
}.toSet()

private fun List<BigDecimalDifferentialCase>.containsArgs(vararg expected: BigDecimalDifferentialArg): Boolean = any { it.args == expected.toList() }

class BigDecimalDifferentialFixtureCorpusGuardTest : FunSpec({
    test("checked-in big decimal differential JSON corpus matches the generator") {
        val seed = BigDecimalDifferentialFixtureGenerator.configuredSeed()
        val fixtures = BigDecimalDifferentialFixtureRepository.loadAll()
        val generated = BigDecimalDifferentialFixtureGenerator.generateCasesByOperation(seed)
        val mismatches = BigDecimalDifferentialOperation.entries.mapNotNull { operation ->
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

        val duplicates = BigDecimalDifferentialOperation.entries.mapNotNull { operation ->
            val cases = generated.getValue(operation)
            operation.takeIf { cases.distinctBy(BigDecimalDifferentialCase::args).size != cases.size }
        }
        duplicates shouldBe emptyList()
    }

    test("edge-case generator covers every rounding mode for rounding-sensitive operations") {
        val edgeCases = BigDecimalDifferentialFixtureGenerator.generateEdgeCasesByOperation()
        val expectedModes = RoundingMode.entries.toSet()
        val roundingSensitiveOperations = listOf(
            BigDecimalDifferentialOperation.ADD_MATH_CONTEXT,
            BigDecimalDifferentialOperation.SUBTRACT_MATH_CONTEXT,
            BigDecimalDifferentialOperation.MULTIPLY_MATH_CONTEXT,
            BigDecimalDifferentialOperation.DIVIDE_ROUNDING_MODE,
            BigDecimalDifferentialOperation.DIVIDE_SCALE_ROUNDING_MODE,
            BigDecimalDifferentialOperation.DIVIDE_MATH_CONTEXT,
            BigDecimalDifferentialOperation.REMAINDER_MATH_CONTEXT,
            BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER_MATH_CONTEXT,
            BigDecimalDifferentialOperation.DIVIDE_TO_INTEGRAL_VALUE_MATH_CONTEXT,
            BigDecimalDifferentialOperation.POW_MATH_CONTEXT,
            BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT,
            BigDecimalDifferentialOperation.ABS_MATH_CONTEXT,
            BigDecimalDifferentialOperation.NEGATE_MATH_CONTEXT,
            BigDecimalDifferentialOperation.PLUS_MATH_CONTEXT,
            BigDecimalDifferentialOperation.ROUND_MATH_CONTEXT,
            BigDecimalDifferentialOperation.SET_SCALE_ROUNDING,
        )

        roundingSensitiveOperations.forEach { operation ->
            roundingModesCovered(edgeCases.getValue(operation)) shouldBe expectedModes
        }
    }

    test("edge-case generator keeps deterministic rounding tie cases") {
        val edgeCases = BigDecimalDifferentialFixtureGenerator.generateEdgeCasesByOperation()

        edgeCases.getValue(BigDecimalDifferentialOperation.SET_SCALE_ROUNDING).containsArgs(
            BigDecArg("2.5"),
            IntArg2(0),
            RoundingModeArg(RoundingMode.HALF_EVEN.name),
        ) shouldBe true

        edgeCases.getValue(BigDecimalDifferentialOperation.ROUND_MATH_CONTEXT).containsArgs(
            BigDecArg("2.5"),
            MathContextArg(MathContext(1, RoundingMode.HALF_DOWN).toString()),
        ) shouldBe true

        edgeCases.getValue(BigDecimalDifferentialOperation.POW_MATH_CONTEXT).containsArgs(
            BigDecArg("-2.5"),
            IntArg2(1),
            MathContextArg(MathContext(1, RoundingMode.HALF_UP).toString()),
        ) shouldBe true

        edgeCases.getValue(BigDecimalDifferentialOperation.DIVIDE_SCALE_ROUNDING_MODE).containsArgs(
            BigDecArg("5"),
            BigDecArg("2"),
            IntArg2(0),
            RoundingModeArg(RoundingMode.HALF_DOWN.name),
        ) shouldBe true

        edgeCases.getValue(BigDecimalDifferentialOperation.DIVIDE_MATH_CONTEXT).containsArgs(
            BigDecArg("7"),
            BigDecArg("2"),
            MathContextArg(MathContext(1, RoundingMode.HALF_EVEN).toString()),
        ) shouldBe true

        edgeCases.getValue(BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT).containsArgs(
            BigDecArg("6.25"),
            MathContextArg(MathContext(1, RoundingMode.HALF_UP).toString()),
        ) shouldBe true

        edgeCases.getValue(BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT).containsArgs(
            BigDecArg("12.25"),
            MathContextArg(MathContext(1, RoundingMode.HALF_EVEN).toString()),
        ) shouldBe true
    }

    test("generator seed parsing accepts decimal and hex values") {
        BigDecimalDifferentialFixtureGenerator.configuredSeed("42") shouldBe 42L
        BigDecimalDifferentialFixtureGenerator.configuredSeed("0x2A") shouldBe 42L
        BigDecimalDifferentialFixtureGenerator.configuredSeed("1_000") shouldBe 1_000L
        BigDecimalDifferentialFixtureGenerator.configuredSeed("0xc20f4ab1e7e9506e") shouldBe
            java.lang.Long.parseUnsignedLong("c20f4ab1e7e9506e", 16)
        shouldThrow<IllegalArgumentException> {
            BigDecimalDifferentialFixtureGenerator.configuredSeed("not-a-seed")
        }
    }
})
