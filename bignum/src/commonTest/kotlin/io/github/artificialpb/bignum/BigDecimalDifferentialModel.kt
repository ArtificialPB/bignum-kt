package io.github.artificialpb.bignum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class BigDecimalDifferentialGroup {
    CONSTRUCTION,
    FACTORY,
    ARITHMETIC,
    SCALE,
    CONVERSION,
    COMPARISON,
}

enum class BigDecimalDifferentialOperation {
    CONSTRUCTOR_STRING,
    CONSTRUCTOR_BIGINT,
    CONSTRUCTOR_BIGINT_SCALE,
    CONSTRUCTOR_LONG,
    CONSTRUCTOR_INT,
    FACTORY_OF_STRING,
    FACTORY_OF_BIGINT,
    FACTORY_OF_BIGINT_SCALE,
    FACTORY_OF_LONG,
    FACTORY_OF_INT,
    ADD,
    ADD_MATH_CONTEXT,
    SUBTRACT,
    SUBTRACT_MATH_CONTEXT,
    MULTIPLY,
    MULTIPLY_MATH_CONTEXT,
    DIVIDE,
    DIVIDE_ROUNDING_MODE,
    DIVIDE_SCALE_ROUNDING_MODE,
    DIVIDE_MATH_CONTEXT,
    REM,
    REMAINDER_MATH_CONTEXT,
    DIVIDE_AND_REMAINDER,
    DIVIDE_AND_REMAINDER_MATH_CONTEXT,
    DIVIDE_TO_INTEGRAL_VALUE_MATH_CONTEXT,
    POW,
    POW_MATH_CONTEXT,
    SQRT_MATH_CONTEXT,
    ABS,
    ABS_MATH_CONTEXT,
    NEGATE,
    NEGATE_MATH_CONTEXT,
    PLUS,
    PLUS_MATH_CONTEXT,
    ROUND_MATH_CONTEXT,
    SIGNUM,
    SCALE,
    PRECISION,
    UNSCALED_VALUE,
    SET_SCALE_EXACT,
    SET_SCALE_ROUNDING,
    MOVE_POINT_LEFT,
    MOVE_POINT_RIGHT,
    SCALE_BY_POWER_OF_TEN,
    STRIP_TRAILING_ZEROS,
    TO_STRING,
    TO_ENGINEERING_STRING,
    TO_PLAIN_STRING,
    TO_BIG_INTEGER,
    TO_BIG_INTEGER_EXACT,
    TO_INT,
    TO_INT_EXACT,
    TO_LONG,
    TO_LONG_EXACT,
    TO_DOUBLE_BITS,
    TO_FLOAT_BITS,
    MIN,
    MAX,
    COMPARE_TO,
    EQUALS_BIGDECIMAL,
    EQUALS_NULL,
    EQUALS_STRING,
    HASH_CODE,
    ULP,
}

