package io.github.artificialpb.bignum

import java.io.File
import kotlin.random.Random
import java.math.BigInteger as JavaBigInteger

object BigDecimalDifferentialFixtureGenerator {
    private const val OUTPUT_DIR = "src/commonTest/resources/differential-bigdecimal"
    private const val RANDOM_CASES_PER_OPERATION = 2500
    private const val MAX_RANDOM_GENERATION_ATTEMPTS = RANDOM_CASES_PER_OPERATION * 32
    private const val MAX_RANDOM_DUPLICATE_STREAK = 2_048
    const val SEED_PROPERTY = "bignum.differential.seed"
    private const val DEFAULT_RANDOM_SEED = 0xB16DEC1A1L

    private val validDecimalStrings = listOf(
        "0",
        "-0",
        "1",
        "-1",
        "10",
        "-10",
        "0.0",
        "0.00",
        "1.0",
        "1.00",
        "-1.25",
        "123.456",
        "-999999999999999999999999.999999999",
        "1E+3",
        "1E-3",
        "-4.321E+7",
        "9.999999999999999999999999999999999E-50",
        "1.2300E+5",
        "1000E-2",
    )
    private val multiLimbDecimalStrings = listOf(
        "12345678901234567890.123456789",
        "99999999999999999999999999999999999",
        "-100000000000000000001.0000000001",
    )
    private val invalidDecimalStrings = listOf("", "+", "-", ".", "1_", "1e", "NaN", "Infinity", "0x10", " 1", "1 ")
    private val bigIntegerStrings = listOf("0", "1", "-1", "10", "-10", "12345678901234567890", "-99999999999999999999")
    private val scaleValues = listOf(Int.MIN_VALUE, -1000, -100, -10, -1, 0, 1, 2, 5, 10, 100, 1000, Int.MAX_VALUE)
    private val divisionScaleValues = listOf(-100, -10, -1, 0, 1, 2, 5, 10, 100)
    private val powValues = listOf(-1, 0, 1, 2, 3, 5, 10)
    private val shiftValues = listOf(Int.MIN_VALUE, -1000, -10, -1, 0, 1, 2, 10, 1000, Int.MAX_VALUE)
    private val roundingModes = RoundingMode.entries
    private val mathContexts = listOf(
        MathContext(0),
        MathContext(1),
        MathContext(2),
        MathContext(2, RoundingMode.HALF_EVEN),
        MathContext(3, RoundingMode.DOWN),
        MathContext(3, RoundingMode.UNNECESSARY),
        MathContext(5, RoundingMode.HALF_UP),
        MathContext(6, RoundingMode.CEILING),
        MathContext(7, RoundingMode.HALF_EVEN),
        MathContext(16, RoundingMode.HALF_EVEN),
        MathContext(34, RoundingMode.HALF_EVEN),
        MathContext(50, RoundingMode.HALF_EVEN),
    )
    private val sqrtDecimalStrings = listOf("0", "0.00", "4", "0.04", "2", "1E+4", "1E+3", "-1")
    private val activeSeed = ThreadLocal.withInitial { DEFAULT_RANDOM_SEED }

    fun defaultSeed(): Long = DEFAULT_RANDOM_SEED

    fun configuredSeed(rawSeed: String? = System.getProperty(SEED_PROPERTY)): Long = parseSeed(rawSeed) ?: DEFAULT_RANDOM_SEED

    fun writeTo(
        projectDir: File,
        seed: Long = DEFAULT_RANDOM_SEED,
        outputDir: File = projectDir.resolve(OUTPUT_DIR),
    ) {
        withSeed(seed) {
            outputDir.mkdirs()
            outputDir.listFiles()?.filter { it.extension == "json" }?.forEach(File::delete)
            val casesByOperation = generateCasesByOperation(seed)
            BigDecimalDifferentialOperation.entries.forEach { operation ->
                val fixture = BigDecimalDifferentialFixtureFile(operation, casesByOperation.getValue(operation))
                outputDir.resolve(operation.fixtureFileName).writeText(BigDecimalDifferentialFixtureJsonCodec.encode(fixture))
            }
        }
    }

    fun generateCasesByOperation(seed: Long = DEFAULT_RANDOM_SEED): Map<BigDecimalDifferentialOperation, List<BigDecimalDifferentialCase>> = withSeed(seed) {
        val builder = Builder()
        populateEdgeCases(builder)
        populateRandomCases(builder)
        builder.build().also(::validateCoverage)
    }

