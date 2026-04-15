package io.github.artificialpb.bignum.benchmark

import io.github.artificialpb.bignum.BigIntegerRange
import io.github.artificialpb.bignum.rangeTo
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
open class RangeBenchmark : ProfiledBenchmarkState() {
    @Benchmark
    fun rangeToMethod(): BigIntegerRange = fixture.rangeStart..fixture.rangeEnd

    @Benchmark
    fun rangeIteration(blackhole: Blackhole) {
        for (value in fixture.rangeStart..fixture.rangeEnd) {
            blackhole.consume(value)
        }
    }
}
