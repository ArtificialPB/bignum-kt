package io.github.artificialpb.bignum

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import java.io.File
import java.lang.Math.floorMod
import kotlin.random.Random

object DifferentialFixtureGenerator {
    private const val OUTPUT_DIR = "src/commonTest/resources/differential"
    private const val RANDOM_CASES_PER_OPERATION = 2_500
    private const val RANDOM_SEED_BASE = 0xB16B00B4L

    private const val MERSENNE_127 = "170141183460469231731687303715884105727"
    private const val MERSENNE_127_SQUARED =
        "28948022309329048855892746252171976962977213799489202546401021394546514198529"

    private val canonicalValuePool: List<String> by lazy { commonCanonicalValues() }
    private val bitwiseValuePool: List<String> by lazy { bitwiseValues() }
    private val invalidNumericLiterals = listOf(
        "",
        "+",
        "-",
        " ",
        " 1",
        "1 ",
        "++1",
        "--1",
        "+-1",
        "1_0",
        "abc",
        "0x10",
        "1e3",
        "NaN",
        "Infinity",
    )
    private val byteListEdgeValues = listOf(
        emptyList(),
        listOf(0),
        listOf(1),
        listOf(-1),
        listOf(127),
        listOf(-128),
        listOf(0, -128),
        listOf(-1, 0),
        listOf(1, 0, 0),
        listOf(-1, -1, 1),
        listOf(0, 0, 1),
        listOf(0, 127, -1),
    )
    private val primalityValuePool = listOf(
        "-7",
        "-2",
        "-1",
        "0",
        "1",
        "2",
        "3",
        "4",
        "5",
        "7",
        "11",
        "13",
        "17",
        "19",
        "23",
        "97",
        "341",
        "561",
        "645",
        "1105",
        "1729",
        "2047",
        "2465",
        "2821",
        "6601",
        "8911",
        "41041",
        "3215031751",
        "2147483647",
    )

    private val constructorStringArgArb = arbitrary { rs: RandomSource ->
        StringArg(randomDecimalConstructorLiteral(rs))
    }

    private val intArgArb = arbitrary { rs: RandomSource ->
        IntArg(randomIntValue(rs))
    }

    private val longArgArb = arbitrary { rs: RandomSource ->
        LongArg(randomLongValue(rs))
    }

    private val byteListArgArb = arbitrary { rs: RandomSource ->
        ByteListArg(randomByteListValue(rs))
    }

    private val generalBigIntArgArb = arbitrary { rs: RandomSource ->
        BigIntArg(randomCanonicalBigInt(rs))
    }

    private val bitwiseBigIntArgArb = arbitrary { rs: RandomSource ->
        BigIntArg(randomBitwiseValue(rs))
    }

    private val divisorBigIntArgArb = arbitrary { rs: RandomSource ->
        BigIntArg(randomDivisor(rs))
    }

    private val powBaseBigIntArgArb = arbitrary { rs: RandomSource ->
        BigIntArg(randomPowBase(rs))
    }

    private val modularBigIntArgArb = arbitrary { rs: RandomSource ->
        BigIntArg(randomModularOperand(rs))
    }

    private val primalityBigIntArgArb = arbitrary { rs: RandomSource ->
        BigIntArg(randomPrimalityInput(rs))
    }

    private val sqrtBigIntArgArb = arbitrary { rs: RandomSource ->
        BigIntArg(randomSqrtInput(rs))
    }

    fun generateCasesByOperation(): Map<DifferentialOperation, List<DifferentialCase>> {
        val builder = CorpusBuilder()
        populateConstructionCases(builder)
        populateFactoryCases(builder)
        populateArithmeticCases(builder)
        populateBitwiseCases(builder)
        populateNumberTheoryCases(builder)
        populateConversionCases(builder)
        populateComparisonCases(builder)
        populateRangeCases(builder)
        populateRandomCases(builder)
        return builder.build().also(::validateCoverage)
    }

    fun writeTo(projectDir: File) {
        val outputDir = projectDir.resolve(OUTPUT_DIR)
        outputDir.mkdirs()
        outputDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.forEach(File::delete)

        val casesByOperation = generateCasesByOperation()
        DifferentialOperation.entries.forEach { operation ->
            val fixture = DifferentialFixtureFile(operation, casesByOperation.getValue(operation))
            outputDir.resolve(operation.fixtureFileName).writeText(DifferentialFixtureJsonCodec.encode(fixture))
        }
    }

    private fun validateCoverage(casesByOperation: Map<DifferentialOperation, List<DifferentialCase>>) {
        val missing = DifferentialOperation.entries.filter { casesByOperation.getValue(it).isEmpty() }
        require(missing.isEmpty()) { "Missing differential fixtures for $missing" }

        val underfilled = DifferentialOperation.entries.filter {
            casesByOperation.getValue(it).size < RANDOM_CASES_PER_OPERATION
        }
        require(underfilled.isEmpty()) {
            "Differential fixtures below $RANDOM_CASES_PER_OPERATION cases for $underfilled"
        }
    }

    private fun populateRandomCases(builder: CorpusBuilder) {
        DifferentialOperation.entries.forEach { operation ->
            val arb = operation.randomArgsArb()
            repeat(RANDOM_CASES_PER_OPERATION) { index ->
                val args = arb.next(rs = RandomSource.seeded(randomSeed(operation, index)))
                builder.add(operation, *args.toTypedArray())
            }
        }
    }

    private fun randomSeed(
        operation: DifferentialOperation,
        index: Int,
    ): Long = RANDOM_SEED_BASE + operation.ordinal * 10_000L + index.toLong()