    private fun validateCoverage(casesByOperation: Map<BigDecimalDifferentialOperation, List<BigDecimalDifferentialCase>>) {
        val missing = BigDecimalDifferentialOperation.entries.filter { casesByOperation.getValue(it).isEmpty() }
        require(missing.isEmpty()) { "Missing differential fixtures for $missing" }

        val duplicates = BigDecimalDifferentialOperation.entries.filter { operation ->
            val cases = casesByOperation.getValue(operation)
            cases.distinctBy(BigDecimalDifferentialCase::args).size != cases.size
        }
        require(duplicates.isEmpty()) { "Duplicate differential fixtures for $duplicates" }
    }

    private fun populateEdgeCases(builder: Builder) {
        validDecimalStrings.forEach { decimal ->
            builder.add(BigDecimalDifferentialOperation.CONSTRUCTOR_STRING, StringArg2(decimal))
            builder.add(BigDecimalDifferentialOperation.FACTORY_OF_STRING, StringArg2(decimal))
            builder.add(BigDecimalDifferentialOperation.ABS, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.NEGATE, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.PLUS, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.SIGNUM, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.SCALE, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.PRECISION, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.UNSCALED_VALUE, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.STRIP_TRAILING_ZEROS, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_STRING, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_ENGINEERING_STRING, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_PLAIN_STRING, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_BIG_INTEGER, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_BIG_INTEGER_EXACT, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_INT, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_INT_EXACT, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_LONG, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_LONG_EXACT, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_DOUBLE_BITS, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_FLOAT_BITS, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.HASH_CODE, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.EQUALS_NULL, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.ULP, BigDecArg(decimal))

            mathContexts.forEach { mathContext ->
                val mathContextArg = MathContextArg(mathContext.toString())
                builder.add(BigDecimalDifferentialOperation.ABS_MATH_CONTEXT, BigDecArg(decimal), mathContextArg)
                builder.add(BigDecimalDifferentialOperation.NEGATE_MATH_CONTEXT, BigDecArg(decimal), mathContextArg)
                builder.add(BigDecimalDifferentialOperation.PLUS_MATH_CONTEXT, BigDecArg(decimal), mathContextArg)
                builder.add(BigDecimalDifferentialOperation.ROUND_MATH_CONTEXT, BigDecArg(decimal), mathContextArg)
            }

            powValues.forEach { exponent ->
                builder.add(BigDecimalDifferentialOperation.POW, BigDecArg(decimal), IntArg2(exponent))
                mathContexts.forEach { mathContext ->
                    builder.add(
                        BigDecimalDifferentialOperation.POW_MATH_CONTEXT,
                        BigDecArg(decimal),
                        IntArg2(exponent),
                        MathContextArg(mathContext.toString()),
                    )
                }
            }
            shiftValues.forEach { amount ->
                builder.add(BigDecimalDifferentialOperation.MOVE_POINT_LEFT, BigDecArg(decimal), IntArg2(amount))
                builder.add(BigDecimalDifferentialOperation.MOVE_POINT_RIGHT, BigDecArg(decimal), IntArg2(amount))
                builder.add(BigDecimalDifferentialOperation.SCALE_BY_POWER_OF_TEN, BigDecArg(decimal), IntArg2(amount))
            }
            scaleValues.forEach { newScale ->
                builder.add(BigDecimalDifferentialOperation.SET_SCALE_EXACT, BigDecArg(decimal), IntArg2(newScale))
                roundingModes.forEach { roundingMode ->
                    builder.add(
                        BigDecimalDifferentialOperation.SET_SCALE_ROUNDING,
                        BigDecArg(decimal),
                        IntArg2(newScale),
                        RoundingModeArg(roundingMode.name),
                    )
                }
            }
        }

        sqrtDecimalStrings.forEach { decimal ->
            mathContexts.forEach { mathContext ->
                builder.add(
                    BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT,
                    BigDecArg(decimal),
                    MathContextArg(mathContext.toString()),
                )
            }
        }

        // Multi-limb values for unary and targeted binary operations (not cross-producted)
        multiLimbDecimalStrings.forEach { decimal ->
            builder.add(BigDecimalDifferentialOperation.CONSTRUCTOR_STRING, StringArg2(decimal))
            builder.add(BigDecimalDifferentialOperation.FACTORY_OF_STRING, StringArg2(decimal))
            builder.add(BigDecimalDifferentialOperation.ABS, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.NEGATE, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.PLUS, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.SIGNUM, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.SCALE, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.PRECISION, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.UNSCALED_VALUE, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.STRIP_TRAILING_ZEROS, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_STRING, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_ENGINEERING_STRING, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_PLAIN_STRING, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_BIG_INTEGER, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_DOUBLE_BITS, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.TO_FLOAT_BITS, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.HASH_CODE, BigDecArg(decimal))
            builder.add(BigDecimalDifferentialOperation.ULP, BigDecArg(decimal))
            // Arithmetic with small operand
            for (small in listOf("1", "-1", "2", "0.5")) {
                builder.add(BigDecimalDifferentialOperation.ADD, BigDecArg(decimal), BigDecArg(small))
                builder.add(BigDecimalDifferentialOperation.SUBTRACT, BigDecArg(decimal), BigDecArg(small))
                builder.add(BigDecimalDifferentialOperation.MULTIPLY, BigDecArg(decimal), BigDecArg(small))
                builder.add(BigDecimalDifferentialOperation.DIVIDE_ROUNDING_MODE, BigDecArg(decimal), BigDecArg(small), RoundingModeArg(RoundingMode.HALF_UP.name))
            }
            mathContexts.forEach { mathContext ->
                val mathContextArg = MathContextArg(mathContext.toString())
                builder.add(BigDecimalDifferentialOperation.ROUND_MATH_CONTEXT, BigDecArg(decimal), mathContextArg)
            }
        }

        invalidDecimalStrings.forEach { decimal ->
            builder.add(BigDecimalDifferentialOperation.CONSTRUCTOR_STRING, StringArg2(decimal))
            builder.add(BigDecimalDifferentialOperation.FACTORY_OF_STRING, StringArg2(decimal))
        }

        bigIntegerStrings.forEach { integer ->
            builder.add(BigDecimalDifferentialOperation.CONSTRUCTOR_BIGINT, BigIntArg2(integer))
            builder.add(BigDecimalDifferentialOperation.FACTORY_OF_BIGINT, BigIntArg2(integer))
            scaleValues.forEach { scale ->
                builder.add(BigDecimalDifferentialOperation.CONSTRUCTOR_BIGINT_SCALE, BigIntArg2(integer), IntArg2(scale))
                builder.add(BigDecimalDifferentialOperation.FACTORY_OF_BIGINT_SCALE, BigIntArg2(integer), IntArg2(scale))
            }
        }

        listOf(Long.MIN_VALUE, -10L, -1L, 0L, 1L, 10L, Long.MAX_VALUE).forEach { value ->
            builder.add(BigDecimalDifferentialOperation.CONSTRUCTOR_LONG, LongArg2(value))
            builder.add(BigDecimalDifferentialOperation.FACTORY_OF_LONG, LongArg2(value))
        }
        listOf(Int.MIN_VALUE, -10, -1, 0, 1, 10, Int.MAX_VALUE).forEach { value ->
            builder.add(BigDecimalDifferentialOperation.CONSTRUCTOR_INT, IntArg2(value))
            builder.add(BigDecimalDifferentialOperation.FACTORY_OF_INT, IntArg2(value))
        }

        validDecimalStrings.forEach { left ->
            validDecimalStrings.forEach { right ->
                builder.add(BigDecimalDifferentialOperation.ADD, BigDecArg(left), BigDecArg(right))
                builder.add(BigDecimalDifferentialOperation.SUBTRACT, BigDecArg(left), BigDecArg(right))
                builder.add(BigDecimalDifferentialOperation.MULTIPLY, BigDecArg(left), BigDecArg(right))
                builder.add(BigDecimalDifferentialOperation.DIVIDE, BigDecArg(left), BigDecArg(right))
                builder.add(BigDecimalDifferentialOperation.REM, BigDecArg(left), BigDecArg(right))
                builder.add(BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER, BigDecArg(left), BigDecArg(right))
                builder.add(BigDecimalDifferentialOperation.MIN, BigDecArg(left), BigDecArg(right))
                builder.add(BigDecimalDifferentialOperation.MAX, BigDecArg(left), BigDecArg(right))
                builder.add(BigDecimalDifferentialOperation.COMPARE_TO, BigDecArg(left), BigDecArg(right))
                builder.add(BigDecimalDifferentialOperation.EQUALS_BIGDECIMAL, BigDecArg(left), BigDecArg(right))
                builder.add(BigDecimalDifferentialOperation.EQUALS_STRING, BigDecArg(left), StringArg2(right))

                mathContexts.forEach { mathContext ->
                    val mathContextArg = MathContextArg(mathContext.toString())
                    builder.add(BigDecimalDifferentialOperation.ADD_MATH_CONTEXT, BigDecArg(left), BigDecArg(right), mathContextArg)
                    builder.add(BigDecimalDifferentialOperation.SUBTRACT_MATH_CONTEXT, BigDecArg(left), BigDecArg(right), mathContextArg)
                    builder.add(BigDecimalDifferentialOperation.MULTIPLY_MATH_CONTEXT, BigDecArg(left), BigDecArg(right), mathContextArg)
                    builder.add(BigDecimalDifferentialOperation.DIVIDE_MATH_CONTEXT, BigDecArg(left), BigDecArg(right), mathContextArg)
                    builder.add(BigDecimalDifferentialOperation.REMAINDER_MATH_CONTEXT, BigDecArg(left), BigDecArg(right), mathContextArg)
                    builder.add(BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER_MATH_CONTEXT, BigDecArg(left), BigDecArg(right), mathContextArg)
                    builder.add(BigDecimalDifferentialOperation.DIVIDE_TO_INTEGRAL_VALUE_MATH_CONTEXT, BigDecArg(left), BigDecArg(right), mathContextArg)
                }

                roundingModes.forEach { roundingMode ->
                    builder.add(
                        BigDecimalDifferentialOperation.DIVIDE_ROUNDING_MODE,
                        BigDecArg(left),
                        BigDecArg(right),
                        RoundingModeArg(roundingMode.name),
                    )
                    divisionScaleValues.forEach { scale ->
                        builder.add(
                            BigDecimalDifferentialOperation.DIVIDE_SCALE_ROUNDING_MODE,
                            BigDecArg(left),
                            BigDecArg(right),
                            IntArg2(scale),
                            RoundingModeArg(roundingMode.name),
                        )
                    }
                }
            }
        }

        // Multi-limb x multi-limb division edge cases
        val multiLimbDivisionPairs = listOf(
            "200000000000000000000" to "100000000000000000000", // exact multi-limb division
            "100000000000000000000" to "300000000000000000000", // non-terminating multi-limb
            "999999999999999999999999999" to "7", // large dividend / small divisor
            "7" to "999999999999999999999999999", // small dividend / large divisor
            "123456789012345678901234" to "987654321098765432109", // two distinct multi-limb values
        )
        for ((left, right) in multiLimbDivisionPairs) {
            builder.add(BigDecimalDifferentialOperation.DIVIDE, BigDecArg(left), BigDecArg(right))
            builder.add(BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER, BigDecArg(left), BigDecArg(right))
            roundingModes.forEach { roundingMode ->
                builder.add(
                    BigDecimalDifferentialOperation.DIVIDE_ROUNDING_MODE,
                    BigDecArg(left),
                    BigDecArg(right),
                    RoundingModeArg(roundingMode.name),
                )
                divisionScaleValues.forEach { scale ->
                    builder.add(
                        BigDecimalDifferentialOperation.DIVIDE_SCALE_ROUNDING_MODE,
                        BigDecArg(left),
                        BigDecArg(right),
                        IntArg2(scale),
                        RoundingModeArg(roundingMode.name),
                    )
                }
            }
            mathContexts.forEach { mathContext ->
                val mathContextArg = MathContextArg(mathContext.toString())
                builder.add(BigDecimalDifferentialOperation.DIVIDE_MATH_CONTEXT, BigDecArg(left), BigDecArg(right), mathContextArg)
                builder.add(BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER_MATH_CONTEXT, BigDecArg(left), BigDecArg(right), mathContextArg)
                builder.add(BigDecimalDifferentialOperation.DIVIDE_TO_INTEGRAL_VALUE_MATH_CONTEXT, BigDecArg(left), BigDecArg(right), mathContextArg)
            }
        }
    }

