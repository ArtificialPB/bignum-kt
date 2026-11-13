// Swift entry point for bignum-kt. Re-exports the Kotlin/Native framework so consumers only
// need `import Bignum`; Swift-specific conveniences belong in this module.
@_exported import BignumKt

// The BigInteger and BigDecimal extensions only use class members, so Kotlin Multiplatform apps can
// copy them into their own Swift code. This one calls a top-level Kotlin function, whose Swift name
// depends on the framework it's exported from.
extension BigDecimal {
    /// Creates the value of `value`'s shortest decimal representation, matching
    /// `java.math.BigDecimal.valueOf(double)`. Throws if `value` is NaN or infinite.
    public static func valueOf(_ value: Double) throws -> BigDecimal {
        try BigDecimal_nonJvmKt.__bigDecimalOf(double: value)
    }
}
