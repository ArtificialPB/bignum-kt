# Contributing to bignum-kt

## Prerequisites

- **macOS** (required for iOS/macOS native targets)
- **Xcode Command Line Tools** — provides `clang`, `make`, `ar`, `xcrun`
  ```bash
  xcode-select --install
  ```
- **JDK 17+** — for building JVM targets. Install via [SDKMAN](https://sdkman.io/) or [Homebrew](https://brew.sh/):
  ```bash
  sdk install java 17-tem    # SDKMAN
  brew install openjdk@17    # Homebrew
  ```

Node.js is used for JavaScript tests and is provisioned by the Kotlin Gradle plugin. LibTomMath (the native math library) is included as a git submodule and compiled from source during the build.

## Getting Started

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/ArtificialPB/bignum-kt.git
cd bignum-kt

# If you already cloned without submodules
git submodule update --init --recursive

# Build and test
./gradlew jvmTest jsNodeTest macosArm64Test
```

## Project Structure

```
bignum-kt/
├── bignum/                              # Library module
│   ├── src/
│   │   ├── commonMain/                  # expect class BigInteger + operators
│   │   ├── commonTest/                  # Shared test suite
│   │   ├── jvmAndroidMain/              # shared actuals for JVM + Android
│   │   ├── jsMain/                      # ECMAScript BigInt-backed actuals
│   │   ├── nativeMain/                  # actual class backed by LibTomMath
│   │   └── nativeInterop/
│   │       ├── cinterop/tommath.def     # cinterop definition
│   │       └── libtommath/              # LibTomMath git submodule
│   └── build.gradle.kts
├── build.gradle.kts                     # Root build file
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

## Build Commands

```bash
./gradlew ktlintCheck              # Kotlin + Gradle Kotlin DSL lint
./gradlew ktlintFormat             # Apply ktlint formatting
./gradlew jvmTest                 # JVM tests only
./gradlew jsNodeTest              # JavaScript tests under Node.js
./gradlew macosArm64Test          # Native tests (macOS ARM64)
./gradlew allTests                # All platform tests
./gradlew build                   # Full build (all targets)
./gradlew coverage                # JVM/common HTML + XML coverage reports
./gradlew coverageHtml            # JVM/common HTML coverage report
./gradlew coverageXml             # JVM/common XML coverage report
./gradlew publishToMavenLocal     # Publish artifacts to your local Maven cache
./gradlew publish                 # Stage artifacts for Maven Central deployment
```

Coverage is reported through Kover for the JVM target. That includes `commonMain` and `jvmMain` code exercised by JVM tests, but it does not include Kotlin/Native execution coverage.

## Publishing

GitHub Actions publishes from [publish-library.yml](.github/workflows/publish-library.yml) using JReleaser and the Central Publisher API. Configure these repository secrets before enabling releases:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `JRELEASER_GPG_PUBLIC_KEY`
- `JRELEASER_GPG_SECRET_KEY`
- `JRELEASER_GPG_PASSPHRASE`

The build version comes from `VERSION_NAME` in [gradle.properties](gradle.properties). Use a `-SNAPSHOT` version for snapshot publishing from `master`, and update it to the release version before pushing a matching `v*` tag.

## Architecture

### JVM and Android — `actual typealias BigInteger = java.math.BigInteger`

Zero-overhead: our `BigInteger` IS `java.math.BigInteger` on JVM. This means:
- Only methods that exist on `java.math.BigInteger` can go inside the `expect class` body
- Operators and extra functions are top-level `expect`/`actual` extension functions
- Factory functions are top-level: `bigIntegerOf(String)`, `bigIntegerOf(Long)`, `bigIntegerOf(Int)`

### Native — Kotlin hot paths and LibTomMath via cinterop

The native `actual class BigInteger` stores a Kotlin-owned magnitude, uses Kotlin hot paths for common operations, and delegates selected large or specialized operations to LibTomMath. LibTomMath is compiled from source during the build for each target architecture.

Supported native targets: macOS ARM64, iOS ARM64, iOS x64, iOS Simulator ARM64.

### JavaScript — ECMAScript `BigInt`

The JS `actual class BigInteger` wraps the host's native arbitrary-precision `BigInt`. `BigDecimal` is implemented in Kotlin as an unscaled `BigInteger` plus an `Int` scale. Run the shared suite with `./gradlew jsNodeTest`.

### Adding a new method

1. Check that `java.math.BigInteger` has the method (otherwise it must be an extension function)
2. Add the declaration to `commonMain/.../BigInteger.kt`
3. JVM/Android: the shared `jvmAndroidMain` typealias implementation picks it up automatically (for class body members) or implement the `actual` extension there
4. JS: implement using ECMAScript `BigInt` in `jsMain/.../BigInteger.js.kt`
5. Native: implement using Kotlin hot paths or LibTomMath in `nativeMain/.../BigInteger.native.kt`
6. Add tests in `commonTest/.../BigIntegerTest.kt`
7. Run `./gradlew jvmTest jsNodeTest macosArm64Test` to verify all backends

## Conventions

- Package: `io.github.artificialpb.bignum`
- Tests go in `commonTest` whenever possible
- Match Java's `BigInteger` behavior exactly (two's complement semantics, same method signatures)
- Native `toString(radix)` must output lowercase to match JVM