val BigDecimalDifferentialOperation.group: BigDecimalDifferentialGroup
    get() = when (this) {
        BigDecimalDifferentialOperation.CONSTRUCTOR_STRING,
        BigDecimalDifferentialOperation.CONSTRUCTOR_BIGINT,
        BigDecimalDifferentialOperation.CONSTRUCTOR_BIGINT_SCALE,
        BigDecimalDifferentialOperation.CONSTRUCTOR_LONG,
        BigDecimalDifferentialOperation.CONSTRUCTOR_INT,
        -> BigDecimalDifferentialGroup.CONSTRUCTION

        BigDecimalDifferentialOperation.FACTORY_OF_STRING,
        BigDecimalDifferentialOperation.FACTORY_OF_BIGINT,
        BigDecimalDifferentialOperation.FACTORY_OF_BIGINT_SCALE,
        BigDecimalDifferentialOperation.FACTORY_OF_LONG,
        BigDecimalDifferentialOperation.FACTORY_OF_INT,
        -> BigDecimalDifferentialGroup.FACTORY

        BigDecimalDifferentialOperation.ADD,
        BigDecimalDifferentialOperation.ADD_MATH_CONTEXT,
        BigDecimalDifferentialOperation.SUBTRACT,
        BigDecimalDifferentialOperation.SUBTRACT_MATH_CONTEXT,
        BigDecimalDifferentialOperation.MULTIPLY,
        BigDecimalDifferentialOperation.MULTIPLY_MATH_CONTEXT,
        BigDecimalDifferentialOperation.DIVIDE,
        BigDecimalDifferentialOperation.DIVIDE_ROUNDING_MODE,
        BigDecimalDifferentialOperation.DIVIDE_SCALE_ROUNDING_MODE,
        BigDecimalDifferentialOperation.DIVIDE_MATH_CONTEXT,
        BigDecimalDifferentialOperation.REM,
        BigDecimalDifferentialOperation.REMAINDER_MATH_CONTEXT,
        BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER,
        BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER_MATH_CONTEXT,
        BigDecimalDifferentialOperation.DIVIDE_TO_INTEGRAL_VALUE_MATH_CONTEXT,
        BigDecimalDifferentialOperation.POW,
        BigDecimalDifferentialOperation.POW_MATH_CONTEXT,
        BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT,
        BigDecimalDifferentialOperation.ABS,
        BigDecimalDifferentialOperation.ABS_MATH_CONTEXT,
        BigDecimalDifferentialOperation.NEGATE,
        BigDecimalDifferentialOperation.NEGATE_MATH_CONTEXT,
        BigDecimalDifferentialOperation.PLUS,
        BigDecimalDifferentialOperation.PLUS_MATH_CONTEXT,
        BigDecimalDifferentialOperation.ROUND_MATH_CONTEXT,
        BigDecimalDifferentialOperation.ULP,
        -> BigDecimalDifferentialGroup.ARITHMETIC

        BigDecimalDifferentialOperation.SIGNUM,
        BigDecimalDifferentialOperation.SCALE,
        BigDecimalDifferentialOperation.PRECISION,
        BigDecimalDifferentialOperation.UNSCALED_VALUE,
        BigDecimalDifferentialOperation.SET_SCALE_EXACT,
        BigDecimalDifferentialOperation.SET_SCALE_ROUNDING,
        BigDecimalDifferentialOperation.MOVE_POINT_LEFT,
        BigDecimalDifferentialOperation.MOVE_POINT_RIGHT,
        BigDecimalDifferentialOperation.SCALE_BY_POWER_OF_TEN,
        BigDecimalDifferentialOperation.STRIP_TRAILING_ZEROS,
        -> BigDecimalDifferentialGroup.SCALE

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
        -> BigDecimalDifferentialGroup.CONVERSION

        BigDecimalDifferentialOperation.MIN,
        BigDecimalDifferentialOperation.MAX,
        BigDecimalDifferentialOperation.COMPARE_TO,
        BigDecimalDifferentialOperation.EQUALS_BIGDECIMAL,
        BigDecimalDifferentialOperation.EQUALS_NULL,
        BigDecimalDifferentialOperation.EQUALS_STRING,
        BigDecimalDifferentialOperation.HASH_CODE,
        -> BigDecimalDifferentialGroup.COMPARISON
    }

val BigDecimalDifferentialOperation.fixtureFileName: String
    get() = "${name.lowercase()}.json"

sealed interface BigDecimalDifferentialArg

data class BigDecArg(val decimal: String) : BigDecimalDifferentialArg
data class BigIntArg2(val decimal: String) : BigDecimalDifferentialArg
data class IntArg2(val value: Int) : BigDecimalDifferentialArg
data class LongArg2(val value: Long) : BigDecimalDifferentialArg
data class StringArg2(val value: String) : BigDecimalDifferentialArg
data class RoundingModeArg(val value: String) : BigDecimalDifferentialArg
data class MathContextArg(val value: String) : BigDecimalDifferentialArg

sealed interface BigDecimalDifferentialExpected

class BigDecExpected(val decimal: String) : BigDecimalDifferentialExpected {
    private val matcher by lazy(LazyThreadSafetyMode.NONE) { parseExpectedBigDecimal(decimal) }
    val value: BigDecimal by lazy(LazyThreadSafetyMode.NONE) { BigDecimal(decimal) }

    override fun equals(other: Any?): Boolean = when (other) {
        is BigDecExpected -> decimal == other.decimal
        is BigDecActual -> matcher.matches(other.value)
        else -> false
    }

    override fun hashCode(): Int = decimal.hashCode()

    override fun toString(): String = decimal
}

class BigDecActual(val value: BigDecimal) : BigDecimalDifferentialExpected {
    override fun equals(other: Any?): Boolean = when (other) {
        is BigDecActual -> value == other.value
        is BigDecExpected -> other == this
        else -> false
    }

    override fun hashCode(): Int = 31 * value.unscaledValue().hashCode() + value.scale()

    override fun toString(): String = "BigDecimal(value=$value, scale=${value.scale()}, sign=${value.signum()})"
}

