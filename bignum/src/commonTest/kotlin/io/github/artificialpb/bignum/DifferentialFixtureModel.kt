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

enum class DifferentialGroup {
    CONSTRUCTION,
    FACTORY,
    ARITHMETIC,
    BITWISE,
    NUMBER_THEORY,
    CONVERSION,
    COMPARISON,
    RANGE,
}

enum class DifferentialOperation {
    CONSTRUCTOR_STRING,
    CONSTRUCTOR_STRING_RADIX,
    CONSTRUCTOR_BYTES,
    CONSTRUCTOR_BYTES_SLICE,
    CONSTANT_ZERO,
    CONSTANT_ONE,
    CONSTANT_TWO,
    CONSTANT_TEN,
    FACTORY_OF_STRING,
    FACTORY_OF_LONG,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    REM,
    ABS,
    POW,
    MOD,
    MOD_POW,
    MOD_INVERSE,
    GCD,
    DIVIDE_AND_REMAINDER,
    UNARY_MINUS,
    INC,
    DEC,
    LCM,
    AND,
    OR,
    XOR,
    NOT,
    AND_NOT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    TEST_BIT,
    SET_BIT,
    CLEAR_BIT,
    FLIP_BIT,
    GET_LOWEST_SET_BIT,
    BIT_LENGTH,
    BIT_COUNT,
    IS_PROBABLE_PRIME,
    NEXT_PROBABLE_PRIME,
    SQRT,
    TO_BYTE_ARRAY,
    TO_INT,
    TO_LONG,
    TO_DOUBLE_BITS,
    TO_STRING,
    TO_STRING_RADIX,
    SIGNUM,
    MIN,
    MAX,
    COMPARE_TO,
    EQUALS_BIGINT,
    EQUALS_NULL,
    EQUALS_STRING,
    HASH_CODE,
    RANGE_TO_LIST,
}

val DifferentialOperation.group: DifferentialGroup
    get() = when (this) {
        DifferentialOperation.CONSTRUCTOR_STRING,
        DifferentialOperation.CONSTRUCTOR_STRING_RADIX,
        DifferentialOperation.CONSTRUCTOR_BYTES,
        DifferentialOperation.CONSTRUCTOR_BYTES_SLICE,
            -> DifferentialGroup.CONSTRUCTION

        DifferentialOperation.CONSTANT_ZERO,
        DifferentialOperation.CONSTANT_ONE,
        DifferentialOperation.CONSTANT_TWO,
        DifferentialOperation.CONSTANT_TEN,
        DifferentialOperation.FACTORY_OF_STRING,
        DifferentialOperation.FACTORY_OF_LONG,
            -> DifferentialGroup.FACTORY

        DifferentialOperation.ADD,
        DifferentialOperation.SUBTRACT,
        DifferentialOperation.MULTIPLY,
        DifferentialOperation.DIVIDE,
        DifferentialOperation.REM,
        DifferentialOperation.ABS,
        DifferentialOperation.POW,
        DifferentialOperation.MOD,
        DifferentialOperation.MOD_POW,
        DifferentialOperation.MOD_INVERSE,
        DifferentialOperation.GCD,
        DifferentialOperation.DIVIDE_AND_REMAINDER,
        DifferentialOperation.UNARY_MINUS,
        DifferentialOperation.INC,
        DifferentialOperation.DEC,
        DifferentialOperation.LCM,
            -> DifferentialGroup.ARITHMETIC

        DifferentialOperation.AND,
        DifferentialOperation.OR,
        DifferentialOperation.XOR,
        DifferentialOperation.NOT,
        DifferentialOperation.AND_NOT,
        DifferentialOperation.SHIFT_LEFT,
        DifferentialOperation.SHIFT_RIGHT,
        DifferentialOperation.TEST_BIT,
        DifferentialOperation.SET_BIT,
        DifferentialOperation.CLEAR_BIT,
        DifferentialOperation.FLIP_BIT,
        DifferentialOperation.GET_LOWEST_SET_BIT,
        DifferentialOperation.BIT_LENGTH,
        DifferentialOperation.BIT_COUNT,
            -> DifferentialGroup.BITWISE

        DifferentialOperation.IS_PROBABLE_PRIME,
        DifferentialOperation.NEXT_PROBABLE_PRIME,
        DifferentialOperation.SQRT,
            -> DifferentialGroup.NUMBER_THEORY

        DifferentialOperation.TO_BYTE_ARRAY,
        DifferentialOperation.TO_INT,
        DifferentialOperation.TO_LONG,
        DifferentialOperation.TO_DOUBLE_BITS,
        DifferentialOperation.TO_STRING,
        DifferentialOperation.TO_STRING_RADIX,
        DifferentialOperation.SIGNUM,
            -> DifferentialGroup.CONVERSION

        DifferentialOperation.MIN,
        DifferentialOperation.MAX,
        DifferentialOperation.COMPARE_TO,
        DifferentialOperation.EQUALS_BIGINT,
        DifferentialOperation.EQUALS_NULL,
        DifferentialOperation.EQUALS_STRING,
        DifferentialOperation.HASH_CODE,
            -> DifferentialGroup.COMPARISON

        DifferentialOperation.RANGE_TO_LIST ->
            DifferentialGroup.RANGE
    }

