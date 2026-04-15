package io.github.artificialpb.bignum

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class BigIntegerBenchmarkCoverageTest : FunSpec({
    test("benchmark suite covers every public BigInteger entrypoint") {
        val benchmarkDir = Path.of("src/commonMain/kotlin/io/github/artificialpb/bignum/benchmark")
        val benchmarkRegex = Regex("@Benchmark\\s+fun\\s+(\\w+)")

        val benchmarkMethods = Files.walk(benchmarkDir).use { paths ->
            val files = paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .toList()

            files
                .asSequence()
                .flatMap { path ->
                    benchmarkRegex.findAll(Files.readString(path)).map { match -> match.groupValues[1] }
                }
                .toSet()
        }

        benchmarkMethods shouldBe setOf(
            "constructorString",
            "constructorStringRadix",
            "constructorBytes",
            "constructorBytesSlice",
            "factoryString",
            "factoryInt",
            "factoryLong",
            "addMethod",
            "plusOperator",
            "subtractMethod",
            "minusOperator",
            "multiplyMethod",
            "timesOperator",
            "divideMethod",
            "divOperator",
            "remainderOperator",
            "absMethod",
            "powMethod",
            "modMethod",
            "modPowMethod",
            "modInverseMethod",
            "gcdMethod",
            "divideAndRemainderMethod",
            "unaryMinusOperator",
            "incrementOperator",
            "decrementOperator",
            "lcmMethod",
            "andMethod",
            "orMethod",
            "xorMethod",
            "notMethod",
            "andNotMethod",
            "shiftLeftMethod",
            "shiftRightMethod",
            "testBitMethod",
            "setBitMethod",
            "clearBitMethod",
            "flipBitMethod",
            "getLowestSetBitMethod",
            "bitLengthMethod",
            "bitCountMethod",
            "isProbablePrimeMethod",
            "nextProbablePrimeMethod",
            "sqrtMethod",
            "toByteArrayMethod",
            "toIntMethod",
            "toLongMethod",
            "toDoubleMethod",
            "toStringMethod",
            "toStringRadixMethod",
            "signumMethod",
            "compareToMethod",
            "equalsMethod",
            "hashCodeMethod",
            "minMethod",
            "maxMethod",
            "rangeToMethod",
            "rangeIteration",
        )
    }
})