    private fun populateRandomCases(builder: Builder) {
        BigDecimalDifferentialOperation.entries.forEach { operation ->
            var uniqueAdded = 0
            var duplicateStreak = 0
            var attempt = 0

            while (uniqueAdded < RANDOM_CASES_PER_OPERATION &&
                duplicateStreak < MAX_RANDOM_DUPLICATE_STREAK &&
                attempt < MAX_RANDOM_GENERATION_ATTEMPTS
            ) {
                val args = randomArgs(operation, seedFor(operation, attempt))
                if (builder.add(operation, *args.toTypedArray())) {
                    uniqueAdded++
                    duplicateStreak = 0
                } else {
                    duplicateStreak++
                }
                attempt++
            }
        }
    }

    private fun randomArgs(operation: BigDecimalDifferentialOperation, seed: Long): List<BigDecimalDifferentialArg> {
        val random = Random(seed)
        return when (operation) {
            BigDecimalDifferentialOperation.CONSTRUCTOR_STRING,
            BigDecimalDifferentialOperation.FACTORY_OF_STRING,
            -> listOf(StringArg2(randomDecimalString(random, validOnly = random.nextBoolean())))

            BigDecimalDifferentialOperation.CONSTRUCTOR_BIGINT,
            BigDecimalDifferentialOperation.FACTORY_OF_BIGINT,
            -> listOf(BigIntArg2(randomBigInteger(random)))

            BigDecimalDifferentialOperation.CONSTRUCTOR_BIGINT_SCALE,
            BigDecimalDifferentialOperation.FACTORY_OF_BIGINT_SCALE,
            -> listOf(BigIntArg2(randomBigInteger(random)), IntArg2(randomScale(random)))

            BigDecimalDifferentialOperation.CONSTRUCTOR_LONG ->
                listOf(LongArg2(random.nextLong()))

            BigDecimalDifferentialOperation.CONSTRUCTOR_INT ->
                listOf(IntArg2(random.nextInt()))

            BigDecimalDifferentialOperation.FACTORY_OF_LONG ->
                listOf(LongArg2(random.nextLong()))

            BigDecimalDifferentialOperation.FACTORY_OF_INT ->
                listOf(IntArg2(random.nextInt()))

            BigDecimalDifferentialOperation.ADD,
            BigDecimalDifferentialOperation.SUBTRACT,
            BigDecimalDifferentialOperation.MULTIPLY,
            BigDecimalDifferentialOperation.DIVIDE,
            BigDecimalDifferentialOperation.REM,
            BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER,
            BigDecimalDifferentialOperation.MIN,
            BigDecimalDifferentialOperation.MAX,
            BigDecimalDifferentialOperation.COMPARE_TO,
            BigDecimalDifferentialOperation.EQUALS_BIGDECIMAL,
            -> listOf(BigDecArg(randomDecimalString(random, validOnly = true)), BigDecArg(randomDecimalString(random, validOnly = true)))

            BigDecimalDifferentialOperation.ADD_MATH_CONTEXT,
            BigDecimalDifferentialOperation.SUBTRACT_MATH_CONTEXT,
            BigDecimalDifferentialOperation.MULTIPLY_MATH_CONTEXT,
            BigDecimalDifferentialOperation.DIVIDE_MATH_CONTEXT,
            BigDecimalDifferentialOperation.REMAINDER_MATH_CONTEXT,
            BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER_MATH_CONTEXT,
            BigDecimalDifferentialOperation.DIVIDE_TO_INTEGRAL_VALUE_MATH_CONTEXT,
            -> listOf(
                BigDecArg(randomDecimalString(random, validOnly = true)),
                BigDecArg(randomDecimalString(random, validOnly = true)),
                randomMathContext(random),
            )

            BigDecimalDifferentialOperation.DIVIDE_ROUNDING_MODE ->
                listOf(
                    BigDecArg(randomDecimalString(random, validOnly = true)),
                    BigDecArg(randomDecimalString(random, validOnly = true)),
                    RoundingModeArg(roundingModes[random.nextInt(roundingModes.size)].name),
                )

            BigDecimalDifferentialOperation.DIVIDE_SCALE_ROUNDING_MODE ->
                listOf(
                    BigDecArg(randomDecimalString(random, validOnly = true)),
                    BigDecArg(randomDecimalString(random, validOnly = true)),
                    IntArg2(randomDivisionScale(random)),
                    RoundingModeArg(roundingModes[random.nextInt(roundingModes.size)].name),
                )

            BigDecimalDifferentialOperation.POW ->
                listOf(BigDecArg(randomDecimalString(random, validOnly = true)), IntArg2(randomPow(random)))

            BigDecimalDifferentialOperation.POW_MATH_CONTEXT ->
                listOf(
                    BigDecArg(randomDecimalString(random, validOnly = true)),
                    IntArg2(randomPow(random)),
                    randomMathContext(random),
                )

            BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT ->
                listOf(
                    BigDecArg(randomDecimalString(random, validOnly = true)),
                    randomMathContext(random),
                )

            BigDecimalDifferentialOperation.ABS,
            BigDecimalDifferentialOperation.NEGATE,
            BigDecimalDifferentialOperation.PLUS,
            BigDecimalDifferentialOperation.SIGNUM,
            BigDecimalDifferentialOperation.SCALE,
            BigDecimalDifferentialOperation.PRECISION,
            BigDecimalDifferentialOperation.UNSCALED_VALUE,
            BigDecimalDifferentialOperation.STRIP_TRAILING_ZEROS,
            BigDecimalDifferentialOperation.TO_STRING,
            BigDecimalDifferentialOperation.TO_ENGINEERING_STRING,
            BigDecimalDifferentialOperation.TO_PLAIN_STRING,
            BigDecimalDifferentialOperation.TO_BIG_INTEGER,
            BigDecimalDifferentialOperation.TO_BIG_INTEGER_EXACT,
            BigDecimalDifferentialOperation.TO_INT,
            BigDecimalDifferentialOperation.TO_INT_EXACT,
            BigDecimalDifferentialOperation.TO_LONG,
            BigDecimalDifferentialOperation.TO_LONG_EXACT,
            BigDecimalDifferentialOperation.TO_DOUBLE_BITS,
            BigDecimalDifferentialOperation.TO_FLOAT_BITS,
            BigDecimalDifferentialOperation.EQUALS_NULL,
            BigDecimalDifferentialOperation.HASH_CODE,
            BigDecimalDifferentialOperation.ULP,
            -> listOf(BigDecArg(randomDecimalString(random, validOnly = true)))

            BigDecimalDifferentialOperation.ABS_MATH_CONTEXT,
            BigDecimalDifferentialOperation.NEGATE_MATH_CONTEXT,
            BigDecimalDifferentialOperation.PLUS_MATH_CONTEXT,
            BigDecimalDifferentialOperation.ROUND_MATH_CONTEXT,
            -> listOf(
                BigDecArg(randomDecimalString(random, validOnly = true)),
                randomMathContext(random),
            )

            BigDecimalDifferentialOperation.SET_SCALE_EXACT ->
                listOf(BigDecArg(randomDecimalString(random, validOnly = true)), IntArg2(randomScale(random)))

            BigDecimalDifferentialOperation.SET_SCALE_ROUNDING ->
                listOf(
                    BigDecArg(randomDecimalString(random, validOnly = true)),
                    IntArg2(randomScale(random)),
                    RoundingModeArg(roundingModes[random.nextInt(roundingModes.size)].name),
                )

            BigDecimalDifferentialOperation.MOVE_POINT_LEFT,
            BigDecimalDifferentialOperation.MOVE_POINT_RIGHT,
            BigDecimalDifferentialOperation.SCALE_BY_POWER_OF_TEN,
            -> listOf(BigDecArg(randomDecimalString(random, validOnly = true)), IntArg2(randomShift(random)))

            BigDecimalDifferentialOperation.EQUALS_STRING ->
                listOf(BigDecArg(randomDecimalString(random, validOnly = true)), StringArg2(randomDecimalString(random, validOnly = random.nextBoolean())))
        }
    }