    private fun DifferentialOperation.randomArgsArb(): Arb<List<DifferentialArg>> = arbitrary { rs ->
        when (this@randomArgsArb) {
            DifferentialOperation.CONSTRUCTOR_STRING,
            DifferentialOperation.FACTORY_OF_STRING,
            -> listOf(constructorStringArgArb.next(rs = rs))

            DifferentialOperation.CONSTRUCTOR_STRING_RADIX ->
                randomRadixConstructorArgs(rs)

            DifferentialOperation.CONSTRUCTOR_BYTES ->
                listOf(byteListArgArb.next(rs = rs))

            DifferentialOperation.CONSTRUCTOR_BYTES_SLICE ->
                randomByteSliceArgs(rs)

            DifferentialOperation.CONSTANT_ZERO,
            DifferentialOperation.CONSTANT_ONE,
            DifferentialOperation.CONSTANT_TWO,
            DifferentialOperation.CONSTANT_TEN,
            -> emptyList()

            DifferentialOperation.FACTORY_OF_LONG ->
                listOf(longArgArb.next(rs = rs))

            DifferentialOperation.FACTORY_OF_INT ->
                listOf(intArgArb.next(rs = rs))

            DifferentialOperation.ADD,
            DifferentialOperation.SUBTRACT,
            DifferentialOperation.MULTIPLY,
            DifferentialOperation.GCD,
            DifferentialOperation.LCM,
            DifferentialOperation.MIN,
            DifferentialOperation.MAX,
            DifferentialOperation.COMPARE_TO,
            DifferentialOperation.EQUALS_BIGINT,
            -> listOf(generalBigIntArgArb.next(rs = rs), generalBigIntArgArb.next(rs = rs))

            DifferentialOperation.DIVIDE,
            DifferentialOperation.REM,
            DifferentialOperation.DIVIDE_AND_REMAINDER,
            -> listOf(generalBigIntArgArb.next(rs = rs), divisorBigIntArgArb.next(rs = rs))

            DifferentialOperation.ABS,
            DifferentialOperation.UNARY_MINUS,
            DifferentialOperation.INC,
            DifferentialOperation.DEC,
            DifferentialOperation.TO_BYTE_ARRAY,
            DifferentialOperation.TO_INT,
            DifferentialOperation.TO_LONG,
            DifferentialOperation.TO_DOUBLE_BITS,
            DifferentialOperation.TO_STRING,
            DifferentialOperation.SIGNUM,
            DifferentialOperation.HASH_CODE,
            DifferentialOperation.EQUALS_NULL,
            -> listOf(generalBigIntArgArb.next(rs = rs))

            DifferentialOperation.POW ->
                listOf(powBaseBigIntArgArb.next(rs = rs), IntArg(randomPowExponent(rs)))

            DifferentialOperation.MOD ->
                listOf(generalBigIntArgArb.next(rs = rs), BigIntArg(randomModulus(rs)))

            DifferentialOperation.MOD_POW ->
                listOf(
                    modularBigIntArgArb.next(rs = rs),
                    BigIntArg(randomModPowExponent(rs)),
                    BigIntArg(randomModulus(rs)),
                )

            DifferentialOperation.MOD_INVERSE ->
                listOf(modularBigIntArgArb.next(rs = rs), BigIntArg(randomModulus(rs)))

            DifferentialOperation.AND,
            DifferentialOperation.OR,
            DifferentialOperation.XOR,
            DifferentialOperation.AND_NOT,
            -> listOf(bitwiseBigIntArgArb.next(rs = rs), bitwiseBigIntArgArb.next(rs = rs))

            DifferentialOperation.NOT,
            DifferentialOperation.GET_LOWEST_SET_BIT,
            DifferentialOperation.BIT_LENGTH,
            DifferentialOperation.BIT_COUNT,
            -> listOf(bitwiseBigIntArgArb.next(rs = rs))

            DifferentialOperation.SHIFT_LEFT,
            DifferentialOperation.SHIFT_RIGHT,
            -> listOf(bitwiseBigIntArgArb.next(rs = rs), IntArg(randomShiftAmount(rs)))

            DifferentialOperation.TEST_BIT,
            DifferentialOperation.SET_BIT,
            DifferentialOperation.CLEAR_BIT,
            DifferentialOperation.FLIP_BIT,
            -> listOf(bitwiseBigIntArgArb.next(rs = rs), IntArg(randomBitIndex(rs)))

            DifferentialOperation.IS_PROBABLE_PRIME ->
                randomProbablePrimeArgs(rs)

            DifferentialOperation.NEXT_PROBABLE_PRIME ->
                listOf(primalityBigIntArgArb.next(rs = rs))

            DifferentialOperation.SQRT ->
                listOf(sqrtBigIntArgArb.next(rs = rs))

            DifferentialOperation.TO_STRING_RADIX ->
                listOf(generalBigIntArgArb.next(rs = rs), IntArg(randomRadix(rs)))

            DifferentialOperation.EQUALS_STRING -> {
                val value = generalBigIntArgArb.next(rs = rs)
                listOf(value, StringArg(randomEqualsString(value.decimal, rs)))
            }

            DifferentialOperation.RANGE_TO_LIST ->
                randomRangeArgs(rs)
        }
    }

    private fun populateConstructionCases(builder: CorpusBuilder) {
        decimalConstructorLiterals().forEach { literal ->
            builder.add(DifferentialOperation.CONSTRUCTOR_STRING, StringArg(literal))
        }

        radixConstructorLiterals().forEach { (literal, radix) ->
            builder.add(
                DifferentialOperation.CONSTRUCTOR_STRING_RADIX,
                StringArg(literal),
                IntArg(radix),
            )
        }

        byteConstructorLiterals().forEach { bytes ->
            builder.add(DifferentialOperation.CONSTRUCTOR_BYTES, ByteListArg(bytes))
        }

        byteSliceConstructorLiterals().forEach { (bytes, off, len) ->
            builder.add(
                DifferentialOperation.CONSTRUCTOR_BYTES_SLICE,
                ByteListArg(bytes),
                IntArg(off),
                IntArg(len),
            )
        }
    }

    private fun populateFactoryCases(builder: CorpusBuilder) {
        builder.add(DifferentialOperation.CONSTANT_ZERO)
        builder.add(DifferentialOperation.CONSTANT_ONE)
        builder.add(DifferentialOperation.CONSTANT_TWO)
        builder.add(DifferentialOperation.CONSTANT_TEN)

        decimalConstructorLiterals().take(18).forEach { literal ->
            builder.add(DifferentialOperation.FACTORY_OF_STRING, StringArg(literal))
        }

        longFactoryValues().forEach { value ->
            builder.add(DifferentialOperation.FACTORY_OF_LONG, LongArg(value))
        }

        intFactoryValues().forEach { value ->
            builder.add(DifferentialOperation.FACTORY_OF_INT, IntArg(value))
        }
    }