val DifferentialOperation.fixtureFileName: String
    get() = "${name.lowercase()}.json"

val DifferentialGroup.operations: List<DifferentialOperation>
    get() = DifferentialOperation.entries.filter { it.group == this }

sealed interface DifferentialArg

data class BigIntArg(val decimal: String) : DifferentialArg

data class IntArg(val value: Int) : DifferentialArg

data class LongArg(val value: Long) : DifferentialArg

data class StringArg(val value: String) : DifferentialArg

data class ByteListArg(val bytes: List<Int>) : DifferentialArg

sealed interface DifferentialExpected

data class BigIntExpected(val decimal: String) : DifferentialExpected

data class BooleanExpected(val value: Boolean) : DifferentialExpected

data class IntExpected(val value: Int) : DifferentialExpected

data class LongExpected(val value: Long) : DifferentialExpected

data class DoubleBitsExpected(val bits: Long) : DifferentialExpected

data class StringExpected(val value: String) : DifferentialExpected

data class ByteListExpected(val bytes: List<Int>) : DifferentialExpected

data class BigIntListExpected(val decimals: List<String>) : DifferentialExpected

data class StringListExpected(val values: List<String>) : DifferentialExpected

data class FailureExpected(val kind: FailureKind) : DifferentialExpected

enum class FailureKind {
    INVALID_FORMAT,
    INVALID_RADIX,
    INVALID_SLICE,
    DIVIDE_BY_ZERO,
    NON_POSITIVE_MODULUS,
    NEGATIVE_EXPONENT,
    NEGATIVE_BIT_INDEX,
    NEGATIVE_INPUT,
    NON_INVERTIBLE,
    OTHER_ARITHMETIC,
    OTHER_ILLEGAL_ARGUMENT,
    OTHER,
}

data class DifferentialCase(
    val id: String,
    val group: DifferentialGroup,
    val operation: DifferentialOperation,
    val args: List<DifferentialArg>,
    val expected: DifferentialExpected,
) {
    override fun toString(): String = id
}

data class DifferentialFixtureFile(
    val operation: DifferentialOperation,
    val cases: List<DifferentialCase>,
)

object DifferentialFixtureJsonCodec {
    private val json = Json {
        prettyPrint = true
    }

    fun encode(file: DifferentialFixtureFile): String =
        json.encodeToString(JsonElement.serializer(), file.toJson())

    fun decode(text: String): DifferentialFixtureFile {
        val root = json.parseToJsonElement(text).jsonObject
        val operation = DifferentialOperation.valueOf(root.requireString("operation"))
        val group = DifferentialGroup.valueOf(root.requireString("group"))
        require(group == operation.group) {
            "Fixture group $group does not match operation ${operation.name}"
        }
        val cases = root.requireArray("cases").map { it.jsonObject.toCase(operation, group) }
        return DifferentialFixtureFile(operation, cases)
    }

    private fun DifferentialFixtureFile.toJson(): JsonObject = buildJsonObject {
        put("operation", JsonPrimitive(operation.name))
        put("group", JsonPrimitive(operation.group.name))
        put("cases", buildJsonArray {
            cases.forEach { add(it.toJson()) }
        })
    }

