# bignum-kt

Kotlin Multiplatform library providing a common BigInteger abstraction that delegates to the most performant native implementation on each platform.

## Architecture

- **commonMain**: Defines the common `BigInteger` and `BigDecimal` APIs using `expect` declarations
- **jvmAndroidMain**: Zero-overhead typealiases to `java.math.BigInteger` and `java.math.BigDecimal`
- **nonJvmMain**: Shared pure Kotlin `BigDecimal` implementation and JVM-compatible `Double` conversion
- **jsMain**: ECMAScript `BigInt` implementation and JavaScript-specific decimal backend hooks
- **nativeMain**: Hybrid Kotlin and LibTomMath-backed `BigInteger` plus optimized native decimal backend hooks

## Project Structure

```
bignum-kt/
├── bignum/                    # Library module
├── benchmarks/                # Multiplatform benchmark module
│   ├── src/
│   │   ├── commonMain/        # expect declarations + common API
│   │   ├── commonTest/        # shared tests
│   │   ├── jvmAndroidMain/    # typealiases to java.math big-number types
│   │   ├── jvmTest/
│   │   ├── nonJvmMain/        # shared BigDecimal implementation
│   │   ├── jsMain/            # ECMAScript BigInt + decimal backend
│   │   ├── jsTest/
│   │   ├── nativeMain/        # LibTomMath cinterop + decimal backend
│   │   └── nativeTest/
│   └── build.gradle.kts
├── build.gradle.kts           # Root build file
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

## Build & Test

```bash
# Build all targets
./gradlew build

# Run all tests
./gradlew allTests

# JVM only
./gradlew jvmTest

# Native only (LibTomMath built from submodule automatically)
./gradlew macosArm64Test

# JavaScript only (Node.js)
./gradlew jsNodeTest

# Compile benchmark sources for every benchmark target
./gradlew :benchmarks:compileAllBenchmarks

# Run benchmark smoke profiles
./gradlew :benchmarks:jvmSmokeBenchmark
./gradlew :benchmarks:jsSmokeBenchmark
./gradlew :benchmarks:macosArm64SmokeBenchmark

# Run full benchmark profiles
./gradlew :benchmarks:jvmBenchmark
./gradlew :benchmarks:jsBenchmark
./gradlew :benchmarks:macosArm64Benchmark

# Run only one benchmark suite/file during optimization work
./gradlew :benchmarks:jvmArithmeticSmokeBenchmark
./gradlew :benchmarks:jvmArithmeticBenchmark
./gradlew :benchmarks:macosArm64ArithmeticSmokeBenchmark
./gradlew :benchmarks:macosArm64ArithmeticBenchmark

```

## Benchmarking

- Benchmarks live in the `:benchmarks` module and are implemented in `benchmarks/src/commonMain`
- The benchmark module depends on `:bignum`, so shared benchmark code is compiled against every library target
- Runnable benchmark targets are `jvm`, `js`, and `macosArm64`
- iOS benchmark targets are compile-checked via `:benchmarks:compileAllBenchmarks`, but not executed as part of the benchmark plugin task set on this host
- Each benchmark file has dedicated Gradle configurations/tasks: `arithmetic`, `bitwise`, `comparison`, `construction`, `conversion`, `numberTheory`, and `range`, each with both full and `Smoke` variants

## API Pattern

JVM uses `actual typealias BigInteger = java.math.BigInteger` for zero-overhead interop. This means:
- Only methods that exist on `java.math.BigInteger` can be declared inside the `expect class` body
- Operators (`+`, `-`, `*`, `/`, `%`, unary `-`) are top-level `expect`/`actual` extension functions
- Factory functions are top-level `bigIntegerOf(String)`, `bigIntegerOf(Long)`, `bigIntegerOf(Int)`

```kotlin
val a = bigIntegerOf("123456789")
val b = bigIntegerOf(42L)
val sum = a + b
```

## Auto-Research Mode

To start an autonomous performance optimization session, invoke the `perf-optimizer` skill and follow the Auto-Research Mode section. The agent will branch, baseline, optimize, test, benchmark, and log results in a continuous loop without human intervention.

## Conventions

- Package: `io.github.artificialpb.bignum`
- Kotlin 2.3.x, Gradle with version catalogs
- Use `expect`/`actual` for platform-specific implementations
- Tests go in `commonTest` whenever possible; platform-specific tests only when needed
- No Compose, no Android — this is a pure library