    private fun randomDecimalString(random: Random, validOnly: Boolean): String {
        if (!validOnly && random.nextInt(10) == 0) return invalidDecimalStrings[random.nextInt(invalidDecimalStrings.size)]
        val sign = when (random.nextInt(6)) {
            0 -> "-"
            1 -> "+"
            else -> ""
        }
        val exponentChar = if (random.nextBoolean()) "E" else "e"
        val useZeroMantissa = random.nextInt(10) == 0
        val digits = if (useZeroMantissa) "0" else randomDigits(random, 1 + random.nextInt(24))
        val fractionLength = 1 + random.nextInt(24)
        return when (random.nextInt(5)) {
            0 -> sign + digits
            1 -> sign + digits + "." + randomDigits(random, fractionLength)
            2 -> sign + "0." + randomDigits(random, fractionLength)
            3 -> sign + digits + exponentChar + (if (random.nextBoolean()) "+" else "-") + random.nextInt(200)
            else -> sign + digits + "." + randomDigits(random, fractionLength) + exponentChar + random.nextInt(-200, 200).toString()
        }
    }

    private fun randomBigInteger(random: Random): String {
        val sign = if (random.nextBoolean()) "-" else ""
        return sign + randomDigits(random, 1 + random.nextInt(24))
    }

    private fun randomDigits(random: Random, length: Int): String {
        val chars = CharArray(length)
        chars[0] = ('1'.code + random.nextInt(9)).toChar()
        for (index in 1 until length) {
            chars[index] = ('0'.code + random.nextInt(10)).toChar()
        }
        return chars.concatToString()
    }