    private fun populateArithmeticCases(builder: CorpusBuilder) {
        val values = arithmeticValues()
        val nonZeroValues = values.filter { it != "0" }

        samplePairs(values, 16, 0xA11A).forEach { (a, b) ->
            builder.add(DifferentialOperation.ADD, BigIntArg(a), BigIntArg(b))
            builder.add(DifferentialOperation.SUBTRACT, BigIntArg(a), BigIntArg(b))
            builder.add(DifferentialOperation.MULTIPLY, BigIntArg(a), BigIntArg(b))
            builder.add(DifferentialOperation.GCD, BigIntArg(a), BigIntArg(b))
            builder.add(DifferentialOperation.LCM, BigIntArg(a), BigIntArg(b))
        }

        sampleValues(values, 14, 0xA12B).forEach { value ->
            builder.add(DifferentialOperation.ABS, BigIntArg(value))
            builder.add(DifferentialOperation.UNARY_MINUS, BigIntArg(value))
            builder.add(DifferentialOperation.INC, BigIntArg(value))
            builder.add(DifferentialOperation.DEC, BigIntArg(value))
        }

        samplePairs(values, 14, 0xA13C).forEach { (a, b) ->
            val divisor = nonZeroValues.pickFrom(b)
            builder.add(DifferentialOperation.DIVIDE, BigIntArg(a), BigIntArg(divisor))
            builder.add(DifferentialOperation.REM, BigIntArg(a), BigIntArg(divisor))
            builder.add(
                DifferentialOperation.DIVIDE_AND_REMAINDER,
                BigIntArg(a),
                BigIntArg(divisor),
            )
        }
        builder.add(DifferentialOperation.DIVIDE, BigIntArg("1"), BigIntArg("0"))
        builder.add(DifferentialOperation.REM, BigIntArg("-1"), BigIntArg("0"))
        builder.add(DifferentialOperation.DIVIDE_AND_REMAINDER, BigIntArg(MERSENNE_127), BigIntArg("0"))

        powCases().forEach { (base, exponent) ->
            builder.add(DifferentialOperation.POW, BigIntArg(base), IntArg(exponent))
        }

        modCases().forEach { (value, modulus) ->
            builder.add(DifferentialOperation.MOD, BigIntArg(value), BigIntArg(modulus))
        }

        modPowCases().forEach { (base, exponent, modulus) ->
            builder.add(
                DifferentialOperation.MOD_POW,
                BigIntArg(base),
                BigIntArg(exponent),
                BigIntArg(modulus),
            )
        }

        modInverseCases().forEach { (value, modulus) ->
            builder.add(DifferentialOperation.MOD_INVERSE, BigIntArg(value), BigIntArg(modulus))
        }
    }

    private fun populateBitwiseCases(builder: CorpusBuilder) {
        val values = bitwiseValues()

        samplePairs(values, 14, 0xB17A).forEach { (a, b) ->
            builder.add(DifferentialOperation.AND, BigIntArg(a), BigIntArg(b))
            builder.add(DifferentialOperation.OR, BigIntArg(a), BigIntArg(b))
            builder.add(DifferentialOperation.XOR, BigIntArg(a), BigIntArg(b))
            builder.add(DifferentialOperation.AND_NOT, BigIntArg(a), BigIntArg(b))
        }

        sampleValues(values, 16, 0xB18B).forEach { value ->
            builder.add(DifferentialOperation.NOT, BigIntArg(value))
            builder.add(DifferentialOperation.GET_LOWEST_SET_BIT, BigIntArg(value))
            builder.add(DifferentialOperation.BIT_LENGTH, BigIntArg(value))
            builder.add(DifferentialOperation.BIT_COUNT, BigIntArg(value))
        }

        shiftCases().forEach { (value, shift) ->
            builder.add(DifferentialOperation.SHIFT_LEFT, BigIntArg(value), IntArg(shift))
            builder.add(DifferentialOperation.SHIFT_RIGHT, BigIntArg(value), IntArg(shift))
        }
        builder.add(DifferentialOperation.SHIFT_LEFT, BigIntArg("1"), IntArg(Int.MIN_VALUE))
        builder.add(DifferentialOperation.SHIFT_RIGHT, BigIntArg("0"), IntArg(Int.MIN_VALUE))

        bitIndexCases().forEach { (value, bit) ->
            builder.add(DifferentialOperation.TEST_BIT, BigIntArg(value), IntArg(bit))
            builder.add(DifferentialOperation.SET_BIT, BigIntArg(value), IntArg(bit))
            builder.add(DifferentialOperation.CLEAR_BIT, BigIntArg(value), IntArg(bit))
            builder.add(DifferentialOperation.FLIP_BIT, BigIntArg(value), IntArg(bit))
        }
    }

    private fun populateNumberTheoryCases(builder: CorpusBuilder) {
        probablePrimeCases().forEach { (value, certainty) ->
            builder.add(
                DifferentialOperation.IS_PROBABLE_PRIME,
                BigIntArg(value),
                IntArg(certainty),
            )
        }

        nextProbablePrimeCases().forEach { value ->
            builder.add(DifferentialOperation.NEXT_PROBABLE_PRIME, BigIntArg(value))
        }

        sqrtCases().forEach { value ->
            builder.add(DifferentialOperation.SQRT, BigIntArg(value))
        }
    }

    private fun populateConversionCases(builder: CorpusBuilder) {
        conversionValues().forEach { value ->
            builder.add(DifferentialOperation.TO_BYTE_ARRAY, BigIntArg(value))
            builder.add(DifferentialOperation.TO_INT, BigIntArg(value))
            builder.add(DifferentialOperation.TO_LONG, BigIntArg(value))
            builder.add(DifferentialOperation.TO_DOUBLE_BITS, BigIntArg(value))
            builder.add(DifferentialOperation.TO_STRING, BigIntArg(value))
            builder.add(DifferentialOperation.SIGNUM, BigIntArg(value))
        }

        sampleValues(conversionValues(), 12, 0xC019).forEachIndexed { index, value ->
            val radix = listOf(2, 8, 10, 16, 36)[index % 5]
            builder.add(DifferentialOperation.TO_STRING_RADIX, BigIntArg(value), IntArg(radix))
        }
        builder.add(DifferentialOperation.TO_STRING_RADIX, BigIntArg("255"), IntArg(1))
        builder.add(DifferentialOperation.TO_STRING_RADIX, BigIntArg("-255"), IntArg(37))
    }

