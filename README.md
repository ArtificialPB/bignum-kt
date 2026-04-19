# bignum-kt

High-performance Kotlin Multiplatform `BigInteger` library with JVM semantics across JVM, Android, and Apple native.

On the JVM and Android, `BigInteger` is a zero-overhead typealias to `java.math.BigInteger`. On Apple native targets,
the implementation is hybrid: hot paths and small-to-medium operations run in pure Kotlin, while larger or specialized
work uses LibTomMath through C interop. All targets follow JVM semantics, including two's complement big-endian byte
arrays, so behavior stays consistent across platforms.

## Supported platforms

| Platform          | Backend                                            |
|-------------------|----------------------------------------------------|
| JVM               | `java.math.BigInteger` typealias                   |
| Android (API 21+) | `java.math.BigInteger` typealias                   |
| macOS ARM64       | Hybrid: pure Kotlin hot paths + LibTomMath interop |
| iOS ARM64         | Hybrid: pure Kotlin hot paths + LibTomMath interop |

## Usage

```kotlin
import io.github.artificialpb.bignum.*

// Create BigIntegers
val a = bigIntegerOf("123456789012345678901234567890")
val b = bigIntegerOf(42L)

// Arithmetic
val sum = a + b
val product = a * b
val quotient = a / b
val remainder = a % b
val power = b.pow(10)

// Bitwise operations
val shifted = a.shiftLeft(16)
val masked = a.and(b)

// Number theory
val gcd = a.gcd(b)
val modPow = a.modPow(b, bigIntegerOf(1000000007L))
val isPrime = b.isProbablePrime(20)

// Conversions
val bytes = a.toByteArray()
val hex = a.toString(16)
val restored = BigInteger(bytes)

// Ranges
for (i in bigIntegerOf(0L)..bigIntegerOf(10L)) {
    println(i)
}
```

## Performance