data class BigIntExpected2(val decimal: String) : BigDecimalDifferentialExpected
data class BooleanExpected2(val value: Boolean) : BigDecimalDifferentialExpected
data class IntExpected2(val value: Int) : BigDecimalDifferentialExpected
data class LongExpected2(val value: Long) : BigDecimalDifferentialExpected
data class DoubleBitsExpected2(val bits: Long) : BigDecimalDifferentialExpected
data class FloatBitsExpected(val bits: Int) : BigDecimalDifferentialExpected
data class StringExpected2(val value: String) : BigDecimalDifferentialExpected
data class BigDecListExpected(val decimals: List<String>) : BigDecimalDifferentialExpected
data class FailureExpected2(val kind: FailureKind2) : BigDecimalDifferentialExpected

enum class FailureKind2 {
    INVALID_FORMAT,
    DIVIDE_BY_ZERO,
    NON_TERMINATING_DECIMAL,
    NON_TERMINATING_SQUARE_ROOT,
    ROUNDING_NECESSARY,
    NEGATIVE_EXPONENT,
    NEGATIVE_SQUARE_ROOT,
    DIVISION_IMPOSSIBLE,
    OVERFLOW,
    OTHER_ARITHMETIC,
    OTHER_ILLEGAL_ARGUMENT,
    OTHER,
}

data class BigDecimalDifferentialCase(
    val id: String,
    val group: BigDecimalDifferentialGroup,
    val operation: BigDecimalDifferentialOperation,
    val args: List<BigDecimalDifferentialArg>,
    val expected: BigDecimalDifferentialExpected,
) {
    override fun toString(): String = id
}

data class BigDecimalDifferentialFixtureFile(
    val operation: BigDecimalDifferentialOperation,
    val cases: List<BigDecimalDifferentialCase>,
)

object BigDecimalDifferentialFixtureJsonCodec {
    private val json = Json { prettyPrint = true }

    fun encode(file: BigDecimalDifferentialFixtureFile): String = json.encodeToString(JsonElement.serializer(), file.toJson())

    fun decode(text: String): BigDecimalDifferentialFixtureFile {
        val root = json.parseToJsonElement(text).jsonObject
        val operation = BigDecimalDifferentialOperation.valueOf(root.requireString("operation"))
        val group = BigDecimalDifferentialGroup.valueOf(root.requireString("group"))
        require(group == operation.group) { "Fixture group $group does not match operation ${operation.name}" }
        return BigDecimalDifferentialFixtureFile(
            operation = operation,
            cases = root.requireArray("cases").map { it.jsonObject.toCase(operation, group) },
        )
    }

    private fun BigDecimalDifferentialFixtureFile.toJson(): JsonObject = buildJsonObject {
        put("operation", JsonPrimitive(operation.name))
        put("group", JsonPrimitive(operation.group.name))
        put("cases", buildJsonArray { cases.forEach { add(it.toJson()) } })
    }

