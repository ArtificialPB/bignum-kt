package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.MathContext
import io.github.artificialpb.bignum.RoundingMode
import io.github.artificialpb.bignum.bigDecimalOf
import io.github.artificialpb.bignum.bigIntegerOf
import io.github.artificialpb.bignum.div
import io.github.artificialpb.bignum.inc
import io.github.artificialpb.bignum.minus
import io.github.artificialpb.bignum.plus
import io.github.artificialpb.bignum.rangeTo
import io.github.artificialpb.bignum.rem
import io.github.artificialpb.bignum.times
import io.github.artificialpb.bignum.unaryMinus
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

data class BenchmarkProfile(
    val operandA: String,
    val operandB: String,
    val powBase: String,
    val powExponent: Int,
    val shiftAmount: Int,
    val bitIndex: Int,
    val rangeWidth: Int,
    val intValue: Int,
    val longValue: Long,
    val mersenneExponent: Int,
)

data class BitwiseBenchmarkFixture(
    val left: BigInteger,
    val right: BigInteger,
    val testBitInput: BigInteger,
    val setBitInput: BigInteger,
    val clearBitInput: BigInteger,
    val flipBitInput: BigInteger,
    val lowestSetBitInput: BigInteger,
)

data class BenchmarkFixture(
    val constructorString: String,
    val constructorRadixString: String,
    val constructorRadix: Int,
    val byteArrayInput: ByteArray,
    val slicedByteArrayInput: ByteArray,
    val sliceOffset: Int,
    val sliceLength: Int,
    val factoryString: String,
    val factoryInt: Int,
    val factoryLong: Long,
    val left: BigInteger,
    val right: BigInteger,
    val negativeLeft: BigInteger,
    val equalLeft: BigInteger,
    val compareTarget: BigInteger,
    val dividend: BigInteger,
    val divisor: BigInteger,
    val modulus: BigInteger,
    val inverseBase: BigInteger,
    val modExponent: BigInteger,
    val powBase: BigInteger,
    val powExponent: Int,
    val positiveBitwise: BitwiseBenchmarkFixture,
    val negativeBitwise: BitwiseBenchmarkFixture,
    val bitIndex: Int,
    val shiftAmount: Int,
    val probablePrime: BigInteger,
    val nextPrimeStart: BigInteger,
    val sqrtInput: BigInteger,
    val rangeStart: BigInteger,
    val rangeEnd: BigInteger,
)

data class BigDecimalBenchmarkFixture(
    val constructorString: String,
    val constructorBigInteger: BigInteger,
    val constructorScale: Int,
    val factoryString: String,
    val factoryInt: Int,
    val factoryLong: Long,
    val factoryBigInteger: BigInteger,
    val factoryBigIntegerScale: Int,
    val left: BigDecimal,
    val right: BigDecimal,
    val negativeLeft: BigDecimal,
    val zeroDividend: BigDecimal,
    val equalLeft: BigDecimal,
    val compareTarget: BigDecimal,
    val equalScaleLeft: BigDecimal,
    val equalScaleRight: BigDecimal,
    val largeScaleGapLeft: BigDecimal,
    val largeScaleGapRight: BigDecimal,
    val singleLimbOperand: BigDecimal,
    val multiLimbOperand: BigDecimal,
    val dividend: BigDecimal,
    val divisor: BigDecimal,
    val genericExactDividend: BigDecimal,
    val genericExactDivisor: BigDecimal,
    val negativeScaleSmallDividend: BigDecimal,
    val negativeScaleSmallDivisor: BigDecimal,
    val positiveScaleGenericDividend: BigDecimal,
    val positiveScaleGenericDivisor: BigDecimal,
    val negativeScaleGenericDividend: BigDecimal,
    val negativeScaleGenericDivisor: BigDecimal,
    val roundedDivideDividend: BigDecimal,
    val roundedDivideDivisor: BigDecimal,
    val powBase: BigDecimal,
    val sqrtInput: BigDecimal,
    val powExponent: Int,
    val mathContext: MathContext,
    val integralMathContext: MathContext,
    val scaleTarget: Int,
    val scaleMoveAmount: Int,
    val roundingMode: RoundingMode,
)

