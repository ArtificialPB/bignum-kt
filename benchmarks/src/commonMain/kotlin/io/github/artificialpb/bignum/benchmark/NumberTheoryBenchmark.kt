package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigInteger
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
open class NumberTheoryBenchmark : ProfiledBenchmarkState() {
    @Benchmark
    fun isProbablePrimeMethod(): Boolean = fixture.probablePrime.isProbablePrime(16)

    @Benchmark
    fun nextProbablePrimeMethod(): BigInteger = fixture.nextPrimeStart.nextProbablePrime()

    @Benchmark
    fun sqrtMethod(): BigInteger = fixture.sqrtInput.sqrt()
}