    private fun BigDecimalDifferentialCase.toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("args", buildJsonArray { args.forEach { add(it.toJson()) } })
        put("expected", expected.toJson())
    }

    private fun BigDecimalDifferentialArg.toJson(): JsonObject = when (this) {
        is BigDecArg -> jsonType("big_decimal", "decimal", decimal)
        is BigIntArg2 -> jsonType("big_integer", "decimal", decimal)
        is IntArg2 -> jsonType("int", "value", value)
        is LongArg2 -> jsonType("long", "value", value)
        is StringArg2 -> jsonType("string", "value", value)
        is RoundingModeArg -> jsonType("rounding_mode", "value", value)
        is MathContextArg -> jsonType("math_context", "value", value)
    }

    private fun BigDecimalDifferentialExpected.toJson(): JsonObject = when (this) {
        is BigDecExpected -> jsonType("big_decimal", "decimal", decimal)
        is BigDecActual -> jsonType("big_decimal", "decimal", value.toString())
        is BigIntExpected2 -> jsonType("big_integer", "decimal", decimal)
        is BooleanExpected2 -> jsonType("boolean", "value", value)
        is IntExpected2 -> jsonType("int", "value", value)
        is LongExpected2 -> jsonType("long", "value", value)
        is DoubleBitsExpected2 -> jsonType("double_bits", "bits", bits)
        is FloatBitsExpected -> jsonType("float_bits", "bits", bits)
        is StringExpected2 -> jsonType("string", "value", value)
        is BigDecListExpected -> buildJsonObject {
            put("type", JsonPrimitive("big_decimal_list"))
            put("values", buildJsonArray { decimals.forEach { add(JsonPrimitive(it)) } })
        }
        is FailureExpected2 -> jsonType("failure", "kind", kind.name)
    }

    private fun jsonType(type: String, key: String, value: Any): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(type))
        put(key, JsonPrimitive(value.toString()))
    }

    private fun JsonObject.toCase(
        operation: BigDecimalDifferentialOperation,
        group: BigDecimalDifferentialGroup,
    ): BigDecimalDifferentialCase = BigDecimalDifferentialCase(
        id = requireString("id"),
        group = group,
        operation = operation,
        args = requireArray("args").map { it.jsonObject.toArg() },
        expected = requireObject("expected").toExpected(),
    )

    private fun JsonObject.toArg(): BigDecimalDifferentialArg = when (requireString("type")) {
        "big_decimal" -> BigDecArg(requireString("decimal"))
        "big_integer" -> BigIntArg2(requireString("decimal"))
        "int" -> IntArg2(requireInt("value"))
        "long" -> LongArg2(requireLong("value"))
        "string" -> StringArg2(requireString("value"))
        "rounding_mode" -> RoundingModeArg(requireString("value"))
        "math_context" -> MathContextArg(requireString("value"))
        else -> error("Unknown BigDecimal differential arg type")
    }

    private fun JsonObject.toExpected(): BigDecimalDifferentialExpected = when (requireString("type")) {
        "big_decimal" -> BigDecExpected(requireString("decimal"))
        "big_integer" -> BigIntExpected2(requireString("decimal"))
        "boolean" -> BooleanExpected2(requireBoolean("value"))
        "int" -> IntExpected2(requireInt("value"))
        "long" -> LongExpected2(requireLong("value"))
        "double_bits" -> DoubleBitsExpected2(requireLong("bits"))
        "float_bits" -> FloatBitsExpected(requireInt("bits"))
        "string" -> StringExpected2(requireString("value"))
        "big_decimal_list" -> BigDecListExpected(requireArray("values").map { it.jsonPrimitive.content })
        "failure" -> FailureExpected2(FailureKind2.valueOf(requireString("kind")))
        else -> error("Unknown BigDecimal differential expected type")
    }

    private fun JsonObject.requireArray(name: String): JsonArray = requireNotNull(this[name]).jsonArray
    private fun JsonObject.requireObject(name: String): JsonObject = requireNotNull(this[name]).jsonObject
    private fun JsonObject.requireString(name: String): String = requireNotNull(this[name]).jsonPrimitive.content
    private fun JsonObject.requireInt(name: String): Int = requireString(name).toInt()
    private fun JsonObject.requireLong(name: String): Long = requireString(name).toLong()
    private fun JsonObject.requireBoolean(name: String): Boolean = requireString(name).toBooleanStrict()
}

expect object BigDecimalDifferentialFixtureTextLoader {
    fun load(operation: BigDecimalDifferentialOperation): String
}

object BigDecimalDifferentialFixtureRepository {
    private val cache = mutableMapOf<BigDecimalDifferentialOperation, List<BigDecimalDifferentialCase>>()

    fun loadCases(operation: BigDecimalDifferentialOperation): List<BigDecimalDifferentialCase> = cache.getOrPut(operation) {
        val decoded = BigDecimalDifferentialFixtureJsonCodec.decode(BigDecimalDifferentialFixtureTextLoader.load(operation))
        require(decoded.operation == operation) { "Loaded ${decoded.operation} from ${operation.fixtureFileName}" }
        decoded.cases
    }

    fun loadAll(): Map<BigDecimalDifferentialOperation, List<BigDecimalDifferentialCase>> = BigDecimalDifferentialOperation.entries.associateWith(::loadCases)
}

object BigDecimalDifferentialExecutor {
    fun evaluate(case: BigDecimalDifferentialCase): BigDecimalDifferentialExpected = evaluate(case.operation, case.args)