    private fun DifferentialCase.toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("args", buildJsonArray {
            args.forEach { add(it.toJson()) }
        })
        put("expected", expected.toJson())
    }

    private fun DifferentialArg.toJson(): JsonObject = when (this) {
        is BigIntArg -> buildJsonObject {
            put("type", JsonPrimitive("big_int"))
            put("decimal", JsonPrimitive(decimal))
        }

        is IntArg -> buildJsonObject {
            put("type", JsonPrimitive("int"))
            put("value", JsonPrimitive(value))
        }

        is LongArg -> buildJsonObject {
            put("type", JsonPrimitive("long"))
            put("value", JsonPrimitive(value))
        }

        is StringArg -> buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("value", JsonPrimitive(value))
        }

        is ByteListArg -> buildJsonObject {
            put("type", JsonPrimitive("byte_list"))
            put("bytes", buildJsonArray {
                bytes.forEach { add(JsonPrimitive(it)) }
            })
        }
    }

    private fun DifferentialExpected.toJson(): JsonObject = when (this) {
        is BigIntExpected -> buildJsonObject {
            put("type", JsonPrimitive("big_int"))
            put("decimal", JsonPrimitive(decimal))
        }

        is BooleanExpected -> buildJsonObject {
            put("type", JsonPrimitive("boolean"))
            put("value", JsonPrimitive(value))
        }

        is IntExpected -> buildJsonObject {
            put("type", JsonPrimitive("int"))
            put("value", JsonPrimitive(value))
        }

        is LongExpected -> buildJsonObject {
            put("type", JsonPrimitive("long"))
            put("value", JsonPrimitive(value))
        }

        is DoubleBitsExpected -> buildJsonObject {
            put("type", JsonPrimitive("double_bits"))
            put("bits", JsonPrimitive(bits))
        }

        is StringExpected -> buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("value", JsonPrimitive(value))
        }

        is ByteListExpected -> buildJsonObject {
            put("type", JsonPrimitive("byte_list"))
            put("bytes", buildJsonArray {
                bytes.forEach { add(JsonPrimitive(it)) }
            })
        }

        is BigIntListExpected -> buildJsonObject {
            put("type", JsonPrimitive("big_int_list"))
            put("values", buildJsonArray {
                decimals.forEach { add(JsonPrimitive(it)) }
            })
        }

        is StringListExpected -> buildJsonObject {
            put("type", JsonPrimitive("string_list"))
            put("values", buildJsonArray {
                values.forEach { add(JsonPrimitive(it)) }
            })
        }

        is FailureExpected -> buildJsonObject {
            put("type", JsonPrimitive("failure"))
            put("kind", JsonPrimitive(kind.name))
        }
    }

    private fun JsonObject.toCase(
        operation: DifferentialOperation,
        group: DifferentialGroup,
    ): DifferentialCase = DifferentialCase(
        id = requireString("id"),
        group = group,
        operation = operation,
        args = requireArray("args").map { it.jsonObject.toArg() },
        expected = requireObject("expected").toExpected(),
    )

    private fun JsonObject.toArg(): DifferentialArg = when (requireString("type")) {
        "big_int" -> BigIntArg(requireString("decimal"))
        "int" -> IntArg(requireInt("value"))
        "long" -> LongArg(requireLong("value"))
        "string" -> StringArg(requireString("value"))
        "byte_list" -> ByteListArg(requireArray("bytes").map { it.jsonPrimitive.content.toInt() })
        else -> error("Unknown differential arg type")
    }

    private fun JsonObject.toExpected(): DifferentialExpected = when (requireString("type")) {
        "big_int" -> BigIntExpected(requireString("decimal"))
        "boolean" -> BooleanExpected(requireBoolean("value"))
        "int" -> IntExpected(requireInt("value"))
        "long" -> LongExpected(requireLong("value"))
        "double_bits" -> DoubleBitsExpected(requireLong("bits"))
        "string" -> StringExpected(requireString("value"))
        "byte_list" -> ByteListExpected(requireArray("bytes").map { it.jsonPrimitive.content.toInt() })
        "big_int_list" -> BigIntListExpected(requireArray("values").map { it.jsonPrimitive.content })
        "string_list" -> StringListExpected(requireArray("values").map { it.jsonPrimitive.content })
        "failure" -> FailureExpected(FailureKind.valueOf(requireString("kind")))
        else -> error("Unknown differential expected type")
    }

    private fun JsonObject.requireArray(name: String): JsonArray =
        requireNotNull(this[name]) { "Missing JSON array: $name" }.jsonArray

    private fun JsonObject.requireObject(name: String): JsonObject =
        requireNotNull(this[name]) { "Missing JSON object: $name" }.jsonObject

    private fun JsonObject.requireString(name: String): String =
        requireNotNull(this[name]) { "Missing JSON string: $name" }.jsonPrimitive.content

    private fun JsonObject.requireInt(name: String): Int =
        requireString(name).toInt()

    private fun JsonObject.requireLong(name: String): Long =
        requireString(name).toLong()

    private fun JsonObject.requireBoolean(name: String): Boolean =
        requireNotNull(this[name]) { "Missing JSON boolean: $name" }.jsonPrimitive.content.toBooleanStrict()
}

