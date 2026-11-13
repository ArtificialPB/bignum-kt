extension BigInteger {
    public static var zero: BigInteger { companion.zero }

    public static var one: BigInteger { companion.one }

    public static var two: BigInteger { companion.two }

    public static var ten: BigInteger { companion.ten }

    /// Creates a value equal to `value`.
    public convenience init(_ value: Int) {
        self.init(int64: Int64(value))
    }

    /// Returns the truncated quotient and the remainder of dividing by `divisor`.
    /// Traps if `divisor` is zero.
    public func quotientAndRemainder(dividingBy divisor: BigInteger) -> (quotient: BigInteger, remainder: BigInteger) {
        let result = __divideAndRemainder(other: divisor)
        return (result.get(index: 0)!, result.get(index: 1)!)
    }

    public static func + (lhs: BigInteger, rhs: BigInteger) -> BigInteger { lhs.add(other: rhs) }

    public static func - (lhs: BigInteger, rhs: BigInteger) -> BigInteger { lhs.subtract(other: rhs) }

    public static func * (lhs: BigInteger, rhs: BigInteger) -> BigInteger { lhs.multiply(other: rhs) }

    /// Truncating division. Traps if `rhs` is zero.
    public static func / (lhs: BigInteger, rhs: BigInteger) -> BigInteger { lhs.divide(other: rhs) }

    /// Remainder with the sign of `lhs`, like Swift's `%`. Traps if `rhs` is zero.
    public static func % (lhs: BigInteger, rhs: BigInteger) -> BigInteger { lhs.remainder(other: rhs) }

    public static prefix func - (operand: BigInteger) -> BigInteger { operand.negate() }

    public static func += (lhs: inout BigInteger, rhs: BigInteger) { lhs = lhs + rhs }

    public static func -= (lhs: inout BigInteger, rhs: BigInteger) { lhs = lhs - rhs }

    public static func *= (lhs: inout BigInteger, rhs: BigInteger) { lhs = lhs * rhs }

    public static func /= (lhs: inout BigInteger, rhs: BigInteger) { lhs = lhs / rhs }

    public static func %= (lhs: inout BigInteger, rhs: BigInteger) { lhs = lhs % rhs }
}

extension BigInteger: Comparable {
    public static func < (lhs: BigInteger, rhs: BigInteger) -> Bool { lhs.compareTo(other: rhs) < 0 }
}

extension Int {
    /// Creates an integer equal to `value`, or `nil` if it doesn't fit.
    public init?(exactly value: BigInteger) {
        guard value.bitLength() < Int.bitWidth else { return nil }
        self = Int(value.toLong())
    }

    /// Creates an integer from the low 64 bits of `value`'s two's-complement representation.
    public init(truncatingIfNeeded value: BigInteger) {
        self = Int(truncatingIfNeeded: value.toLong())
    }
}

extension Double {
    /// Creates the nearest `Double` to `value`, or ±infinity if it's out of range.
    public init(_ value: BigInteger) {
        self = value.toDouble()
    }
}