    private fun randomScale(random: Random): Int = when (random.nextInt(6)) {
        0 -> Int.MIN_VALUE
        1 -> Int.MAX_VALUE
        else -> random.nextInt(-5_000, 5_001)
    }

    private fun randomMathContext(random: Random): MathContextArg {
        val precision = when (random.nextInt(6)) {
            0 -> 0
            1 -> 1
            2 -> 2
            else -> random.nextInt(1, 41)
        }
        val roundingMode = roundingModes[random.nextInt(roundingModes.size)]
        return MathContextArg(MathContext(precision, roundingMode).toString())
    }

    private fun randomDivisionScale(random: Random): Int = when (random.nextInt(5)) {
        0 -> -100
        1 -> 100
        else -> random.nextInt(-200, 201)
    }

    private fun randomPow(random: Random): Int = when (random.nextInt(5)) {
        0 -> -1
        1 -> 0
        else -> random.nextInt(1, 12)
    }

    private fun randomShift(random: Random): Int = when (random.nextInt(6)) {
        0 -> Int.MIN_VALUE
        1 -> Int.MAX_VALUE
        else -> random.nextInt(-3_000, 3_001)
    }

    private fun seedFor(operation: BigDecimalDifferentialOperation, index: Int): Long = activeSeed.get() xor (operation.ordinal.toLong() shl 32) xor index.toLong()