internal fun decimalPattern(pattern: String, repeats: Int, suffix: String): String = buildString {
    repeat(repeats) { append(pattern) }
    append(suffix)
}

val benchmarkProfiles = mapOf(
    "small" to BenchmarkProfile(
        operandA = "12345678901234567890",
        operandB = "9876543210987654321",
        powBase = "123456789",
        powExponent = 5,
        shiftAmount = 13,
        bitIndex = 11,
        rangeWidth = 32,
        intValue = 123456789,
        longValue = 1234567890123456789L,
        mersenneExponent = 61,
    ),
    "medium" to BenchmarkProfile(
        operandA = decimalPattern("12345678901234567890", 4, "3141592653589793"),
        operandB = decimalPattern("99887766554433221100", 4, "2718281828459045"),
        powBase = decimalPattern("12345678901234567890", 2, "12345"),
        powExponent = 3,
        shiftAmount = 29,
        bitIndex = 47,
        rangeWidth = 64,
        intValue = 987654321,
        longValue = 876543210987654321L,
        mersenneExponent = 127,
    ),
    "large" to BenchmarkProfile(
        operandA = decimalPattern("12345678901234567890", 16, "246813579"),
        operandB = decimalPattern("98765432100123456789", 16, "135792468"),
        powBase = decimalPattern("31415926535897932384", 3, "6264338"),
        powExponent = 2,
        shiftAmount = 61,
        bitIndex = 89,
        rangeWidth = 96,
        intValue = 2_000_000_123,
        longValue = 8_070_450_532_247_928_83L,
        mersenneExponent = 521,
    ),
)

internal object BenchmarkFixtures {
    private val zero = bigIntegerOf(0)
    private val one = bigIntegerOf(1)
    private val two = bigIntegerOf(2)
    private val three = bigIntegerOf(3)
    private val hundred = bigIntegerOf(100)

    fun create(profileName: String): BenchmarkFixture {
        val profile = benchmarkProfiles[profileName]
            ?: error("Unknown benchmark profile: $profileName")
        val left = BigInteger(profile.operandA)
        val right = BigInteger(profile.operandB)
        val negativeLeft = -left
        val equalLeft = BigInteger(left.toString())
        val compareTarget = left + one
        val constructorRadixString = left.toString(16)
        val byteArrayInput = left.toByteArray()
        val sliceOffset = 3
        val slicedByteArrayInput = ByteArray(byteArrayInput.size + sliceOffset + 2)
        byteArrayInput.copyInto(slicedByteArrayInput, destinationOffset = sliceOffset)

        val divisor = right.abs() + one
        val dividend = (left.abs() + hundred) * divisor + three
        val modulus = mersennePrime(profile.mersenneExponent)
        val inverseBase = firstCoprime(modulus)
        val modExponent = bigIntegerOf(65537)
        val powBase = BigInteger(profile.powBase)

        val shiftedOdd = left.abs().or(one).shiftLeft((profile.shiftAmount / 2).coerceAtLeast(1))
        val positiveBitwiseLeft = shiftedOdd.or(one.shiftLeft(profile.bitIndex))
        val positiveBitwiseRight = right.abs().or(three)
        val positiveBitwise = BitwiseBenchmarkFixture(
            left = positiveBitwiseLeft,
            right = positiveBitwiseRight,
            testBitInput = positiveBitwiseLeft.setBit(profile.bitIndex),
            setBitInput = positiveBitwiseLeft.clearBit(profile.bitIndex),
            clearBitInput = positiveBitwiseLeft.setBit(profile.bitIndex),
            flipBitInput = positiveBitwiseLeft,
            lowestSetBitInput = three.shiftLeft((profile.bitIndex / 2).coerceAtLeast(1)),
        )
        val negativeBitwiseLeft = -positiveBitwiseLeft
        val negativeBitwiseRight = -positiveBitwiseRight
        val negativeBitwise = BitwiseBenchmarkFixture(
            left = negativeBitwiseLeft,
            right = negativeBitwiseRight,
            testBitInput = negativeBitwiseLeft.setBit(profile.bitIndex),
            setBitInput = negativeBitwiseLeft.clearBit(profile.bitIndex),
            clearBitInput = negativeBitwiseLeft.setBit(profile.bitIndex),
            flipBitInput = negativeBitwiseLeft,
            lowestSetBitInput = -three.shiftLeft((profile.bitIndex / 2).coerceAtLeast(1)),
        )

        val probablePrime = modulus
        val nextPrimeStart = probablePrime - hundred
        val sqrtInput = (left.abs() + one) * (right.abs() + one)
        val rangeStart = zero
        val rangeEnd = bigIntegerOf(profile.rangeWidth)

        return BenchmarkFixture(
            constructorString = profile.operandA,
            constructorRadixString = constructorRadixString,
            constructorRadix = 16,
            byteArrayInput = byteArrayInput,
            slicedByteArrayInput = slicedByteArrayInput,
            sliceOffset = sliceOffset,
            sliceLength = byteArrayInput.size,
            factoryString = profile.operandB,
            factoryInt = profile.intValue,
            factoryLong = profile.longValue,
            left = left,
            right = right,
            negativeLeft = negativeLeft,
            equalLeft = equalLeft,
            compareTarget = compareTarget,
            dividend = dividend,
            divisor = divisor,
            modulus = modulus,
            inverseBase = inverseBase,
            modExponent = modExponent,
            powBase = powBase,
            powExponent = profile.powExponent,
            positiveBitwise = positiveBitwise,
            negativeBitwise = negativeBitwise,
            bitIndex = profile.bitIndex,
            shiftAmount = profile.shiftAmount,
            probablePrime = probablePrime,
            nextPrimeStart = nextPrimeStart,
            sqrtInput = sqrtInput,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
        )
    }

