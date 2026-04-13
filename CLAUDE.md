# bignum-kt

Kotlin Multiplatform library providing a common BigInteger abstraction that delegates to the most performant native implementation on each platform.

## Architecture

- **commonMain**: Defines the common `BigInteger` API using `expect` declarations
- **jvmMain**: `actual typealias BigInteger = java.math.BigInteger` — zero-overhead delegation to the JVM standard library
- **nativeMain**: LibTomMath-backed `BigInteger` implementation via `cinterop` for Kotlin/Native (macOS, iOS)

## Project Structure

```
bignum-kt/
├── bignum/                    # Library module
│   ├── src/
│   │   ├── commonMain/        # expect declarations + common API
│   │   ├── commonTest/        # shared tests
│   │   ├── jvmMain/           # typealias to java.math.BigInteger
│   │   ├── jvmTest/
│   │   ├── nativeMain/        # LibTomMath cinterop implementation
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
```

## API Pattern

JVM uses `actual typealias BigInteger = java.math.BigInteger` for zero-overhead interop. This means:
- Only methods that exist on `java.math.BigInteger` can be declared inside the `expect class` body
- Operators (`+`, `-`, `*`, `/`, `%`, unary `-`) are top-level `expect`/`actual` extension functions
- Factory methods and constants live on `BigIntegers` object (not a companion — typealias'd classes can't have one)

```kotlin
val a = BigIntegers.of("123456789")
val b = BigIntegers.of(42L)
val sum = a + b
val zero = BigIntegers.ZERO
```

## Conventions

- Package: `io.github.artificialpb.bignum`
- Kotlin 2.3.x, Gradle with version catalogs
- Use `expect`/`actual` for platform-specific implementations
- Tests go in `commonTest` whenever possible; platform-specific tests only when needed
- No Compose, no Android — this is a pure library