    private inline fun <T> withSeed(seed: Long, block: () -> T): T {
        val previous = activeSeed.get()
        activeSeed.set(seed)
        return try {
            block()
        } finally {
            activeSeed.set(previous)
        }
    }

    private fun parseSeed(rawSeed: String?): Long? {
        if (rawSeed.isNullOrBlank()) return null
        val normalized = rawSeed.trim().replace("_", "")
        return runCatching {
            val negative = normalized.startsWith('-')
            val unsigned = normalized.removePrefix("+").removePrefix("-")
            val (radix, digits) = when {
                unsigned.startsWith("0x", ignoreCase = true) -> 16 to unsigned.drop(2)
                unsigned.startsWith("#") -> 16 to unsigned.drop(1)
                else -> 10 to unsigned
            }
            require(digits.isNotEmpty()) { "Missing seed digits" }
            val magnitude = JavaBigInteger(digits, radix)
            val value = if (negative) magnitude.negate() else magnitude
            value.toLong()
        }.getOrElse { throw IllegalArgumentException("Invalid differential seed '$rawSeed'", it) }
    }

    private class Builder {
        private val casesByOperation: Map<BigDecimalDifferentialOperation, LinkedHashMap<List<BigDecimalDifferentialArg>, BigDecimalDifferentialCase>> =
            BigDecimalDifferentialOperation.entries.associateWith { LinkedHashMap() }

        fun add(operation: BigDecimalDifferentialOperation, vararg args: BigDecimalDifferentialArg): Boolean {
            val argsList = args.toList()
            val cases = casesByOperation.getValue(operation)
            if (cases.containsKey(argsList)) return false
            val index = cases.size
            val id = "${operation.name.lowercase()}_$index"
            cases[argsList] = BigDecimalDifferentialCase(
                id = id,
                group = operation.group,
                operation = operation,
                args = argsList,
                expected = BigDecimalDifferentialExecutor.evaluate(operation, argsList),
            )
            return true
        }

        fun build(): Map<BigDecimalDifferentialOperation, List<BigDecimalDifferentialCase>> = casesByOperation.mapValues { (_, cases) -> cases.values.toList() }
    }
}