    private fun populateComparisonCases(builder: CorpusBuilder) {
        val values = comparisonValues()

        samplePairs(values, 16, 0xD041).forEach { (a, b) ->
            builder.add(DifferentialOperation.MIN, BigIntArg(a), BigIntArg(b))
            builder.add(DifferentialOperation.MAX, BigIntArg(a), BigIntArg(b))
            builder.add(DifferentialOperation.COMPARE_TO, BigIntArg(a), BigIntArg(b))
            builder.add(DifferentialOperation.EQUALS_BIGINT, BigIntArg(a), BigIntArg(b))
        }

        sampleValues(values, 10, 0xD052).forEach { value ->
            builder.add(DifferentialOperation.EQUALS_NULL, BigIntArg(value))
            builder.add(
                DifferentialOperation.EQUALS_STRING,
                BigIntArg(value),
                StringArg(if (value.startsWith("-")) value.drop(1) else "$value!"),
            )
            builder.add(DifferentialOperation.HASH_CODE, BigIntArg(value))
        }

        builder.add(DifferentialOperation.EQUALS_STRING, BigIntArg("0"), StringArg("0"))
        builder.add(DifferentialOperation.EQUALS_STRING, BigIntArg(MERSENNE_127), StringArg(MERSENNE_127))
    }

    private fun populateRangeCases(builder: CorpusBuilder) {
        listOf(
            "-3" to "-1",
            "-2" to "2",
            "-1" to "-1",
            "0" to "0",
            "0" to "3",
            "1" to "4",
            "3" to "1",
            "5" to "5",
        ).forEach { (start, end) ->
            builder.add(DifferentialOperation.RANGE_TO_LIST, BigIntArg(start), BigIntArg(end))
        }
    }

    private fun randomDecimalConstructorLiteral(rs: RandomSource): String = when (nextChoice(rs, 8)) {
        0, 1, 2 -> randomSignedDecimalLiteral(rs, nextInt(rs, 1, 120))
        3 -> canonicalValuePool.pick(rs)
        4 -> renderSignedValue(randomCanonicalBigInt(rs, maxDigits = 120), rs)
        else -> invalidNumericLiterals.pick(rs)
    }

    private fun randomRadixConstructorArgs(rs: RandomSource): List<DifferentialArg> {
        val radix = randomRadix(rs)
        val literal = if (radix in 2..36 && nextChoice(rs, 5) != 0) {
            randomRadixLiteral(BigInteger(randomCanonicalBigInt(rs, maxDigits = 80)), radix, rs)
        } else {
            randomInvalidRadixLiteral(radix, rs)
        }
        return listOf(StringArg(literal), IntArg(radix))
    }

    private fun randomByteSliceArgs(rs: RandomSource): List<DifferentialArg> {
        val bytes = randomByteListValue(rs, maxSize = 24)
        val valid = bytes.isNotEmpty() && nextChoice(rs, 4) != 0
        val size = bytes.size

        val (offset, length) = if (valid) {
            val off = nextInt(rs, 0, size - 1)
            off to nextInt(rs, 1, size - off)
        } else {
            when (nextChoice(rs, 4)) {
                0 -> -1 to nextInt(rs, 1, size + 1)
                1 -> nextInt(rs, 0, size + 1) to -1
                2 -> size + 1 to 1
                else -> {
                    val off = if (size == 0) 0 else nextInt(rs, 0, size)
                    off to maxOf(1, size - off + 1)
                }
            }
        }

        return listOf(ByteListArg(bytes), IntArg(offset), IntArg(length))
    }

    private fun randomLongValue(rs: RandomSource): Long = when (nextChoice(rs, 6)) {
        0 -> listOf(Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, 2L, Long.MAX_VALUE - 1, Long.MAX_VALUE).pick(rs)
        else -> Arb.long().next(rs = rs)
    }

