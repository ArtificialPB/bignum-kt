package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigInteger
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
    val bitwiseLeft: BigInteger,
    val bitwiseRight: BigInteger,
    val testBitInput: BigInteger,
    val setBitInput: BigInteger,
    val clearBitInput: BigInteger,
    val flipBitInput: BigInteger,
    val lowestSetBitInput: BigInteger,
    val bitIndex: Int,
    val shiftAmount: Int,
    val probablePrime: BigInteger,
    val nextPrimeStart: BigInteger,
    val sqrtInput: BigInteger,
    val rangeStart: BigInteger,
    val rangeEnd: BigInteger,
)

internal fun decimalPattern(pattern: String, repeats: Int, suffix: String): String =
    buildString {
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
        val bitwiseLeft = shiftedOdd.or(one.shiftLeft(profile.bitIndex))
        val bitwiseRight = right.abs().or(three)
        val testBitInput = bitwiseLeft.setBit(profile.bitIndex)
        val setBitInput = bitwiseLeft.clearBit(profile.bitIndex)
        val clearBitInput = bitwiseLeft.setBit(profile.bitIndex)
        val flipBitInput = bitwiseLeft
        val lowestSetBitInput = three.shiftLeft((profile.bitIndex / 2).coerceAtLeast(1))

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
            bitwiseLeft = bitwiseLeft,
            bitwiseRight = bitwiseRight,
            testBitInput = testBitInput,
            setBitInput = setBitInput,
            clearBitInput = clearBitInput,
            flipBitInput = flipBitInput,
            lowestSetBitInput = lowestSetBitInput,
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