expect object DifferentialFixtureTextLoader {
    fun load(operation: DifferentialOperation): String
}

object DifferentialFixtureRepository {
    private val cache = mutableMapOf<DifferentialOperation, List<DifferentialCase>>()

    fun loadCases(operation: DifferentialOperation): List<DifferentialCase> =
        cache.getOrPut(operation) {
            val decoded = DifferentialFixtureJsonCodec.decode(DifferentialFixtureTextLoader.load(operation))
            require(decoded.operation == operation) {
                "Loaded ${decoded.operation} from ${operation.fixtureFileName}"
            }
            decoded.cases
        }

    fun loadAll(): Map<DifferentialOperation, List<DifferentialCase>> =
        DifferentialOperation.entries.associateWith(::loadCases)
}

object DifferentialExecutor {
    fun evaluate(case: DifferentialCase): DifferentialExpected = evaluate(case.operation, case.args)

    fun evaluate(
        operation: DifferentialOperation,
        args: List<DifferentialArg>,
    ): DifferentialExpected = try {
        when (operation) {
            DifferentialOperation.CONSTRUCTOR_STRING ->
                normalizeBigInt(BigInteger(args.string(0)))

            DifferentialOperation.CONSTRUCTOR_STRING_RADIX ->
                normalizeBigInt(BigInteger(args.string(0), args.int(1)))

            DifferentialOperation.CONSTRUCTOR_BYTES ->
                normalizeBigInt(BigInteger(args.byteArray(0)))

            DifferentialOperation.CONSTRUCTOR_BYTES_SLICE ->
                normalizeBigInt(BigInteger(args.byteArray(0), args.int(1), args.int(2)))

            DifferentialOperation.CONSTANT_ZERO ->
                normalizeBigInt(BigIntegers.ZERO)

            DifferentialOperation.CONSTANT_ONE ->
                normalizeBigInt(BigIntegers.ONE)

            DifferentialOperation.CONSTANT_TWO ->
                normalizeBigInt(BigIntegers.TWO)

            DifferentialOperation.CONSTANT_TEN ->
                normalizeBigInt(BigIntegers.TEN)

            DifferentialOperation.FACTORY_OF_STRING ->
                normalizeBigInt(BigIntegers.of(args.string(0)))

            DifferentialOperation.FACTORY_OF_LONG ->
                normalizeBigInt(BigIntegers.of(args.long(0)))

            DifferentialOperation.ADD ->
                normalizeBigInt(args.bigInt(0) + args.bigInt(1))

            DifferentialOperation.SUBTRACT ->
                normalizeBigInt(args.bigInt(0) - args.bigInt(1))

            DifferentialOperation.MULTIPLY ->
                normalizeBigInt(args.bigInt(0) * args.bigInt(1))

            DifferentialOperation.DIVIDE ->
                normalizeBigInt(args.bigInt(0) / args.bigInt(1))

            DifferentialOperation.REM ->
                normalizeBigInt(args.bigInt(0) % args.bigInt(1))

            DifferentialOperation.ABS ->
                normalizeBigInt(args.bigInt(0).abs())

            DifferentialOperation.POW ->
                normalizeBigInt(args.bigInt(0).pow(args.int(1)))

            DifferentialOperation.MOD ->
                normalizeBigInt(args.bigInt(0).mod(args.bigInt(1)))

            DifferentialOperation.MOD_POW ->
                normalizeBigInt(args.bigInt(0).modPow(args.bigInt(1), args.bigInt(2)))

            DifferentialOperation.MOD_INVERSE ->
                normalizeBigInt(args.bigInt(0).modInverse(args.bigInt(1)))

            DifferentialOperation.GCD ->
                normalizeBigInt(args.bigInt(0).gcd(args.bigInt(1)))

            DifferentialOperation.DIVIDE_AND_REMAINDER ->
                BigIntListExpected(args.bigInt(0).divideAndRemainder(args.bigInt(1)).map { it.toString() })

            DifferentialOperation.UNARY_MINUS ->
                normalizeBigInt(-args.bigInt(0))

            DifferentialOperation.INC ->
                normalizeBigInt(args.bigInt(0).inc())

            DifferentialOperation.DEC ->
                normalizeBigInt(args.bigInt(0).dec())

            DifferentialOperation.LCM ->
                normalizeBigInt(args.bigInt(0).lcm(args.bigInt(1)))

            DifferentialOperation.AND ->
                normalizeBigInt(args.bigInt(0).and(args.bigInt(1)))

            DifferentialOperation.OR ->
                normalizeBigInt(args.bigInt(0).or(args.bigInt(1)))

            DifferentialOperation.XOR ->
                normalizeBigInt(args.bigInt(0).xor(args.bigInt(1)))

            DifferentialOperation.NOT ->
                normalizeBigInt(args.bigInt(0).not())

            DifferentialOperation.AND_NOT ->
                normalizeBigInt(args.bigInt(0).andNot(args.bigInt(1)))

            DifferentialOperation.SHIFT_LEFT ->
                normalizeBigInt(args.bigInt(0).shiftLeft(args.int(1)))

            DifferentialOperation.SHIFT_RIGHT ->
                normalizeBigInt(args.bigInt(0).shiftRight(args.int(1)))

            DifferentialOperation.TEST_BIT ->
                BooleanExpected(args.bigInt(0).testBit(args.int(1)))

            DifferentialOperation.SET_BIT ->
                normalizeBigInt(args.bigInt(0).setBit(args.int(1)))

            DifferentialOperation.CLEAR_BIT ->
                normalizeBigInt(args.bigInt(0).clearBit(args.int(1)))

            DifferentialOperation.FLIP_BIT ->
                normalizeBigInt(args.bigInt(0).flipBit(args.int(1)))

            DifferentialOperation.GET_LOWEST_SET_BIT ->
                IntExpected(args.bigInt(0).getLowestSetBit())

            DifferentialOperation.BIT_LENGTH ->
                IntExpected(args.bigInt(0).bitLength())

            DifferentialOperation.BIT_COUNT ->
                IntExpected(args.bigInt(0).bitCount())

            DifferentialOperation.IS_PROBABLE_PRIME ->
                BooleanExpected(args.bigInt(0).isProbablePrime(args.int(1)))

            DifferentialOperation.NEXT_PROBABLE_PRIME ->
                normalizeBigInt(args.bigInt(0).nextProbablePrime())

            DifferentialOperation.SQRT ->
                normalizeBigInt(args.bigInt(0).sqrt())

            DifferentialOperation.TO_BYTE_ARRAY ->
                ByteListExpected(args.bigInt(0).toByteArray().map { it.toInt() })

            DifferentialOperation.TO_INT ->
                IntExpected(args.bigInt(0).toInt())

            DifferentialOperation.TO_LONG ->
                LongExpected(args.bigInt(0).toLong())

            DifferentialOperation.TO_DOUBLE_BITS ->
                DoubleBitsExpected(args.bigInt(0).toDouble().toBits())

            DifferentialOperation.TO_STRING ->
                StringExpected(args.bigInt(0).toString())

            DifferentialOperation.TO_STRING_RADIX ->
                StringExpected(args.bigInt(0).toString(args.int(1)))

            DifferentialOperation.SIGNUM ->
                IntExpected(args.bigInt(0).signum())

            DifferentialOperation.MIN ->
                normalizeBigInt(args.bigInt(0).min(args.bigInt(1)))

            DifferentialOperation.MAX ->
                normalizeBigInt(args.bigInt(0).max(args.bigInt(1)))

            DifferentialOperation.COMPARE_TO ->
                IntExpected(args.bigInt(0).compareTo(args.bigInt(1)))

            DifferentialOperation.EQUALS_BIGINT ->
                BooleanExpected(args.bigInt(0) == args.bigInt(1))

            DifferentialOperation.EQUALS_NULL ->
                BooleanExpected(args.bigInt(0).equals(null))

            DifferentialOperation.EQUALS_STRING ->
                BooleanExpected(args.bigInt(0).equals(args.string(1)))

            DifferentialOperation.HASH_CODE ->
                IntExpected(args.bigInt(0).hashCode())

            DifferentialOperation.RANGE_TO_LIST ->
                StringListExpected((args.bigInt(0)..args.bigInt(1)).map { it.toString() })
        }
    } catch (throwable: Throwable) {
        FailureExpected(classifyFailure(operation, args, throwable))
    }