    fun evaluate(
        operation: BigDecimalDifferentialOperation,
        args: List<BigDecimalDifferentialArg>,
    ): BigDecimalDifferentialExpected = try {
        when (operation) {
            BigDecimalDifferentialOperation.CONSTRUCTOR_STRING ->
                normalizeBigDecimal(BigDecimal(args.string(0)))

            BigDecimalDifferentialOperation.CONSTRUCTOR_BIGINT ->
                normalizeBigDecimal(BigDecimal(args.bigInt(0)))

            BigDecimalDifferentialOperation.CONSTRUCTOR_BIGINT_SCALE ->
                normalizeBigDecimal(BigDecimal(args.bigInt(0), args.int(1)))

            BigDecimalDifferentialOperation.CONSTRUCTOR_LONG ->
                normalizeBigDecimal(BigDecimal(args.long(0)))

            BigDecimalDifferentialOperation.CONSTRUCTOR_INT ->
                normalizeBigDecimal(BigDecimal(args.int(0)))

            BigDecimalDifferentialOperation.FACTORY_OF_STRING ->
                normalizeBigDecimal(bigDecimalOf(args.string(0)))

            BigDecimalDifferentialOperation.FACTORY_OF_BIGINT ->
                normalizeBigDecimal(bigDecimalOf(args.bigInt(0)))

            BigDecimalDifferentialOperation.FACTORY_OF_BIGINT_SCALE ->
                normalizeBigDecimal(bigDecimalOf(args.bigInt(0), args.int(1)))

            BigDecimalDifferentialOperation.FACTORY_OF_LONG ->
                normalizeBigDecimal(bigDecimalOf(args.long(0)))

            BigDecimalDifferentialOperation.FACTORY_OF_INT ->
                normalizeBigDecimal(bigDecimalOf(args.int(0)))

            BigDecimalDifferentialOperation.ADD ->
                normalizeBigDecimal(args.bigDec(0) + args.bigDec(1))

            BigDecimalDifferentialOperation.ADD_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).add(args.bigDec(1), args.mathContext(2)))

            BigDecimalDifferentialOperation.SUBTRACT ->
                normalizeBigDecimal(args.bigDec(0) - args.bigDec(1))