    private fun randomIntValue(rs: RandomSource): Int = when (nextChoice(rs, 6)) {
        0 -> listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -100, -10, -2, -1, 0, 1, 2, 7, 10, 42, 100, Int.MAX_VALUE - 1, Int.MAX_VALUE).pick(rs)
        else -> Arb.int().next(rs = rs)
    }

    private fun randomCanonicalBigInt(
        rs: RandomSource,
        maxDigits: Int = 96,
    ): String = when (nextChoice(rs, 7)) {
        0 -> canonicalValuePool.pick(rs)
        1 -> Arb.long().next(rs = rs).toString()
        2 -> randomPowerVariant(rs, maxShift = minOf(maxDigits * 3, 256))
        else -> BigInteger(randomSignedDecimalLiteral(rs, nextInt(rs, 1, maxDigits))).toString()
    }

    private fun randomBitwiseValue(rs: RandomSource): String = when (nextChoice(rs, 5)) {
        0, 1 -> bitwiseValuePool.pick(rs)
        2 -> randomPowerVariant(rs, maxShift = 255)
        else -> randomCanonicalBigInt(rs, maxDigits = 96)
    }

    private fun randomDivisor(rs: RandomSource): String = if (nextChoice(rs, 8) == 0) "0" else randomNonZeroBigInt(rs, maxDigits = 80)

    private fun randomNonZeroBigInt(
        rs: RandomSource,
        maxDigits: Int = 96,
    ): String {
        repeat(32) {
            val candidate = randomCanonicalBigInt(rs, maxDigits)
            if (candidate != "0") return candidate
        }
        return "1"
    }

    private fun randomPositiveBigInt(
        rs: RandomSource,
        maxDigits: Int = 48,
    ): String {
        repeat(32) {
            val candidate = when (nextChoice(rs, 5)) {
                0 -> listOf("1", "2", "3", "17", "97", "65537").pick(rs)
                1 -> BigInteger(randomUnsignedDecimalLiteral(rs, nextInt(rs, 1, maxDigits))).toString()
                else -> BigInteger(randomCanonicalBigInt(rs, maxDigits)).abs().toString()
            }
            if (candidate != "0") return candidate
        }
        return "1"
    }

    private fun randomPositiveBigIntAtLeastTwo(
        rs: RandomSource,
        maxDigits: Int = 48,
    ): String {
        repeat(32) {
            val candidate = randomPositiveBigInt(rs, maxDigits)
            if (candidate != "1") return candidate
        }
        return "2"
    }

    private fun randomSmallBigInt(rs: RandomSource): String = when (nextChoice(rs, 6)) {
        0 -> listOf("-1024", "-256", "-16", "-2", "-1", "0", "1", "2", "16", "256", "1024").pick(rs)
        1 -> nextInt(rs, -1_000_000, 1_000_000).toString()
        2 -> randomPowerVariant(rs, maxShift = 32)
        else -> BigInteger(randomSignedDecimalLiteral(rs, nextInt(rs, 1, 18))).toString()
    }

    private fun randomPowBase(rs: RandomSource): String = when (nextChoice(rs, 4)) {
        0 -> listOf("-256", "-10", "-2", "-1", "0", "1", "2", "10", "256").pick(rs)
        else -> randomSmallBigInt(rs)
    }

    private fun randomModularOperand(rs: RandomSource): String = when (nextChoice(rs, 4)) {
        0 -> randomSmallBigInt(rs)
        else -> randomCanonicalBigInt(rs, maxDigits = 40)
    }

    private fun randomPrimalityInput(rs: RandomSource): String = when (nextChoice(rs, 7)) {
        0, 1 -> primalityValuePool.pick(rs)
        2 -> nextInt(rs, -1_000_000, 1_000_000).toString()
        3 -> {
            val root = BigInteger(randomPositiveBigInt(rs, maxDigits = 10))
            val square = root * root
            if (nextChoice(rs, 2) == 0) square.toString() else (-square).toString()
        }

        4 -> (BigInteger(randomPositiveBigInt(rs, maxDigits = 12)) * bigIntegerOf(2L)).toString()
        else -> randomCanonicalBigInt(rs, maxDigits = 24)
    }

    private fun randomProbablePrimeArgs(rs: RandomSource): List<DifferentialArg> = when (nextChoice(rs, 5)) {
        0 -> listOf(
            BigIntArg(listOf("-7", "-2", "-1", "0", "1").pick(rs)),
            IntArg(randomCertainty(rs)),
        )

        1, 2 -> listOf(
            BigIntArg(randomPrimeCandidate(rs)),
            IntArg(randomCertainty(rs)),
        )

        else -> listOf(
            BigIntArg(randomStableCompositeCandidate(rs)),
            IntArg(randomCompositeCertainty(rs)),
        )
    }

    private fun randomPrimeCandidate(rs: RandomSource): String = when (nextChoice(rs, 4)) {
        0 -> listOf("2", "3", "5", "7", "11", "13", "17", "19", "23", "97", "65537", MERSENNE_127).pick(rs)
        else -> BigInteger(randomPositiveBigIntAtLeastTwo(rs, maxDigits = 18)).nextProbablePrime().toString()
    }

    private fun randomStableCompositeCandidate(rs: RandomSource): String = when (nextChoice(rs, 5)) {
        0 -> listOf("4", "6", "8", "9", "10", "12", "15", "21", "25", "27", "341", "561", "645", "1105", "1729").pick(rs)
        else -> {
            val factor = listOf("2", "3", "5", "7", "11").pick(rs)
            (BigInteger(factor) * BigInteger(randomPositiveBigIntAtLeastTwo(rs, maxDigits = 12))).toString()
        }
    }

    private fun randomCompositeCertainty(rs: RandomSource): Int = listOf(-5, -1, 0, 10, 25, 50, 100, 128).pick(rs)

    private fun randomSqrtInput(rs: RandomSource): String = when (nextChoice(rs, 7)) {
        0 -> "-${randomPositiveBigInt(rs, maxDigits = 32)}"
        1 -> {
            val root = BigInteger(randomPositiveBigInt(rs, maxDigits = 18))
            (root * root).toString()
        }

        2 -> {
            val root = BigInteger(randomPositiveBigInt(rs, maxDigits = 18))
            ((root * root) + bigIntegerOf(1L)).toString()
        }

        else -> randomPositiveBigInt(rs, maxDigits = 64)
    }

    private fun randomPowExponent(rs: RandomSource): Int = when (nextChoice(rs, 4)) {
        0 -> listOf(-3, -1, 0, 1, 2, 3, 7, 15, 16, 31, 32, 63, 64, 127).pick(rs)
        else -> nextInt(rs, -4, 48)
    }

    private fun randomModPowExponent(rs: RandomSource): String = when (nextChoice(rs, 4)) {
        0 -> listOf("-8", "-4", "-1", "0", "1", "2", "3", "7", "16", "32", "64", "127").pick(rs)
        else -> nextInt(rs, -8, 128).toString()
    }

    private fun randomModulus(rs: RandomSource): String = when (nextChoice(rs, 6)) {
        0 -> "0"
        1 -> "-${randomPositiveBigInt(rs, maxDigits = 24)}"
        2 -> "1"
        else -> randomPositiveBigInt(rs, maxDigits = 24)
    }

    private fun randomShiftAmount(rs: RandomSource): Int = when (nextChoice(rs, 4)) {
        0 -> listOf(-256, -129, -65, -64, -8, -1, 0, 1, 7, 31, 63, 64, 127, 128, 255, 256).pick(rs)
        else -> nextInt(rs, -256, 256)
    }

    private fun randomBitIndex(rs: RandomSource): Int = when (nextChoice(rs, 4)) {
        0 -> listOf(-8, -1, 0, 1, 7, 8, 15, 16, 31, 63, 127, 255, 256, 511).pick(rs)
        else -> nextInt(rs, -8, 512)
    }

    private fun randomCertainty(rs: RandomSource): Int = when (nextChoice(rs, 4)) {
        0 -> listOf(-5, -1, 0, 1, 2, 10, 25, 50, 100, 128).pick(rs)
        else -> nextInt(rs, -5, 128)
    }

    private fun randomRadix(rs: RandomSource): Int = when (nextChoice(rs, 5)) {
        0 -> listOf(Int.MIN_VALUE, -10, -1, 0, 1, 37, 38, 50, Int.MAX_VALUE).pick(rs)
        else -> nextInt(rs, 2, 36)
    }

    private fun randomEqualsString(
        value: String,
        rs: RandomSource,
    ): String = when (nextChoice(rs, 6)) {
        0 -> value
        1 -> renderSignedValue(value, rs)
        2 -> if (value.startsWith("-")) value.drop(1) else "$value!"
        3 -> randomDecimalConstructorLiteral(rs)
        4 -> randomInvalidNumericLiteral(rs)
        else -> randomAsciiToken(rs, 0, 16)
    }

    private fun randomRangeArgs(rs: RandomSource): List<DifferentialArg> {
        val start = nextInt(rs, -50, 50)
        val end = start + nextInt(rs, -10, 10)
        return listOf(BigIntArg(start.toString()), BigIntArg(end.toString()))
    }

    private fun renderSignedValue(
        canonical: String,
        rs: RandomSource,
    ): String {
        val negative = canonical.startsWith("-")
        val digits = canonical.removePrefix("-")
        val prefix = when {
            negative -> "-"
            nextChoice(rs, 3) == 0 -> "+"
            else -> ""
        }
        val zeros = "0".repeat(nextInt(rs, 0, 3))
        return prefix + zeros + digits
    }

    private fun randomSignedDecimalLiteral(
        rs: RandomSource,
        digits: Int,
    ): String {
        val sign = when (nextChoice(rs, 3)) {
            0 -> ""
            1 -> "-"
            else -> "+"
        }
        return sign + randomUnsignedDecimalLiteral(rs, digits, allowLeadingZeros = true)
    }

    private fun randomUnsignedDecimalLiteral(
        rs: RandomSource,
        digits: Int,
        allowLeadingZeros: Boolean = false,
    ): String {
        val zeroCount = if (allowLeadingZeros) nextInt(rs, 0, 3) else 0
        val leadingZeros = "0".repeat(zeroCount)
        val body = if (nextChoice(rs, 6) == 0) {
            "0"
        } else {
            buildString {
                append(('1'.code + nextChoice(rs, 9)).toChar())
                repeat(digits - 1) {
                    append(('0'.code + nextChoice(rs, 10)).toChar())
                }
            }
        }
        return leadingZeros + body
    }

    private fun randomInvalidNumericLiteral(rs: RandomSource): String = when (nextChoice(rs, 4)) {
        0 -> invalidNumericLiterals.pick(rs)
        else -> randomAsciiToken(rs, 1, 12)
    }

    private fun randomInvalidRadixLiteral(
        radix: Int,
        rs: RandomSource,
    ): String = when {
        radix !in 2..36 -> randomInvalidNumericLiteral(rs)
        nextChoice(rs, 4) == 0 -> invalidNumericLiterals.pick(rs)
        else -> {
            val sign = if (nextChoice(rs, 3) == 0) "-" else ""
            val zeros = "0".repeat(nextInt(rs, 0, 2))
            sign + zeros + invalidDigitFor(radix) + randomAsciiToken(rs, 0, 8)
        }
    }

    private fun invalidDigitFor(radix: Int): Char {
        val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
        return digits.getOrNull(radix.coerceAtLeast(0)) ?: '@'
    }

    private fun randomRadixLiteral(
        value: BigInteger,
        radix: Int,
        rs: RandomSource,
    ): String {
        val digits = value.abs().toString(radix).let { text ->
            if (radix > 10 && nextChoice(rs, 2) == 0) text.uppercase() else text
        }
        val zeros = "0".repeat(nextInt(rs, 0, 2))
        val sign = when {
            value.signum() < 0 -> "-"
            nextChoice(rs, 2) == 0 -> "+"
            else -> ""
        }
        return sign + zeros + digits
    }

    private fun randomPowerVariant(
        rs: RandomSource,
        maxShift: Int,
    ): String {
        val power = bigIntegerOf(1L).shiftLeft(nextInt(rs, 0, maxShift))
        return when (nextChoice(rs, 6)) {
            0 -> power.toString()
            1 -> (-power).toString()
            2 -> (power - bigIntegerOf(1L)).toString()
            3 -> (power + bigIntegerOf(1L)).toString()
            4 -> (-(power - bigIntegerOf(1L))).toString()
            else -> (-(power + bigIntegerOf(1L))).toString()
        }
    }

    private fun randomAsciiToken(
        rs: RandomSource,
        minLength: Int,
        maxLength: Int,
    ): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_+-x "
        val length = nextInt(rs, minLength, maxLength)
        return buildString {
            repeat(length) {
                append(chars[nextChoice(rs, chars.length)])
            }
        }
    }

    private fun randomByteListValue(
        rs: RandomSource,
        maxSize: Int = 512,
    ): List<Int> = when (nextChoice(rs, 4)) {
        0 -> byteListEdgeValues.filter { it.size <= maxSize }.pick(rs)
        else -> List(nextInt(rs, 0, maxSize)) { nextInt(rs, -128, 127) }
    }

    private fun nextChoice(
        rs: RandomSource,
        upperExclusive: Int,
    ): Int = floorMod(Arb.int().next(rs = rs), upperExclusive)

    private fun nextInt(
        rs: RandomSource,
        minInclusive: Int,
        maxInclusive: Int,
    ): Int {
        require(minInclusive <= maxInclusive) {
            "Invalid int range: $minInclusive..$maxInclusive"
        }
        val span = maxInclusive - minInclusive + 1
        return minInclusive + floorMod(Arb.int().next(rs = rs), span)
    }

    private fun <T> List<T>.pick(rs: RandomSource): T = this[nextChoice(rs, size)]

    private fun decimalConstructorLiterals(): List<String> = buildList {
        addAll(
            listOf(
                "0",
                "-0",
                "+0",
                "0000",
                "+000123",
                "-000123",
                "9223372036854775807",
                "-9223372036854775808",
                MERSENNE_127,
                "-$MERSENNE_127",
                "",
                "+",
                "-",
                " 1",
                "1 ",
                "++1",
                "--1",
                "1_0",
                "abc",
                "0x10",
            ),
        )
        val random = Random(0xD311)
        repeat(12) {
            add(randomDecimalLiteral(random, random.nextInt(1, 128)))
        }
    }

    private fun radixConstructorLiterals(): List<Pair<String, Int>> = buildList {
        val random = Random(0xD322)
        val values = sampleValues(commonCanonicalValues(), 14, 0xD323)
        val radices = listOf(2, 8, 10, 16, 36)
        values.forEachIndexed { index, decimal ->
            val radix = radices[index % radices.size]
            add(renderRadixLiteral(BigInteger(decimal), radix, random) to radix)
        }
        add("10" to 1)
        add("10" to 37)
        add("2" to 2)
        add("2" to 1)
        add("g" to 16)
        add("z" to 10)
        add("" to 10)
        add("-" to 36)
    }

    private fun byteConstructorLiterals(): List<List<Int>> = buildList {
        addAll(byteListEdgeValues)
        val random = Random(0xD333)
        repeat(8) {
            add(randomByteList(random, random.nextInt(1, 256)))
        }
    }

    private fun byteSliceConstructorLiterals(): List<Triple<List<Int>, Int, Int>> = buildList {
        add(Triple(listOf(1, 2, 3, 4), 0, 4))
        add(Triple(listOf(1, 2, 3, 4), 1, 2))
        add(Triple(listOf(-1, 0, 1, 2), 2, 2))
        add(Triple(listOf(0, 127, -1, -128), 0, 1))
        add(Triple(listOf(0, 127, -1, -128), 3, 1))
        add(Triple(listOf(1, 2, 3), -1, 2))
        add(Triple(listOf(1, 2, 3), 0, -1))
        add(Triple(listOf(1, 2, 3), 4, 1))
        add(Triple(listOf(1, 2, 3), 2, 2))
        add(Triple(emptyList(), 0, 1))
        val random = Random(0xD344)
        repeat(6) {
            val bytes = randomByteList(random, random.nextInt(1, 256))
            val off = random.nextInt(-1, bytes.size + 2)
            val len = if (random.nextBoolean()) -1 else random.nextInt(1, bytes.size + 2)
            add(Triple(bytes, off, len))
        }
    }

    private fun longFactoryValues(): List<Long> = buildList {
        addAll(
            listOf(
                Long.MIN_VALUE,
                Long.MIN_VALUE + 1,
                -1L,
                0L,
                1L,
                2L,
                7L,
                255L,
                65_535L,
                1_000_000_007L,
                Long.MAX_VALUE - 1,
                Long.MAX_VALUE,
            ),
        )
        val random = Random(0xF111)
        repeat(8) {
            add(random.nextLong())
        }
    }

    private fun intFactoryValues(): List<Int> = buildList {
        addAll(
            listOf(
                Int.MIN_VALUE,
                Int.MIN_VALUE + 1,
                -255,
                -100,
                -10,
                -2,
                -1,
                0,
                1,
                2,
                7,
                10,
                42,
                100,
                255,
                65_535,
                Int.MAX_VALUE - 1,
                Int.MAX_VALUE,
            ),
        )
        val random = Random(0xF110)
        repeat(8) {
            add(random.nextInt())
        }
    }

    private fun arithmeticValues(): List<String> = sampleValues(commonCanonicalValues(), 64, 0xA101)

    private fun bitwiseValues(): List<String> = buildList {
        addAll(
            listOf(
                "-1025",
                "-257",
                "-255",
                "-129",
                "-128",
                "-127",
                "-3",
                "-2",
                "-1",
                "0",
                "1",
                "2",
                "3",
                "7",
                "8",
                "15",
                "16",
                "31",
                "32",
                "63",
                "64",
                "127",
                "128",
                "255",
                "256",
                MERSENNE_127,
                "-$MERSENNE_127",
            ),
        )
    }

    private fun conversionValues(): List<String> = buildList {
        addAll(
            listOf(
                "-340282366920938463463374607431768211456",
                "-18446744073709551616",
                "-9223372036854775808",
                "-9007199254740993",
                "-9007199254740992",
                "-129",
                "-128",
                "-127",
                "-1",
                "0",
                "1",
                "127",
                "128",
                "129",
                "255",
                "256",
                "9007199254740992",
                "9007199254740993",
                "9223372036854775807",
                "18446744073709551616",
                "340282366920938463463374607431768211456",
                MERSENNE_127,
                "-$MERSENNE_127",
                "1${"0".repeat(400)}",
                "-1${"0".repeat(400)}",
            ),
        )
    }

    private fun comparisonValues(): List<String> = sampleValues(commonCanonicalValues(), 20, 0xD101)

    private fun probablePrimeCases(): List<Pair<String, Int>> = listOf(
        "0" to 10,
        "1" to 10,
        "2" to 10,
        "3" to 10,
        "4" to 10,
        "-2" to 10,
        "97" to -1,
        "97" to 0,
        "97" to 1,
        "97" to 10,
        "341" to 100,
        "561" to 100,
        "645" to 100,
        "1105" to 100,
        "1729" to 100,
        "41041" to 100,
        "3215031751" to 100,
        "2152302898747" to 100,
        MERSENNE_127 to 100,
        "-$MERSENNE_127" to 100,
        MERSENNE_127_SQUARED to 100,
    )

    private fun nextProbablePrimeCases(): List<String> = listOf(
        "-1",
        "0",
        "1",
        "2",
        "3",
        "4",
        "561",
        "1105",
        "1729",
        "2047",
        "2147483647",
        MERSENNE_127,
    )

    private fun sqrtCases(): List<String> = buildList {
        addAll(
            listOf(
                "-1",
                "0",
                "1",
                "2",
                "3",
                "4",
                "15",
                "16",
                "17",
                "81",
                "82",
                MERSENNE_127,
            ),
        )
        val square = BigInteger("123456789012345678901234567890").pow(2)
        add(square.toString())
        add((square + bigIntegerOf(1L)).toString())
    }

    private fun powCases(): List<Pair<String, Int>> = listOf(
        "0" to -1,
        "0" to 0,
        "0" to 5,
        "1" to 0,
        "1" to 63,
        "-1" to 1,
        "-1" to 64,
        "2" to 16,
        "-2" to 15,
        "10" to 8,
        "-10" to 9,
        "256" to 7,
        "-256" to 6,
        MERSENNE_127 to 2,
    )

    private fun modCases(): List<Pair<String, String>> = listOf(
        "0" to "1",
        "1" to "1",
        "-1" to "2",
        "2" to "17",
        "-2" to "17",
        "12345678901234567890" to "97",
        MERSENNE_127 to "65537",
        "5" to "0",
        "5" to "-7",
    )

    private fun modPowCases(): List<Triple<String, String, String>> = listOf(
        Triple("2", "0", "17"),
        Triple("2", "10", "17"),
        Triple("-2", "15", "17"),
        Triple("5", "-1", "17"),
        Triple("6", "-1", "15"),
        Triple("5", "123", "1"),
        Triple("5", "3", "0"),
        Triple("5", "3", "-7"),
        Triple("0", "0", "17"),
        Triple(MERSENNE_127, "19", "65537"),
    )

    private fun modInverseCases(): List<Pair<String, String>> = listOf(
        "3" to "11",
        "10" to "17",
        "-3" to "11",
        "5" to "1",
        "2" to "4",
        "7" to "0",
        "7" to "-5",
        "0" to "11",
        "12345678901234567890" to "97",
    )

    private fun shiftCases(): List<Pair<String, Int>> = buildList {
        listOf("0", "1", "-1", "255", "-255", MERSENNE_127, "-$MERSENNE_127").forEach { value ->
            listOf(-63, -8, -1, 0, 1, 7, 31, 63, 127).forEach { shift ->
                add(value to shift)
            }
        }
    }

    private fun bitIndexCases(): List<Pair<String, Int>> = buildList {
        listOf("-255", "-1", "0", "1", "2", "255", MERSENNE_127).forEach { value ->
            listOf(-1, 0, 1, 7, 8, 15, 16, 63, 127).forEach { bit ->
                add(value to bit)
            }
        }
    }

    private fun commonCanonicalValues(): List<String> = buildList {
        val values = linkedSetOf<String>()

        fun addCanonical(literal: String) {
            values += BigInteger(literal).toString()
        }

        listOf(
            "0",
            "1",
            "-1",
            "2",
            "-2",
            "3",
            "-3",
            "7",
            "-7",
            "15",
            "-15",
            "16",
            "-16",
            "31",
            "-31",
            "63",
            "-63",
            "64",
            "-64",
            "127",
            "-127",
            "128",
            "-128",
            "255",
            "-255",
            "256",
            "-256",
            "1024",
            "-1024",
            "9223372036854775807",
            "-9223372036854775808",
            "18446744073709551616",
            "-18446744073709551616",
            MERSENNE_127,
            "-$MERSENNE_127",
            MERSENNE_127_SQUARED,
            "-$MERSENNE_127_SQUARED",
        ).forEach(::addCanonical)

        listOf(1, 2, 7, 8, 15, 16, 31, 32, 63, 64, 127, 128, 255).forEach { shift ->
            val power = bigIntegerOf(1L).shiftLeft(shift)
            values += power.toString()
            values += (-power).toString()
            values += (power - bigIntegerOf(1L)).toString()
            values += (power + bigIntegerOf(1L)).toString()
            values += (-(power - bigIntegerOf(1L))).toString()
            values += (-(power + bigIntegerOf(1L))).toString()
        }

        val random = Random(0xC0FFEE)
        repeat(24) {
            addCanonical(randomDecimalLiteral(random, random.nextInt(1, 100)))
        }

        addAll(values)
    }

    private fun sampleValues(
        values: List<String>,
        count: Int,
        seed: Int,
    ): List<String> = values.shuffled(Random(seed)).take(count.coerceAtMost(values.size))

    private fun samplePairs(
        values: List<String>,
        count: Int,
        seed: Int,
    ): List<Pair<String, String>> {
        val random = Random(seed)
        return buildList {
            repeat(count) {
                add(values[random.nextInt(values.size)] to values[random.nextInt(values.size)])
            }
        }
    }

    private fun renderRadixLiteral(
        value: BigInteger,
        radix: Int,
        random: Random,
    ): String {
        val digits = value.abs().toString(radix).let { text ->
            if (radix > 10 && random.nextBoolean()) text.uppercase() else text
        }
        val zeros = "0".repeat(random.nextInt(0, 3))
        val sign = when {
            value.signum() < 0 -> "-"
            random.nextBoolean() -> "+"
            else -> ""
        }
        return sign + zeros + digits
    }

    private fun randomDecimalLiteral(
        random: Random,
        digits: Int,
    ): String {
        val sign = when (random.nextInt(3)) {
            0 -> ""
            1 -> "-"
            else -> "+"
        }
        val leadingZeros = "0".repeat(random.nextInt(0, 3))
        val body = if (random.nextInt(6) == 0) {
            "0"
        } else {
            buildString {
                append(('1'.code + random.nextInt(9)).toChar())
                repeat(digits - 1) {
                    append(('0'.code + random.nextInt(10)).toChar())
                }
            }
        }
        return sign + leadingZeros + body
    }

    private fun randomByteList(
        random: Random,
        size: Int,
    ): List<Int> = List(size) { random.nextInt(-128, 128) }

    private fun List<String>.pickFrom(fallbackSeed: String): String = if (contains(fallbackSeed) && fallbackSeed != "0") fallbackSeed else first()
}

private class CorpusBuilder {
    private val casesByOperation = DifferentialOperation.entries.associateWith { mutableListOf<DifferentialCase>() }
        .toMutableMap()

    fun add(
        operation: DifferentialOperation,
        vararg args: DifferentialArg,
    ) {
        val cases = casesByOperation.getValue(operation)
        val id = "${operation.name.lowercase()}_${cases.size.toString().padStart(4, '0')}"
        val arguments = args.toList()
        cases += DifferentialCase(
            id = id,
            group = operation.group,
            operation = operation,
            args = arguments,
            expected = DifferentialExecutor.evaluate(operation, arguments),
        )
    }

    fun build(): Map<DifferentialOperation, List<DifferentialCase>> = casesByOperation.mapValues { (_, cases) -> cases.toList() }
}