    private fun normalizeBigInt(value: BigInteger): DifferentialExpected = BigIntExpected(value.toString())

    private fun classifyFailure(
        operation: DifferentialOperation,
        args: List<DifferentialArg>,
        throwable: Throwable,
    ): FailureKind = when {
        operation == DifferentialOperation.CONSTRUCTOR_STRING_RADIX &&
            args.int(1) !in 2..36 -> FailureKind.INVALID_RADIX

        operation == DifferentialOperation.TO_STRING_RADIX &&
            args.int(1) !in 2..36 -> FailureKind.INVALID_RADIX

        operation == DifferentialOperation.CONSTRUCTOR_BYTES_SLICE &&
            !isValidSlice(args.bytes(0).size, args.int(1), args.int(2)) -> FailureKind.INVALID_SLICE

        operation in setOf(
            DifferentialOperation.CONSTRUCTOR_STRING,
            DifferentialOperation.CONSTRUCTOR_STRING_RADIX,
            DifferentialOperation.CONSTRUCTOR_BYTES,
            DifferentialOperation.CONSTRUCTOR_BYTES_SLICE,
            DifferentialOperation.FACTORY_OF_STRING,
        ) -> FailureKind.INVALID_FORMAT

        operation in setOf(
            DifferentialOperation.DIVIDE,
            DifferentialOperation.REM,
            DifferentialOperation.DIVIDE_AND_REMAINDER,
        ) && args.bigInt(1).signum() == 0 -> FailureKind.DIVIDE_BY_ZERO

        operation in setOf(
            DifferentialOperation.MOD,
            DifferentialOperation.MOD_POW,
            DifferentialOperation.MOD_INVERSE,
        ) && args.bigInt(operation.bigIntModulusIndex).signum() <= 0 -> FailureKind.NON_POSITIVE_MODULUS

        operation == DifferentialOperation.POW &&
            args.int(1) < 0 -> FailureKind.NEGATIVE_EXPONENT

        operation in setOf(
            DifferentialOperation.TEST_BIT,
            DifferentialOperation.SET_BIT,
            DifferentialOperation.CLEAR_BIT,
            DifferentialOperation.FLIP_BIT,
        ) && args.int(1) < 0 -> FailureKind.NEGATIVE_BIT_INDEX

        operation in setOf(
            DifferentialOperation.NEXT_PROBABLE_PRIME,
            DifferentialOperation.SQRT,
        ) && args.bigInt(0).signum() < 0 -> FailureKind.NEGATIVE_INPUT

        operation == DifferentialOperation.MOD_INVERSE ->
            FailureKind.NON_INVERTIBLE

        operation == DifferentialOperation.MOD_POW &&
            args.bigInt(1).signum() < 0 -> FailureKind.NON_INVERTIBLE

        throwable is ArithmeticException -> FailureKind.OTHER_ARITHMETIC
        throwable is IllegalArgumentException -> FailureKind.OTHER_ILLEGAL_ARGUMENT
        else -> FailureKind.OTHER
    }

