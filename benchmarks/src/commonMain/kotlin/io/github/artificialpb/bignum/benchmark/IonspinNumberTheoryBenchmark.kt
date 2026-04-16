package io.github.artificialpb.bignum.benchmark

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
open class IonspinNumberTheoryBenchmark : IonspinProfiledBenchmarkState() {
    @Benchmark
    fun sqrtMethod(): BigInteger = fixture.sqrtInput.sqrt()
}
