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

### BigInteger

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
cells. The biggest wins are large `toString` (333x), large radix `toString` (290x), large `toDouble` (283x), large
byte-array construction (73x), and `sqrt` on the small profile (40x).

<details>
<summary>Full BigInteger method-by-method results vs `kotlin-multiplatform-bignum` on macOS ARM64</summary>

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
<summary>bignum-kt-only BigInteger benchmark coverage</summary>

These benchmarks do not currently have a `kotlin-multiplatform-bignum` counterpart in the suite, but they are part of
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

### BigDecimal

The same `:benchmarks` module also compares `bignum-kt` against [
`kotlin-multiplatform-bignum`](https://github.com/ionspin/kotlin-multiplatform-bignum) for `BigDecimal` on
macOS ARM64 across the same `small`, `medium`, and `large` operand profiles. The numbers below come from the
latest full benchmark reports generated on April 23, 2026 using the checked-in benchmark configuration: 2 warmups,
3 measurement iterations, and 1 second per iteration.

All timings are in `us/op`. Lower is better.

| Category      | Comparable cases | Faster cases | Slower cases | Geometric mean speedup |
|---------------|-----------------:|-------------:|-------------:|-----------------------:|
| Arithmetic    |               78 |           77 |            1 |                 192.20x |
| Comparison    |               15 |           15 |            0 |                  10.04x |
| Construction  |               24 |           24 |            0 |                  70.04x |
| Conversion    |               21 |           18 |            3 |                 257.98x |
| Scale         |               24 |           13 |           11 |                  10.24x |
| Overall       |              162 |          147 |           15 |                  84.72x |

`bignum-kt` is faster in 147 of 162 comparable macOS ARM64 BigDecimal benchmark cases. The losses cluster in the
cheapest accessors: small-profile `%`, `toFloat`, and the `scale`, `precision`, `signum`, and `unscaled value`
accessors. The biggest wins are large `toPlainString` (624630x), large `toString` (571556x), large equal-scale
`subtract` (212052x), large `factoryBigInteger` construction (140958x), and large `constructor(BigInteger)` (123643x).

<details>
<summary>Full BigDecimal method-by-method results vs `kotlin-multiplatform-bignum` on macOS ARM64</summary>

Comparable cells are `bignum-kt / kotlin-multiplatform-bignum (speedup)`.

### Arithmetic

| Method | Small | Medium | Large |
|--------|-------|--------|-------|
| abs (negative) | 0.072 / 0.238 (3.29x) | 0.093 / 0.717 (7.69x) | 0.069 / 2284.329 (33019.13x) |
| abs (positive) | 0.006 / 0.223 (35.00x) | 0.006 / 0.984 (155.28x) | 0.006 / 2589.213 (401138.83x) |
| add | 0.120 / 1.794 (14.94x) | 0.130 / 5.717 (43.95x) | 0.173 / 16371.616 (94444.54x) |
| add (equal scale) | 0.093 / 2.483 (26.81x) | 0.114 / 8.570 (75.44x) | 0.195 / 26726.459 (137221.74x) |
| add (large scale gap) | 0.190 / 4.353 (22.85x) | 0.242 / 140.691 (581.79x) | 0.603 / 27988.553 (46406.11x) |
| + operator | 0.096 / 1.648 (17.24x) | 0.115 / 5.581 (48.54x) | 0.154 / 12431.199 (80624.07x) |
| subtract | 0.099 / 1.659 (16.81x) | 0.118 / 5.626 (47.75x) | 0.174 / 12117.471 (69640.44x) |
| subtract (equal scale) | 0.053 / 2.225 (41.73x) | 0.060 / 7.812 (130.84x) | 0.084 / 17708.460 (212052.22x) |
| subtract (large scale gap) | 0.142 / 3.931 (27.62x) | 0.184 / 100.625 (546.29x) | 0.517 / 20057.177 (38831.51x) |
| - operator | 0.097 / 1.805 (18.68x) | 0.113 / 5.489 (48.59x) | 0.165 / 15029.345 (90939.66x) |
| multiply | 0.076 / 1.374 (18.09x) | 0.178 / 653.444 (3661.65x) | 0.743 / 23123.191 (31119.81x) |
| multiply (single-limb left) | 0.087 / 1.378 (15.83x) | 0.098 / 17.953 (183.65x) | 0.116 / 5893.020 (50618.25x) |
| multiply (single-limb right) | 0.082 / 1.082 (13.22x) | 0.097 / 18.252 (188.68x) | 0.127 / 5536.333 (43698.63x) |
| * operator | 0.075 / 1.189 (15.86x) | 0.178 / 533.482 (2991.44x) | 0.696 / 21697.941 (31186.09x) |
| divide | 0.471 / 2.848 (6.05x) | 0.401 / 17.396 (43.41x) | 0.597 / 4865.775 (8150.82x) |
| divide (zero dividend) | 0.021 / 0.242 (11.56x) | 0.021 / 0.241 (11.56x) | 0.021 / 0.241 (11.35x) |
| divide (generic exact) | 0.321 / 2.927 (9.11x) | 0.385 / 16.793 (43.56x) | 0.503 / 4784.775 (9507.99x) |
| / operator | 0.466 / 3.293 (7.06x) | 0.391 / 19.694 (50.41x) | 0.595 / 5319.669 (8935.44x) |
| remainder | 0.382 / 4.687 (12.26x) | 0.443 / 22.853 (51.64x) | 0.656 / 24164.242 (36833.35x) |
| % operator | 0.376 / 0.159 (2.36x slower) | 0.422 / 5.586 (13.25x) | 0.646 / 19.507 (30.22x) |
| divideAndRemainder | 0.349 / 4.692 (13.45x) | 0.406 / 23.688 (58.28x) | 0.624 / 24901.360 (39885.37x) |
| pow | 0.217 / 3.591 (16.58x) | 0.214 / 73.459 (343.30x) | 0.173 / 74.142 (428.69x) |
| pow(0) | 0.006 / 0.016 (2.61x) | 0.006 / 0.015 (2.53x) | 0.006 / 0.016 (2.58x) |
| pow(1) | 0.006 / 0.016 (2.84x) | 0.006 / 0.015 (2.71x) | 0.006 / 0.015 (2.72x) |
| negate | 0.038 / 0.203 (5.27x) | 0.039 / 0.655 (16.92x) | 0.036 / 1540.750 (42850.98x) |
| unaryMinus | 0.036 / 0.196 (5.42x) | 0.035 / 0.651 (18.46x) | 0.035 / 1484.662 (42419.05x) |

### Comparison

| Method | Small | Medium | Large |
|--------|-------|--------|-------|
| compareTo | 0.016 / 0.075 (4.66x) | 0.020 / 0.078 (3.93x) | 0.037 / 0.104 (2.85x) |
| equals | 0.017 / 0.072 (4.26x) | 0.021 / 0.079 (3.76x) | 0.038 / 0.103 (2.72x) |
| hashCode | 0.042 / 0.518 (12.21x) | 0.031 / 10.623 (341.15x) | 0.051 / 2414.825 (47016.77x) |
| min | 0.016 / 0.072 (4.61x) | 0.020 / 0.082 (3.98x) | 0.037 / 0.097 (2.67x) |
| max | 0.015 / 0.074 (4.81x) | 0.021 / 0.081 (3.82x) | 0.038 / 0.099 (2.64x) |

### Construction

| Method | Small | Medium | Large |
|--------|-------|--------|-------|
| constructor(String) | 0.180 / 4.418 (24.59x) | 0.504 / 20.067 (39.80x) | 4.807 / 2299.887 (478.42x) |
| constructor(BigInteger) | 0.032 / 0.333 (10.55x) | 0.037 / 1.762 (47.12x) | 0.041 / 5069.321 (123643.04x) |
| constructor(BigInteger, scale) | 0.041 / 0.192 (4.68x) | 0.046 / 0.672 (14.64x) | 0.037 / 2460.310 (66913.35x) |
| factory string | 0.126 / 2.940 (23.40x) | 0.458 / 14.695 (32.10x) | 2.619 / 1569.475 (599.33x) |
| factory Int | 0.072 / 0.265 (3.68x) | 0.074 / 0.255 (3.45x) | 0.053 / 0.254 (4.80x) |
| factory Long | 0.052 / 0.360 (6.87x) | 0.060 / 0.346 (5.80x) | 0.048 / 0.341 (7.05x) |
| factory BigInteger | 0.044 / 0.309 (7.11x) | 0.034 / 1.270 (36.96x) | 0.029 / 4060.217 (140958.36x) |
| factory BigInteger+scale | 0.038 / 0.182 (4.84x) | 0.037 / 0.640 (17.52x) | 0.035 / 2116.791 (60808.30x) |

### Conversion

| Method | Small | Medium | Large |
|--------|-------|--------|-------|
| toString | 0.012 / 4.439 (373.38x) | 0.011 / 585.392 (52880.44x) | 0.011 / 6417.615 (571555.76x) |
| toPlainString | 0.008 / 2.801 (347.08x) | 0.008 / 353.071 (43647.27x) | 0.009 / 5484.588 (624629.79x) |
| toBigInteger | 0.008 / 0.362 (43.19x) | 0.008 / 11.402 (1396.89x) | 0.008 / 27.170 (3412.59x) |
| toInt | 0.028 / 0.376 (13.44x) | 0.021 / 10.301 (479.56x) | 0.021 / 25.479 (1223.32x) |
| toLong | 0.021 / 0.329 (15.48x) | 0.022 / 7.821 (357.62x) | 0.019 / 22.743 (1182.46x) |
| toFloat | 0.651 / 0.029 (22.75x slower) | 0.791 / 0.027 (29.58x slower) | 2.188 / 0.028 (76.99x slower) |
| toDouble | 0.712 / 8.249 (11.58x) | 2.224 / 1192.517 (536.32x) | 3.090 / 9984.794 (3231.84x) |

### Scale

| Method | Small | Medium | Large |
|--------|-------|--------|-------|
| scale | 0.006 / 0.006 (1.04x slower) | 0.006 / 0.006 (1.04x slower) | 0.006 / 0.006 (1.01x slower) |
| precision | 0.009 / 0.007 (1.35x slower) | 0.009 / 0.006 (1.40x slower) | 0.023 / 0.021 (1.13x slower) |
| signum | 0.007 / 0.007 (1.02x slower) | 0.007 / 0.007 (1.02x slower) | 0.007 / 0.007 (1.00x slower) |
| unscaled value | 0.005 / 0.005 (1.02x slower) | 0.005 / 0.005 (1.01x) | 0.005 / 0.005 (1.03x slower) |
| setScale (exact) | 0.113 / 0.890 (7.88x) | 0.141 / 2.381 (16.84x) | 0.156 / 5918.918 (37821.85x) |
| setScale (rounded) | 0.309 / 2.288 (7.41x) | 0.415 / 13.108 (31.61x) | 0.721 / 14326.184 (19864.31x) |
| movePointLeft | 0.052 / 0.201 (3.85x) | 0.057 / 0.679 (11.99x) | 0.047 / 2228.801 (47104.63x) |
| movePointRight | 0.047 / 0.190 (4.01x) | 0.155 / 0.915 (5.91x) | 0.645 / 2428.086 (3762.91x) |

</details>

<details>
<summary>bignum-kt-only BigDecimal benchmark coverage</summary>

These benchmarks do not currently have a `kotlin-multiplatform-bignum` counterpart in the suite, but they are part
of the public `BigDecimal` surface that `bignum-kt` covers.

### Arithmetic

| Method | Small | Medium | Large |
|--------|------:|-------:|------:|
| add (MathContext) | 0.846 | 1.100 | 1.913 |
| subtract (MathContext) | 0.475 | 1.104 | 1.782 |
| multiply (MathContext) | 0.826 | 1.056 | 5.074 |
| divide (RoundingMode) | 0.200 | 0.375 | 1.511 |
| divide (scale + RoundingMode) | 0.246 | 0.441 | 1.522 |
| divide (MathContext) | 0.461 | 0.806 | 1.851 |
| remainder (MathContext) | 1.866 | 3.685 | 13.112 |
| divideAndRemainder (negative scale, small divisor) | 0.050 | 0.065 | 0.052 |
| divideAndRemainder (positive scale, generic divisor) | 0.572 | 1.327 | 3.162 |
| divideAndRemainder (negative scale, generic divisor) | 0.275 | 0.955 | 2.734 |
| divideAndRemainder (MathContext) | 1.808 | 3.475 | 12.704 |
| divideToIntegralValue (MathContext) | 1.540 | 3.291 | 12.085 |
| pow (MathContext) | 1.446 | 2.657 | 0.327 |
| sqrt (MathContext) | 7.752 | 10.241 | 40.651 |
| abs (MathContext) | 0.567 | 1.216 | 1.918 |
| negate (MathContext) | 0.388 | 1.016 | 1.602 |
| plus() | 0.005 | 0.005 | 0.005 |
| plus(MathContext) | 0.341 | 0.965 | 1.578 |
| round(MathContext) | 0.353 | 0.964 | 1.579 |
| ulp() | 0.025 | 0.027 | 0.027 |

### Conversion

| Method | Small | Medium | Large |
|--------|------:|-------:|------:|
| toEngineeringString | 0.012 | 0.011 | 0.011 |

### Scale

| Method | Small | Medium | Large |
|--------|------:|-------:|------:|
| scaleByPowerOfTen | 0.047 | 0.050 | 0.045 |
| stripTrailingZeros | 0.267 | 0.031 | 0.031 |

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

Coverage reports are generated with Kover for the JVM target, which means `commonMain` and `jvmAndroidMain` code exercised by JVM tests is reported. Apple native tests still run via the normal test tasks, but Kotlin-native coverage is not collected by the current JetBrains coverage tooling.

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

# Regenerate differential fuzz fixtures with a custom seed
./gradlew :bignum:generateDifferentialFixtures -PdifferentialFixtureSeed=0xB16B00B4

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

# BigDecimal macOS ARM64 benchmark suites
./gradlew :benchmarks:macosArm64BigDecimalArithmeticBenchmark
./gradlew :benchmarks:macosArm64BigDecimalComparisonBenchmark
./gradlew :benchmarks:macosArm64BigDecimalConstructionBenchmark
./gradlew :benchmarks:macosArm64BigDecimalConversionBenchmark
./gradlew :benchmarks:macosArm64BigDecimalScaleBenchmark

# Ionspin's kotlin-multiplatform-bignum comparison suites
./gradlew :benchmarks:macosArm64IonspinArithmeticBenchmark
./gradlew :benchmarks:macosArm64IonspinBitwiseBenchmark
./gradlew :benchmarks:macosArm64IonspinComparisonBenchmark
./gradlew :benchmarks:macosArm64IonspinConstructionBenchmark
./gradlew :benchmarks:macosArm64IonspinConversionBenchmark
./gradlew :benchmarks:macosArm64IonspinNumberTheoryBenchmark

# Ionspin BigDecimal comparison suites
./gradlew :benchmarks:macosArm64IonspinBigDecimalArithmeticBenchmark
./gradlew :benchmarks:macosArm64IonspinBigDecimalComparisonBenchmark
./gradlew :benchmarks:macosArm64IonspinBigDecimalConstructionBenchmark
./gradlew :benchmarks:macosArm64IonspinBigDecimalConversionBenchmark
./gradlew :benchmarks:macosArm64IonspinBigDecimalScaleBenchmark
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