    private val DifferentialOperation.bigIntModulusIndex: Int
        get() = when (this) {
            DifferentialOperation.MOD -> 1
            DifferentialOperation.MOD_POW -> 2
            DifferentialOperation.MOD_INVERSE -> 1
            else -> error("No modulus argument for $this")
        }

    private fun isValidSlice(size: Int, off: Int, len: Int): Boolean =
        off >= 0 && len >= 0 && off <= size && len <= size - off
}

private fun List<DifferentialArg>.bigInt(index: Int): BigInteger = when (val arg = get(index)) {
    is BigIntArg -> BigInteger(arg.decimal)
    else -> error("Expected BigIntArg at $index, found ${arg::class.simpleName}")
}

private fun List<DifferentialArg>.int(index: Int): Int = when (val arg = get(index)) {
    is IntArg -> arg.value
    else -> error("Expected IntArg at $index, found ${arg::class.simpleName}")
}

private fun List<DifferentialArg>.long(index: Int): Long = when (val arg = get(index)) {
    is LongArg -> arg.value
    else -> error("Expected LongArg at $index, found ${arg::class.simpleName}")
}

private fun List<DifferentialArg>.string(index: Int): String = when (val arg = get(index)) {
    is StringArg -> arg.value
    else -> error("Expected StringArg at $index, found ${arg::class.simpleName}")
}

private fun List<DifferentialArg>.bytes(index: Int): List<Int> = when (val arg = get(index)) {
    is ByteListArg -> arg.bytes
    else -> error("Expected ByteListArg at $index, found ${arg::class.simpleName}")
}

private fun List<DifferentialArg>.byteArray(index: Int): ByteArray =
    bytes(index).map { it.toByte() }.toByteArray()
