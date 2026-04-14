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

No other dependencies are needed. LibTomMath (the native math library) is included as a git submodule and compiled from source during the build.

## Getting Started

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/ArtificialPB/bignum-kt.git
cd bignum-kt

# If you already cloned without submodules
git submodule update --init --recursive

# Build and test
./gradlew jvmTest macosArm64Test
```

## Project Structure

```
bignum-kt/
├── bignum/                              # Library module
│   ├── src/
│   │   ├── commonMain/                  # expect class BigInteger + operators
│   │   ├── commonTest/                  # Shared test suite
│   │   ├── jvmMain/                     # actual typealias to java.math.BigInteger
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
./gradlew jvmTest                 # JVM tests only
./gradlew macosArm64Test          # Native tests (macOS ARM64)
./gradlew allTests                # All platform tests
./gradlew build                   # Full build (all targets)
```

## Architecture

### JVM — `actual typealias BigInteger = java.math.BigInteger`

Zero-overhead: our `BigInteger` IS `java.math.BigInteger` on JVM. This means:
- Only methods that exist on `java.math.BigInteger` can go inside the `expect class` body
- Operators and extra functions are top-level `expect`/`actual` extension functions
- Factory functions are top-level: `bigIntegerOf(String)`, `bigIntegerOf(Long)`, `bigIntegerOf(Int)`

### Native — LibTomMath via cinterop

The `actual class BigInteger` wraps LibTomMath's `mp_int`. LibTomMath is compiled from source (git submodule) during the Gradle build for each target architecture.

Supported native targets: macOS ARM64, iOS ARM64, iOS x64, iOS Simulator ARM64.

### Adding a new method

1. Check that `java.math.BigInteger` has the method (otherwise it must be an extension function)
2. Add the declaration to `commonMain/.../BigInteger.kt`
3. JVM: the typealias picks it up automatically (for class body members) or implement the `actual` extension
4. Native: implement using LibTomMath functions in `nativeMain/.../BigInteger.native.kt`
5. Add tests in `commonTest/.../BigIntegerTest.kt`
6. Run `./gradlew jvmTest macosArm64Test` to verify both platforms

## Conventions

- Package: `io.github.artificialpb.bignum`
- Tests go in `commonTest` whenever possible
- Match Java's `BigInteger` behavior exactly (two's complement semantics, same method signatures)
- Native `toString(radix)` must output lowercase to match JVM