The `:benchmarks` module compares `bignum-kt` against [
`kotlin-multiplatform-bignum`](https://github.com/ionspin/kotlin-multiplatform-bignum) on macOS ARM64 across three
operand profiles: `small`, `medium`, and `large`. The numbers below come from the latest full benchmark reports
generated on April 19, 2026 using the checked-in benchmark configuration: 2 warmups, 3 measurement iterations, and 1
second per iteration.

All timings are in `us/op`. Lower is better.

| Category      | Comparable cases | Faster cases | Slower cases | Geometric mean speedup |
|---------------|-----------------:|-------------:|-------------:|-----------------------:|
| Arithmetic    |               42 |           42 |            0 |                  4.74x |
| Bitwise       |               42 |           42 |            0 |                  1.60x |
| Comparison    |               15 |           12 |            3 |                  2.76x |
| Construction  |               15 |           15 |            0 |                  9.97x |
| Conversion    |               21 |           21 |            0 |                  9.61x |
| Number theory |                3 |            3 |            0 |                 10.44x |
| Overall       |              138 |          135 |            3 |                  3.94x |

`bignum-kt` is faster in 135 of 138 comparable macOS ARM64 benchmark cases. The only losses are the three `hashCode`
cells; The biggest wins are large `toString` (333x), large radix `toString` (290x), large `toDouble` (283x), large
byte-array construction (73x), and `sqrt` on the small profile (40x).

<details>
<summary>Full method-by-method results vs `kotlin-multiplatform-bignum` on macOS ARM64</summary>

Comparable cells are `bignum-kt / kotlin-multiplatform-bignum (speedup)`.

### Arithmetic

| Method             | Small                 | Medium                  | Large                     |
|--------------------|-----------------------|-------------------------|---------------------------|
| add                | 0.055 / 0.172 (3.16x) | 0.068 / 0.171 (2.50x)   | 0.119 / 0.247 (2.08x)     |
| subtract           | 0.031 / 0.119 (3.82x) | 0.038 / 0.110 (2.90x)   | 0.058 / 0.137 (2.38x)     |
| multiply           | 0.063 / 0.315 (4.98x) | 0.135 / 1.036 (7.67x)   | 0.644 / 4.915 (7.64x)     |
| divide             | 0.320 / 2.315 (7.23x) | 0.858 / 9.027 (10.52x)  | 2.661 / 28.233 (10.61x)   |
| remainder          | 0.235 / 1.470 (6.25x) | 0.787 / 7.516 (9.55x)   | 2.539 / 23.054 (9.08x)    |
| divideAndRemainder | 0.509 / 2.817 (5.54x) | 1.050 / 13.226 (12.59x) | 2.849 / 36.551 (12.83x)   |
| abs                | 0.025 / 0.045 (1.79x) | 0.026 / 0.044 (1.68x)   | 0.025 / 0.045 (1.84x)     |
| pow                | 0.180 / 0.464 (2.58x) | 0.179 / 1.246 (6.95x)   | 0.142 / 2.074 (14.64x)    |
| mod                | 0.271 / 1.131 (4.18x) | 0.698 / 3.808 (5.46x)   | 1.093 / 11.570 (10.59x)   |
| modInverse         | 1.709 / 7.729 (4.52x) | 3.194 / 17.356 (5.43x)  | 15.627 / 72.613 (4.65x)   |
| gcd                | 0.525 / 5.201 (9.91x) | 4.370 / 65.857 (15.07x) | 19.754 / 226.108 (11.45x) |
| increment          | 0.027 / 0.116 (4.21x) | 0.038 / 0.122 (3.22x)   | 0.039 / 0.142 (3.60x)     |
| decrement          | 0.046 / 0.135 (2.94x) | 0.066 / 0.160 (2.43x)   | 0.112 / 0.257 (2.29x)     |
| unaryMinus         | 0.015 / 0.032 (2.07x) | 0.015 / 0.032 (2.07x)   | 0.015 / 0.031 (2.04x)     |

### Bitwise

| Method                | Small                 | Medium                | Large                 |
|-----------------------|-----------------------|-----------------------|-----------------------|
| and (positive)        | 0.060 / 0.120 (2.00x) | 0.076 / 0.125 (1.64x) | 0.121 / 0.171 (1.41x) |
| and (negative)        | 0.079 / 0.123 (1.56x) | 0.097 / 0.133 (1.37x) | 0.162 / 0.177 (1.10x) |
| or (positive)         | 0.045 / 0.082 (1.83x) | 0.053 / 0.078 (1.47x) | 0.076 / 0.095 (1.25x) |
| or (negative)         | 0.056 / 0.088 (1.58x) | 0.063 / 0.088 (1.40x) | 0.086 / 0.115 (1.34x) |
| xor (positive)        | 0.039 / 0.078 (1.99x) | 0.047 / 0.080 (1.70x) | 0.073 / 0.099 (1.36x) |
| xor (negative)        | 0.052 / 0.085 (1.64x) | 0.059 / 0.093 (1.57x) | 0.085 / 0.110 (1.29x) |
| not (positive)        | 0.035 / 0.073 (2.08x) | 0.039 / 0.089 (2.27x) | 0.048 / 0.126 (2.60x) |
| not (negative)        | 0.043 / 0.084 (1.94x) | 0.045 / 0.082 (1.82x) | 0.047 / 0.106 (2.28x) |
| shiftLeft (positive)  | 0.039 / 0.064 (1.63x) | 0.043 / 0.076 (1.75x) | 0.061 / 0.113 (1.86x) |
| shiftLeft (negative)  | 0.046 / 0.069 (1.50x) | 0.050 / 0.080 (1.61x) | 0.067 / 0.118 (1.74x) |
| shiftRight (positive) | 0.035 / 0.057 (1.63x) | 0.040 / 0.068 (1.72x) | 0.055 / 0.093 (1.68x) |
| shiftRight (negative) | 0.048 / 0.063 (1.32x) | 0.052 / 0.074 (1.42x) | 0.068 / 0.099 (1.45x) |
| bitLength (positive)  | 0.016 / 0.023 (1.41x) | 0.022 / 0.033 (1.54x) | 0.020 / 0.032 (1.59x) |
| bitLength (negative)  | 0.025 / 0.027 (1.10x) | 0.029 / 0.040 (1.38x) | 0.029 / 0.039 (1.34x) |

### Comparison

| Method    | Small                        | Medium                       | Large                        |
|-----------|------------------------------|------------------------------|------------------------------|
| compareTo | 0.013 / 0.069 (5.51x)        | 0.016 / 0.073 (4.45x)        | 0.029 / 0.091 (3.18x)        |
| equals    | 0.013 / 0.068 (5.25x)        | 0.018 / 0.074 (4.17x)        | 0.035 / 0.091 (2.64x)        |
| hashCode  | 0.017 / 0.012 (1.42x slower) | 0.024 / 0.013 (1.90x slower) | 0.058 / 0.018 (3.26x slower) |
| min       | 0.012 / 0.069 (5.93x)        | 0.016 / 0.074 (4.75x)        | 0.030 / 0.092 (3.10x)        |
| max       | 0.012 / 0.069 (5.94x)        | 0.016 / 0.074 (4.77x)        | 0.028 / 0.092 (3.27x)        |

### Construction

| Method               | Small                  | Medium                  | Large                   |
|----------------------|------------------------|-------------------------|-------------------------|
| parse decimal string | 0.188 / 2.565 (13.67x) | 0.654 / 12.266 (18.76x) | 2.453 / 49.581 (20.21x) |
| parse radix string   | 0.158 / 2.097 (13.30x) | 0.554 / 11.641 (21.00x) | 2.204 / 48.364 (21.94x) |
| from byte array      | 0.059 / 1.929 (32.71x) | 0.084 / 4.576 (54.72x)  | 0.194 / 14.221 (73.42x) |
| from Int             | 0.043 / 0.099 (2.33x)  | 0.037 / 0.108 (2.92x)   | 0.036 / 0.089 (2.48x)   |
| from Long            | 0.033 / 0.085 (2.58x)  | 0.034 / 0.082 (2.38x)   | 0.032 / 0.072 (2.22x)   |

### Conversion

| Method          | Small                  | Medium                    | Large                       |
|-----------------|------------------------|---------------------------|-----------------------------|
| toByteArray     | 0.031 / 0.281 (9.18x)  | 0.050 / 1.156 (23.00x)    | 0.145 / 3.601 (24.82x)      |
| toInt           | 0.011 / 0.013 (1.16x)  | 0.011 / 0.013 (1.16x)     | 0.011 / 0.013 (1.15x)       |
| toLong          | 0.012 / 0.014 (1.14x)  | 0.012 / 0.013 (1.12x)     | 0.012 / 0.013 (1.12x)       |
| toDouble        | 0.734 / 3.437 (4.68x)  | 3.540 / 269.765 (76.21x)  | 10.466 / 2962.341 (283.05x) |
| toString        | 0.227 / 2.936 (12.95x) | 1.864 / 266.351 (142.88x) | 8.802 / 2931.252 (333.00x)  |
| toString(radix) | 0.186 / 2.640 (14.16x) | 1.869 / 219.516 (117.45x) | 8.308 / 2410.336 (290.12x)  |
| signum          | 0.006 / 0.007 (1.08x)  | 0.006 / 0.007 (1.07x)     | 0.006 / 0.007 (1.08x)       |

### Number theory

| Method | Small                    | Medium                   | Large                    |
|--------|--------------------------|--------------------------|--------------------------|
| sqrt   | 3.475 / 138.404 (39.83x) | 19.315 / 148.939 (7.71x) | 55.821 / 207.101 (3.71x) |

</details>

<details>
<summary>bignum-kt-only benchmark coverage</summary>

These benchmarks do not currently have an `kotlin-multiplatform-bignum` counterpart in the suite, but they are part of
the public surface and native implementation work that `bignum-kt` covers.

### Arithmetic

| Method         | Small | Medium |  Large |
|----------------|------:|-------:|-------:|
| plus operator  | 0.029 |  0.034 |  0.051 |
| minus operator | 0.033 |  0.040 |  0.064 |
| times operator | 0.060 |  0.131 |  0.626 |
| div operator   | 0.522 |  0.876 |  2.708 |
| lcm            | 0.805 |  5.011 | 21.309 |
| modPow         | 2.964 |  3.601 |  5.409 |

### Bitwise

| Method                     | Small | Medium | Large |
|----------------------------|------:|-------:|------:|
| andNot (positive)          | 0.069 |  0.086 | 0.173 |
| andNot (negative)          | 0.076 |  0.109 | 0.151 |
| bitCount (positive)        | 0.015 |  0.028 | 0.036 |
| bitCount (negative)        | 0.024 |  0.034 | 0.043 |
| clearBit (positive)        | 0.047 |  0.051 | 0.058 |
| clearBit (negative)        | 0.050 |  0.054 | 0.062 |
| flipBit (positive)         | 0.040 |  0.043 | 0.053 |
| flipBit (negative)         | 0.051 |  0.056 | 0.065 |
| getLowestSetBit (positive) | 0.015 |  0.015 | 0.016 |
| getLowestSetBit (negative) | 0.020 |  0.020 | 0.020 |
| setBit (positive)          | 0.046 |  0.048 | 0.050 |
| setBit (negative)          | 0.052 |  0.055 | 0.060 |
| testBit (positive)         | 0.017 |  0.017 | 0.017 |
| testBit (negative)         | 0.024 |  0.024 | 0.024 |

### Construction

| Method                 | Small | Medium | Large |
|------------------------|------:|-------:|------:|
| factory string         | 0.172 |  0.631 | 2.263 |
| constructor byte slice | 0.065 |  0.098 | 0.195 |

### Number theory

| Method            |   Small |   Medium |    Large |
|-------------------|--------:|---------:|---------:|
| isProbablePrime   |  67.507 |  469.823 | 2320.873 |
| nextProbablePrime | 154.078 | 2098.829 | 3468.492 |

### Range

| Method          | Small | Medium | Large |
|-----------------|------:|-------:|------:|
| rangeTo         | 0.021 |  0.020 | 0.024 |
| range iteration | 1.833 |  3.684 | 4.801 |

</details>

## API overview

**Constructors** - `BigInteger(String)`, `BigInteger(String, radix)`, `BigInteger(ByteArray)`,
`BigInteger(ByteArray, off, len)`

**Arithmetic** - `add`, `subtract`, `multiply`, `divide`, `mod`, `divideAndRemainder`, `abs`, `pow`, `gcd`, `lcm`,
`sqrt`, `modPow`, `modInverse`

**Operators** - `+`, `-`, `*`, `/`, `%`, unary `-`, `++`, `--`, `..`

**Bitwise** - `and`, `or`, `xor`, `not`, `andNot`, `shiftLeft`, `shiftRight`, `testBit`, `setBit`, `clearBit`,
`flipBit`, `bitLength`, `bitCount`, `getLowestSetBit`

**Number theory** - `isProbablePrime`, `nextProbablePrime`

**Conversions** - `toByteArray`, `toInt`, `toLong`, `toDouble`, `toString(radix)`, `signum`

**Comparison** - `compareTo`, `min`, `max`, `equals`, `hashCode`

**Factory** - `bigIntegerOf(String)`, `bigIntegerOf(Long)`, `bigIntegerOf(Int)`

## Testing

The library is tested at several levels:

- Data-driven tests validate operations against known inputs and expected outputs.
- Property-based tests verify algebraic laws and invariants with generated values.
- Differential fuzz tests generate a JVM fixture corpus and replay it on native targets to catch semantic drift.
- Edge-case tests cover sign handling, negative bitwise behavior, and boundary conditions.

Tests live in `commonTest`, so the same cases run across JVM, Android, and Apple native targets.

Coverage reports are generated with Kover for the JVM target, which means `commonMain` and `jvmMain` code exercised by JVM tests is reported. Apple native tests still run via the normal test tasks, but Kotlin-native coverage is not collected by the current JetBrains coverage tooling.

## Build, test, and benchmark

Requires JDK 17+. Xcode is required for Apple targets. The Android SDK is required if you build the Android artifact.

```bash
# Build everything
./gradlew build

# Run all tests
./gradlew allTests

# Run ktlint
./gradlew ktlintCheck
./gradlew ktlintFormat

# Library tests only
./gradlew :bignum:jvmTest
./gradlew :bignum:macosArm64Test

# Generate JVM/common coverage reports
./gradlew coverage
./gradlew coverageHtml
./gradlew coverageXml

# Regenerate differential fuzz fixtures
./gradlew :bignum:generateDifferentialFixtures

# Compile benchmark sources for every benchmark target
./gradlew :benchmarks:compileAllBenchmarks

# Full macOS ARM64 benchmark sweep
./gradlew :benchmarks:macosArm64Benchmark

# Targeted macOS ARM64 benchmark suites
./gradlew :benchmarks:macosArm64ArithmeticBenchmark
./gradlew :benchmarks:macosArm64BitwiseBenchmark
./gradlew :benchmarks:macosArm64ComparisonBenchmark
./gradlew :benchmarks:macosArm64ConstructionBenchmark
./gradlew :benchmarks:macosArm64ConversionBenchmark
./gradlew :benchmarks:macosArm64NumberTheoryBenchmark
./gradlew :benchmarks:macosArm64RangeBenchmark

# Ionspin's kotlin-multiplatform-bignum comparison suites
./gradlew :benchmarks:macosArm64IonspinArithmeticBenchmark
./gradlew :benchmarks:macosArm64IonspinBitwiseBenchmark
./gradlew :benchmarks:macosArm64IonspinComparisonBenchmark
./gradlew :benchmarks:macosArm64IonspinConstructionBenchmark
./gradlew :benchmarks:macosArm64IonspinConversionBenchmark
./gradlew :benchmarks:macosArm64IonspinNumberTheoryBenchmark
```

## Contributing

### Getting started

1. Clone the repository with submodules:
   ```bash
   git clone --recurse-submodules https://github.com/ArtificialPB/bignum-kt.git
   ```
2. Open the project in IntelliJ IDEA (or another IDE with Kotlin Multiplatform support).
3. Run `./gradlew build`.
4. Run `./gradlew allTests`.

### Guidelines

- Follow JVM semantics. If native and JVM behavior differ, JVM behavior is the reference.
- Prefer `commonTest` unless you are testing something that is genuinely platform-specific.
- Re-run the relevant benchmark suites when you touch hot paths.
- Regenerate differential fixtures when new cross-platform behavior needs fixture coverage.
- Keep the common API surface small and behaviorally consistent across platforms.
- Preserve the hybrid native design: optimize hot paths in Kotlin when that buys real wins, and use LibTomMath where it
  is still the best backend.

## Project structure

```text
bignum-kt/
├── bignum/
│   └── src/
│       ├── commonMain/        # expect declarations + common API
│       ├── commonTest/        # shared tests and differential fixtures
│       ├── jvmMain/           # actual typealias to java.math.BigInteger
│       ├── nativeMain/        # hybrid Kotlin + LibTomMath native implementation
│       └── nativeInterop/     # LibTomMath sources and cinterop glue
├── benchmarks/
│   └── src/
│       └── commonMain/        # shared benchmark fixtures and suites
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```