            BigDecimalDifferentialOperation.SUBTRACT_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).subtract(args.bigDec(1), args.mathContext(2)))

            BigDecimalDifferentialOperation.MULTIPLY ->
                normalizeBigDecimal(args.bigDec(0) * args.bigDec(1))

            BigDecimalDifferentialOperation.MULTIPLY_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).multiply(args.bigDec(1), args.mathContext(2)))

            BigDecimalDifferentialOperation.DIVIDE ->
                normalizeBigDecimal(args.bigDec(0) / args.bigDec(1))

            BigDecimalDifferentialOperation.DIVIDE_ROUNDING_MODE ->
                normalizeBigDecimal(args.bigDec(0).divide(args.bigDec(1), args.roundingMode(2)))

            BigDecimalDifferentialOperation.DIVIDE_SCALE_ROUNDING_MODE ->
                normalizeBigDecimal(args.bigDec(0).divide(args.bigDec(1), args.int(2), args.roundingMode(3)))

            BigDecimalDifferentialOperation.DIVIDE_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).divide(args.bigDec(1), args.mathContext(2)))

            BigDecimalDifferentialOperation.REM ->
                normalizeBigDecimal(args.bigDec(0) % args.bigDec(1))

            BigDecimalDifferentialOperation.REMAINDER_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).remainder(args.bigDec(1), args.mathContext(2)))

            BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER ->
                BigDecListExpected(args.bigDec(0).divideAndRemainder(args.bigDec(1)).map { it.toString() })

            BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER_MATH_CONTEXT ->
                BigDecListExpected(args.bigDec(0).divideAndRemainder(args.bigDec(1), args.mathContext(2)).map { it.toString() })

            BigDecimalDifferentialOperation.DIVIDE_TO_INTEGRAL_VALUE_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).divideToIntegralValue(args.bigDec(1), args.mathContext(2)))

            BigDecimalDifferentialOperation.POW ->
                normalizeBigDecimal(args.bigDec(0).pow(args.int(1)))

            BigDecimalDifferentialOperation.POW_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).pow(args.int(1), args.mathContext(2)))

            BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).sqrt(args.mathContext(1)))

            BigDecimalDifferentialOperation.ABS ->
                normalizeBigDecimal(args.bigDec(0).abs())

            BigDecimalDifferentialOperation.ABS_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).abs(args.mathContext(1)))

            BigDecimalDifferentialOperation.NEGATE ->
                normalizeBigDecimal(args.bigDec(0).negate())

            BigDecimalDifferentialOperation.NEGATE_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).negate(args.mathContext(1)))

            BigDecimalDifferentialOperation.PLUS ->
                normalizeBigDecimal(args.bigDec(0).plus())

            BigDecimalDifferentialOperation.PLUS_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).plus(args.mathContext(1)))

            BigDecimalDifferentialOperation.ROUND_MATH_CONTEXT ->
                normalizeBigDecimal(args.bigDec(0).round(args.mathContext(1)))

            BigDecimalDifferentialOperation.SIGNUM ->
                IntExpected2(args.bigDec(0).signum())

            BigDecimalDifferentialOperation.SCALE ->
                IntExpected2(args.bigDec(0).scale())

            BigDecimalDifferentialOperation.PRECISION ->
                IntExpected2(args.bigDec(0).precision())

            BigDecimalDifferentialOperation.UNSCALED_VALUE ->
                BigIntExpected2(args.bigDec(0).unscaledValue().toString())

            BigDecimalDifferentialOperation.SET_SCALE_EXACT ->
                normalizeBigDecimal(args.bigDec(0).setScale(args.int(1)))

            BigDecimalDifferentialOperation.SET_SCALE_ROUNDING ->
                normalizeBigDecimal(args.bigDec(0).setScale(args.int(1), args.roundingMode(2)))

            BigDecimalDifferentialOperation.MOVE_POINT_LEFT ->
                normalizeBigDecimal(args.bigDec(0).movePointLeft(args.int(1)))

            BigDecimalDifferentialOperation.MOVE_POINT_RIGHT ->
                normalizeBigDecimal(args.bigDec(0).movePointRight(args.int(1)))

            BigDecimalDifferentialOperation.SCALE_BY_POWER_OF_TEN ->
                normalizeBigDecimal(args.bigDec(0).scaleByPowerOfTen(args.int(1)))

            BigDecimalDifferentialOperation.STRIP_TRAILING_ZEROS ->
                normalizeBigDecimal(args.bigDec(0).stripTrailingZeros())

            BigDecimalDifferentialOperation.TO_STRING ->
                StringExpected2(args.bigDec(0).toString())

            BigDecimalDifferentialOperation.TO_ENGINEERING_STRING ->
                StringExpected2(args.bigDec(0).toEngineeringString())

            BigDecimalDifferentialOperation.TO_PLAIN_STRING ->
                StringExpected2(args.bigDec(0).toPlainString())

            BigDecimalDifferentialOperation.TO_BIG_INTEGER ->
                BigIntExpected2(args.bigDec(0).toBigInteger().toString())

            BigDecimalDifferentialOperation.TO_BIG_INTEGER_EXACT ->
                BigIntExpected2(args.bigDec(0).toBigIntegerExact().toString())

            BigDecimalDifferentialOperation.TO_INT ->
                IntExpected2(args.bigDec(0).toInt())

            BigDecimalDifferentialOperation.TO_INT_EXACT ->
                IntExpected2(args.bigDec(0).toIntExact())

            BigDecimalDifferentialOperation.TO_LONG ->
                LongExpected2(args.bigDec(0).toLong())

            BigDecimalDifferentialOperation.TO_LONG_EXACT ->
                LongExpected2(args.bigDec(0).toLongExact())

            BigDecimalDifferentialOperation.TO_DOUBLE_BITS ->
                DoubleBitsExpected2(args.bigDec(0).toDouble().toBits())

            BigDecimalDifferentialOperation.TO_FLOAT_BITS ->
                FloatBitsExpected(args.bigDec(0).toFloat().toBits())

            BigDecimalDifferentialOperation.MIN ->
                normalizeBigDecimal(args.bigDec(0).min(args.bigDec(1)))

            BigDecimalDifferentialOperation.MAX ->
                normalizeBigDecimal(args.bigDec(0).max(args.bigDec(1)))

            BigDecimalDifferentialOperation.COMPARE_TO ->
                IntExpected2(args.bigDec(0).compareTo(args.bigDec(1)))

            BigDecimalDifferentialOperation.EQUALS_BIGDECIMAL ->
                BooleanExpected2(args.bigDec(0) == args.bigDec(1))

            BigDecimalDifferentialOperation.EQUALS_NULL ->
                BooleanExpected2(args.bigDec(0).equals(null))

            BigDecimalDifferentialOperation.EQUALS_STRING ->
                BooleanExpected2(args.bigDec(0).equals(args.string(1)))

            BigDecimalDifferentialOperation.HASH_CODE ->
                IntExpected2(args.bigDec(0).hashCode())

            BigDecimalDifferentialOperation.ULP ->
                normalizeBigDecimal(args.bigDec(0).ulp())
        }
    } catch (throwable: Throwable) {
        FailureExpected2(classifyFailure(operation, args, throwable))
    }

    private fun normalizeBigDecimal(value: BigDecimal): BigDecimalDifferentialExpected = BigDecActual(value)

    private fun classifyFailure(
        operation: BigDecimalDifferentialOperation,
        args: List<BigDecimalDifferentialArg>,
        throwable: Throwable,
    ): FailureKind2 = when {
        operation == BigDecimalDifferentialOperation.CONSTRUCTOR_STRING ||
            operation == BigDecimalDifferentialOperation.FACTORY_OF_STRING ->
            FailureKind2.INVALID_FORMAT

        operation in setOf(
            BigDecimalDifferentialOperation.DIVIDE,
            BigDecimalDifferentialOperation.DIVIDE_ROUNDING_MODE,
            BigDecimalDifferentialOperation.DIVIDE_SCALE_ROUNDING_MODE,
            BigDecimalDifferentialOperation.DIVIDE_MATH_CONTEXT,
            BigDecimalDifferentialOperation.REM,
            BigDecimalDifferentialOperation.REMAINDER_MATH_CONTEXT,
            BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER,
            BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER_MATH_CONTEXT,
            BigDecimalDifferentialOperation.DIVIDE_TO_INTEGRAL_VALUE_MATH_CONTEXT,
        ) && args.bigDec(1).signum() == 0 -> FailureKind2.DIVIDE_BY_ZERO

        operation == BigDecimalDifferentialOperation.DIVIDE ||
            (operation == BigDecimalDifferentialOperation.DIVIDE_MATH_CONTEXT && args.mathContext(2).precision == 0) ->
            FailureKind2.NON_TERMINATING_DECIMAL

        operation == BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT && args.bigDec(0).signum() < 0 ->
            FailureKind2.NEGATIVE_SQUARE_ROOT

        operation == BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT &&
            args.mathContext(1).precision == 0 &&
            !hasFiniteExactSquareRoot(args.bigDec(0)) ->
            FailureKind2.NON_TERMINATING_SQUARE_ROOT

        operation in setOf(
            BigDecimalDifferentialOperation.SET_SCALE_EXACT,
            BigDecimalDifferentialOperation.DIVIDE_ROUNDING_MODE,
            BigDecimalDifferentialOperation.DIVIDE_SCALE_ROUNDING_MODE,
            BigDecimalDifferentialOperation.DIVIDE_MATH_CONTEXT,
            BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT,
            BigDecimalDifferentialOperation.TO_BIG_INTEGER_EXACT,
            BigDecimalDifferentialOperation.TO_INT_EXACT,
            BigDecimalDifferentialOperation.TO_LONG_EXACT,
        ) && (
            operation != BigDecimalDifferentialOperation.SQRT_MATH_CONTEXT ||
                args.mathContext(1).roundingMode == RoundingMode.UNNECESSARY
            ) -> FailureKind2.ROUNDING_NECESSARY

        operation == BigDecimalDifferentialOperation.POW && args.int(1) < 0 ->
            FailureKind2.NEGATIVE_EXPONENT

        operation in setOf(
            BigDecimalDifferentialOperation.DIVIDE_TO_INTEGRAL_VALUE_MATH_CONTEXT,
            BigDecimalDifferentialOperation.REMAINDER_MATH_CONTEXT,
            BigDecimalDifferentialOperation.DIVIDE_AND_REMAINDER_MATH_CONTEXT,
        ) -> FailureKind2.DIVISION_IMPOSSIBLE

        throwable is ArithmeticException -> FailureKind2.OTHER_ARITHMETIC
        throwable is IllegalArgumentException -> FailureKind2.OTHER_ILLEGAL_ARGUMENT
        else -> FailureKind2.OTHER
    }
}

