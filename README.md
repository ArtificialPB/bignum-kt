# bignum-kt

Kotlin Multiplatform `BigInteger` library that delegates to the best native implementation on each platform.

On the JVM, `BigInteger` is a zero-overhead typealias to `java.math.BigInteger`. On Kotlin/Native (Apple platforms), it
wraps [LibTomMath](https://www.libtom.dev/LibTomMath/) via C interop. All implementations follow JVM semantics —
two's complement big-endian byte order — so code behaves identically regardless of where it runs.

## Supported platforms

| Platform            | Backend                            |
|---------------------|------------------------------------|
| JVM                 | `java.math.BigInteger` (typealias) |
| Android (API 21+)   | `java.math.BigInteger` (typealias) |
| macOS ARM64         | LibTomMath                         |
| iOS ARM64           | LibTomMath                         |
| iOS x64 (simulator) | LibTomMath                         |
| iOS Simulator ARM64 | LibTomMath                         |

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

## API overview

**Arithmetic** — `add`, `subtract`, `multiply`, `divide`, `mod`, `divideAndRemainder`, `abs`, `pow`, `gcd`, `lcm`,
`sqrt`, `modPow`, `modInverse`

**Operators** — `+`, `-`, `*`, `/`, `%`, unary `-`, `++`, `--`, `..`

**Bitwise** — `and`, `or`, `xor`, `not`, `andNot`, `shiftLeft`, `shiftRight`, `testBit`, `setBit`, `clearBit`,
`flipBit`, `bitLength`, `bitCount`, `getLowestSetBit`

**Number theory** — `isProbablePrime`, `nextProbablePrime`

**Conversions** — `toByteArray`, `toInt`, `toLong`, `toDouble`, `toString(radix)`

**Factory** — `bigIntegerOf(String)`, `bigIntegerOf(Long)`, `bigIntegerOf(Int)`

## Testing

The library is thoroughly tested at multiple levels:

- **Data-driven tests** validate every operation against known inputs and expected outputs.
- **Property-based tests** verify mathematical laws (commutativity, associativity, identity) using randomly generated
  values.
- **Differential fuzz tests** generate a fixture corpus on the JVM and replay it on every native target, ensuring
  cross-platform consistency.
- **Edge-case tests** cover boundary conditions, negative-number bitwise semantics, and sign handling.

All tests are shared across platforms via `commonTest`, so every target runs the same suite.

## Build & test

Requires a JDK 17+ and Xcode (for native targets).

```bash
# Build all targets
./gradlew build

# Run all tests
./gradlew allTests

# JVM only
./gradlew jvmTest

# Native only (LibTomMath is built from source automatically)
./gradlew macosArm64Test

# Regenerate differential fuzz fixtures
./gradlew generateDifferentialFixtures
```

## Contributing

### Getting started

1. Clone the repository with submodules:
   ```bash
   git clone --recurse-submodules https://github.com/ArtificialPB/bignum-kt.git
   ```
2. Open the project in IntelliJ IDEA (or any IDE with Kotlin Multiplatform support).
3. Run `./gradlew build` to verify everything compiles.
4. Run `./gradlew allTests` to confirm all tests pass.

### Guidelines

- **Follow JVM semantics.** The native implementation must behave identically to `java.math.BigInteger`. When in doubt,
  the JVM behavior is correct.
- **Write tests in `commonTest`.** Tests should be platform-agnostic. Only add platform-specific tests when testing
  platform-specific behavior.
- **Run the full test suite** before submitting a PR — `./gradlew allTests` covers all platforms.
- **Regenerate differential fixtures** if you add new operations: `./gradlew generateDifferentialFixtures`, then commit
  the updated fixtures.
- **Keep the common API surface minimal.** Only expose operations that can work identically across all platforms.
- Use `expect`/`actual` for platform-specific implementations. Operators and factory methods are top-level extension
  functions (required by the JVM typealias approach).

### Project structure

```
bignum-kt/
├── bignum/
│   └── src/
│       ├── commonMain/        # expect declarations + common API
│       ├── commonTest/        # shared test suite
│       ├── jvmMain/           # actual typealias to java.math.BigInteger
│       ├── nativeMain/        # LibTomMath cinterop implementation
│       └── nativeInterop/     # LibTomMath sources (git submodule)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```