    private fun mersennePrime(exponent: Int): BigInteger = two.pow(exponent) - one

    private fun firstCoprime(modulus: BigInteger): BigInteger {
        var candidate = three
        while (candidate.gcd(modulus) != one) {
            candidate += two
        }
        return candidate
    }
}

internal object BigDecimalBenchmarkFixtures {
    private val one = bigIntegerOf(1)
    private val three = bigIntegerOf(3)
    private val seven = bigIntegerOf(7)
    private val nine = bigIntegerOf(9)
    private val hundred = bigIntegerOf(100)
    private val tenToTwenty = bigIntegerOf(10).pow(20)

    fun create(profileName: String): BigDecimalBenchmarkFixture {
        val profile = benchmarkProfiles[profileName]
            ?: error("Unknown benchmark profile: $profileName")

        val constructorString = decimalOperand(profile.operandA, 6)
        val factoryString = decimalOperand(profile.operandB, 4)
        val left = BigDecimal(constructorString)
        val right = BigDecimal(factoryString)
        val negativeLeft = -left
        val equalLeft = BigDecimal(left.toString())
        val compareTarget = left + left.ulp()

        val constructorScale = 6
        val constructorBigInteger = BigInteger(profile.operandA)
        val factoryBigInteger = BigInteger(profile.operandB)
        val factoryBigIntegerScale = 4

        val zeroDividend = bigDecimalOf(0)
        val divisor = bigDecimalOf(125)
        val dividend = left.movePointRight(3)
        val equalScaleLeft = bigDecimalOf(constructorBigInteger, constructorScale)
        val equalScaleRight = bigDecimalOf(factoryBigInteger, constructorScale)
        val largeScaleGapLeft = bigDecimalOf(constructorBigInteger, constructorScale)
        val largeScaleGapRight = bigDecimalOf(factoryBigInteger, 30)
        val singleLimbOperand = bigDecimalOf(nine)
        val multiLimbOperand = bigDecimalOf(
            constructorBigInteger.abs() * tenToTwenty + seven,
            0,
        )
        val genericExactDividend = bigDecimalOf((constructorBigInteger.abs() + one) * three, 0)
        val genericExactDivisor = bigDecimalOf(three)
        val negativeScaleSmallDividend = bigDecimalOf(constructorBigInteger.abs() + seven, 0)
        val negativeScaleSmallDivisor = bigDecimalOf(one, 2)

        val genericDivisorUnscaled = factoryBigInteger.abs() + nine
        val positiveScaleGenericDivisor = bigDecimalOf(genericDivisorUnscaled, 0)
        val positiveScaleGenericDividend = bigDecimalOf(
            (constructorBigInteger.abs() + one) * genericDivisorUnscaled * hundred + seven,
            2,
        )
        val negativeScaleGenericDivisor = bigDecimalOf(genericDivisorUnscaled, 2)
        val negativeScaleGenericDividend = bigDecimalOf(
            (constructorBigInteger.abs() + one) * genericDivisorUnscaled,
            0,
        )
        val roundedDivideDividend = left
        val roundedDivideDivisor = bigDecimalOf(three)
        val powBase = BigDecimal(decimalOperand(profile.powBase, 3))
        val sqrtInput = left.abs() + bigDecimalOf(2)
        val roundingMode = RoundingMode.HALF_EVEN
        val mathContextPrecision = (maxOf(left.precision(), right.precision()) / 2).coerceAtLeast(16)
        val mathContext = MathContext(mathContextPrecision, roundingMode)
        val integralMathContext = MathContext(
            maxOf(positiveScaleGenericDividend.precision(), positiveScaleGenericDivisor.precision()) + 4,
            roundingMode,
        )
        val scaleTarget = (left.scale() + 2).coerceAtMost(18)
        val scaleMoveAmount = (profile.shiftAmount / 2).coerceAtLeast(1)

        return BigDecimalBenchmarkFixture(
            constructorString = constructorString,
            constructorBigInteger = constructorBigInteger,
            constructorScale = constructorScale,
            factoryString = factoryString,
            factoryInt = profile.intValue,
            factoryLong = profile.longValue,
            factoryBigInteger = factoryBigInteger,
            factoryBigIntegerScale = factoryBigIntegerScale,
            left = left,
            right = right,
            negativeLeft = negativeLeft,
            zeroDividend = zeroDividend,
            equalLeft = equalLeft,
            compareTarget = compareTarget,
            equalScaleLeft = equalScaleLeft,
            equalScaleRight = equalScaleRight,
            largeScaleGapLeft = largeScaleGapLeft,
            largeScaleGapRight = largeScaleGapRight,
            singleLimbOperand = singleLimbOperand,
            multiLimbOperand = multiLimbOperand,
            dividend = dividend,
            divisor = divisor,
            genericExactDividend = genericExactDividend,
            genericExactDivisor = genericExactDivisor,
            negativeScaleSmallDividend = negativeScaleSmallDividend,
            negativeScaleSmallDivisor = negativeScaleSmallDivisor,
            positiveScaleGenericDividend = positiveScaleGenericDividend,
            positiveScaleGenericDivisor = positiveScaleGenericDivisor,
            negativeScaleGenericDividend = negativeScaleGenericDividend,
            negativeScaleGenericDivisor = negativeScaleGenericDivisor,
            roundedDivideDividend = roundedDivideDividend,
            roundedDivideDivisor = roundedDivideDivisor,
            powBase = powBase,
            sqrtInput = sqrtInput,
            powExponent = profile.powExponent,
            mathContext = mathContext,
            integralMathContext = integralMathContext,
            scaleTarget = scaleTarget,
            scaleMoveAmount = scaleMoveAmount,
            roundingMode = roundingMode,
        )
    }

    private fun decimalOperand(digits: String, scale: Int): String {
        val wholeDigits = digits.length - scale
        return if (wholeDigits > 0) {
            digits.substring(0, wholeDigits) + "." + digits.substring(wholeDigits)
        } else {
            "0." + "0".repeat(-wholeDigits) + digits
        }
    }
}

@State(Scope.Benchmark)
abstract class ProfiledBenchmarkState {
    @Param("small", "medium", "large")
    var profile: String = "small"

    protected lateinit var fixture: BenchmarkFixture

    @Setup
    fun setupFixture() {
        fixture = BenchmarkFixtures.create(profile)
    }
}

@State(Scope.Benchmark)
abstract class BigDecimalProfiledBenchmarkState {
    @Param("small", "medium", "large")
    var profile: String = "small"

    protected lateinit var fixture: BigDecimalBenchmarkFixture

    @Setup
    fun setupFixture() {
        fixture = BigDecimalBenchmarkFixtures.create(profile)
    }
}