private data class ExpectedBigDecimalMatcher(
    val scale: Int,
    val isZero: Boolean,
    val unscaledValue: BigInteger?,
    val trailingZeros: Int,
    val reducedValue: BigDecimal?,
) {
    fun matches(actual: BigDecimal): Boolean {
        if (actual.scale() != scale) return false
        if (isZero) return actual.signum() == 0
        if (trailingZeros > 0 && reducedValue != null) {
            if (trailingZeros <= 18) {
                val divisor = bigIntegerOf(10).pow(trailingZeros)
                val reduced = actual.unscaledValue().divideAndRemainder(divisor)
                return reduced[1].signum() == 0 && reduced[0] == reducedValue.unscaledValue()
            }
            return actual.setScale(scale - trailingZeros) == reducedValue
        }
        return actual.unscaledValue() == unscaledValue
    }
}

private fun parseExpectedBigDecimal(decimal: String): ExpectedBigDecimalMatcher {
    var index = 0
    var signPrefix = ""
    if (decimal[index] == '+' || decimal[index] == '-') {
        signPrefix = if (decimal[index] == '-') "-" else ""
        index++
    }

    val digits = StringBuilder()
    var seenPoint = false
    var fractionDigits = 0
    while (index < decimal.length) {
        val ch = decimal[index]
        when {
            ch in '0'..'9' -> {
                digits.append(ch)
                if (seenPoint) fractionDigits++
            }
            ch == '.' && !seenPoint -> seenPoint = true
            ch == 'e' || ch == 'E' -> break
            else -> throw NumberFormatException("Unexpected BigDecimal fixture digit '$ch'")
        }
        index++
    }

    require(digits.isNotEmpty()) { "Expected BigDecimal fixture has no digits: $decimal" }

    val exponent = if (index < decimal.length) parseExpectedExponent(decimal.substring(index + 1)) else 0L
    val scale = (fractionDigits.toLong() - exponent).also {
        require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Expected BigDecimal scale out of range: $decimal" }
    }.toInt()

    val digitString = digits.toString()
    val isZero = digitString.all { it == '0' }
    if (isZero) {
        return ExpectedBigDecimalMatcher(
            scale = scale,
            isZero = true,
            unscaledValue = bigIntegerOf(0),
            trailingZeros = digitString.length,
            reducedValue = null,
        )
    }

    val unscaledValue = BigInteger(signPrefix + digitString)
    val trailingZeros = digitString.length - digitString.trimEnd('0').length
    val reducedScale = scale.toLong() - trailingZeros.toLong()
    val reducedValue = if (
        trailingZeros > 0 &&
        reducedScale in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    ) {
        val reducedDigits = digitString.dropLast(trailingZeros)
        BigDecimal(BigInteger(signPrefix + reducedDigits), reducedScale.toInt())
    } else {
        null
    }

    return ExpectedBigDecimalMatcher(
        scale = scale,
        isZero = false,
        unscaledValue = unscaledValue,
        trailingZeros = trailingZeros,
        reducedValue = reducedValue,
    )
}

