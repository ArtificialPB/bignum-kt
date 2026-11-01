// Swift entry point for bignum-kt. Re-exports the Kotlin/Native framework so consumers only
// need `import Bignum`; Swift-specific conveniences belong in this module.
@_exported import BignumKt

// Operators can't throw without forcing `try` on every expression, so like Swift's integer
// operators they trap on invalid input (e.g. division by zero). The named methods throw instead.
@inline(__always)
func trapOnError<T>(_ body: () throws -> T) -> T {
    do {
        return try body()
    } catch {
        fatalError(error.localizedDescription)
    }
}
