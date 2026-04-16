package io.github.artificialpb.bignum.benchmark

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

data class IonspinBenchmarkFixture(
    val constructorString: String,
    val constructorRadixString: String,
    val constructorRadix: Int,
    val byteArrayInput: ByteArray,
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
    val powBase: BigInteger,
    val powExponent: Int,
    val bitwiseLeft: BigInteger,
    val bitwiseRight: BigInteger,
    val shiftAmount: Int,
    val bitLength: Int,
    val sqrtInput: BigInteger,
)

internal object IonspinBenchmarkFixtures {
    private val ZERO = BigInteger.ZERO
    private val ONE = BigInteger.ONE
    private val TWO = BigInteger.TWO
    private val THREE = BigInteger.parseString("3", 10)
    private val HUNDRED = BigInteger.parseString("100", 10)

    fun create(profileName: String): IonspinBenchmarkFixture {
        val profile = benchmarkProfiles[profileName]
            ?: error("Unknown benchmark profile: $profileName")
        val left = BigInteger.parseString(profile.operandA, 10)
        val right = BigInteger.parseString(profile.operandB, 10)
        val negativeLeft = left.negate()
        val equalLeft = BigInteger.parseString(left.toString(10), 10)
        val compareTarget = left.add(ONE)
        val constructorRadixString = left.toString(16)

        val divisor = right.abs().add(ONE)
        val dividend = (left.abs().add(HUNDRED)).multiply(divisor).add(THREE)
        val modulus = mersennePrime(profile.mersenneExponent)
        val inverseBase = firstCoprime(modulus)
        val powBase = BigInteger.parseString(profile.powBase, 10)

        val shiftedOdd = left.abs().or(ONE).shl((profile.shiftAmount / 2).coerceAtLeast(1))
        val bitwiseLeft = shiftedOdd.or(ONE.shl(profile.bitIndex))
        val bitwiseRight = right.abs().or(THREE)

        val sqrtInput = (left.abs().add(ONE)).multiply(right.abs().add(ONE))

        val byteArrayInput = left.toByteArray()

        return IonspinBenchmarkFixture(
            constructorString = profile.operandA,
            constructorRadixString = constructorRadixString,
            constructorRadix = 16,
            byteArrayInput = byteArrayInput,
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
            powBase = powBase,
            powExponent = profile.powExponent,
            bitwiseLeft = bitwiseLeft,
            bitwiseRight = bitwiseRight,
            shiftAmount = profile.shiftAmount,
            bitLength = profile.bitIndex,
            sqrtInput = sqrtInput,
        )
    }

    private fun mersennePrime(exponent: Int): BigInteger = TWO.pow(exponent) - ONE

    private fun firstCoprime(modulus: BigInteger): BigInteger {
        var candidate = THREE
        while (candidate.gcd(modulus) != ONE) {
            candidate = candidate.add(TWO)
        }
        return candidate
    }
}

@State(Scope.Benchmark)
abstract class IonspinProfiledBenchmarkState {
    @Param("small", "medium", "large")
    var profile: String = "small"

    protected lateinit var fixture: IonspinBenchmarkFixture

    @Setup
    fun setupFixture() {
        fixture = IonspinBenchmarkFixtures.create(profile)
    }
}