private fun parseExpectedExponent(value: String): Long {
    require(value.isNotEmpty()) { "Expected BigDecimal fixture exponent is empty" }
    var index = 0
    var sign = 1L
    when (value[index]) {
        '+' -> index++
        '-' -> {
            sign = -1L
            index++
        }
    }
    require(index < value.length) { "Expected BigDecimal fixture exponent is empty" }

    var exponent = 0L
    while (index < value.length) {
        val digit = value[index]
        require(digit in '0'..'9') { "Unexpected BigDecimal fixture exponent digit '$digit'" }
        if (exponent > (Long.MAX_VALUE - (digit - '0')) / 10L) {
            exponent = Long.MAX_VALUE
        } else {
            exponent = exponent * 10L + (digit - '0')
        }
        index++
    }
    return if (sign < 0L) -exponent else exponent
}

private fun hasFiniteExactSquareRoot(value: BigDecimal): Boolean {
    if (value.signum() < 0) return false
    if (value.signum() == 0) return true

    val stripped = value.stripTrailingZeros()
    if (stripped.scale() % 2 != 0) return false
    val root = stripped.unscaledValue().sqrt()
    return root * root == stripped.unscaledValue()
}

private fun List<BigDecimalDifferentialArg>.bigDec(index: Int): BigDecimal = when (val arg = get(index)) {
    is BigDecArg -> BigDecimal(arg.decimal)
    else -> error("Expected BigDecArg at $index, found ${arg::class.simpleName}")
}

private fun List<BigDecimalDifferentialArg>.bigInt(index: Int): BigInteger = when (val arg = get(index)) {
    is BigIntArg2 -> BigInteger(arg.decimal)
    else -> error("Expected BigIntArg2 at $index, found ${arg::class.simpleName}")
}

private fun List<BigDecimalDifferentialArg>.int(index: Int): Int = when (val arg = get(index)) {
    is IntArg2 -> arg.value
    else -> error("Expected IntArg2 at $index, found ${arg::class.simpleName}")
}

private fun List<BigDecimalDifferentialArg>.long(index: Int): Long = when (val arg = get(index)) {
    is LongArg2 -> arg.value
    else -> error("Expected LongArg2 at $index, found ${arg::class.simpleName}")
}

private fun List<BigDecimalDifferentialArg>.string(index: Int): String = when (val arg = get(index)) {
    is StringArg2 -> arg.value
    is BigDecArg -> arg.decimal
    else -> error("Expected StringArg2 at $index, found ${arg::class.simpleName}")
}

private fun List<BigDecimalDifferentialArg>.roundingMode(index: Int): RoundingMode = when (val arg = get(index)) {
    is RoundingModeArg -> RoundingMode.valueOf(arg.value)
    else -> error("Expected RoundingModeArg at $index, found ${arg::class.simpleName}")
}

private fun List<BigDecimalDifferentialArg>.mathContext(index: Int): MathContext = when (val arg = get(index)) {
    is MathContextArg -> MathContext(arg.value)
    else -> error("Expected MathContextArg at $index, found ${arg::class.simpleName}")
}
