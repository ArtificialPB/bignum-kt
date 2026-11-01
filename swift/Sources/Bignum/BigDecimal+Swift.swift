// `BigDecimal` has no `/` operator: exact division throws for non-terminating results such as
// 1 / 3, which an operator would turn into a crash. Use `divide(other:mathContext:)` or
// `divide(other:scale:roundingMode:)` instead.
//
// Like `java.math.BigDecimal`, `==` compares value and scale (2.0 != 2.00), while `<` and the
// other `Comparable` operators compare numeric value only.
extension BigDecimal {
    /// Creates a value equal to `value` with a scale of 0.
    public convenience init(_ value: Int) {
        self.init(int64: Int64(value))
    }

    /// Creates the value of `value`'s shortest decimal representation, matching
    /// `java.math.BigDecimal.valueOf(double)`. Throws if `value` is NaN or infinite.
    public static func valueOf(_ value: Double) throws -> BigDecimal {
        try BigDecimal_nonJvmKt.__bigDecimalOf(double: value)
    }

    /// Returns the integral quotient and the remainder of dividing by `divisor`.
    /// Throws if `divisor` is zero.
    public func quotientAndRemainder(dividingBy divisor: BigDecimal) throws -> (quotient: BigDecimal, remainder: BigDecimal) {
        let result = try __divideAndRemainder(other: divisor)
        return (result.get(index: 0)!, result.get(index: 1)!)
    }

    public static func + (lhs: BigDecimal, rhs: BigDecimal) -> BigDecimal {
        trapOnError { try lhs.add(other: rhs) }
    }

    public static func - (lhs: BigDecimal, rhs: BigDecimal) -> BigDecimal {
        trapOnError { try lhs.subtract(other: rhs) }
    }

    public static func * (lhs: BigDecimal, rhs: BigDecimal) -> BigDecimal {
        trapOnError { try lhs.multiply(other: rhs) }
    }

    public static prefix func - (operand: BigDecimal) -> BigDecimal { operand.negate() }

    public static func += (lhs: inout BigDecimal, rhs: BigDecimal) { lhs = lhs + rhs }

    public static func -= (lhs: inout BigDecimal, rhs: BigDecimal) { lhs = lhs - rhs }

    public static func *= (lhs: inout BigDecimal, rhs: BigDecimal) { lhs = lhs * rhs }
}

extension BigDecimal: Comparable {
    public static func < (lhs: BigDecimal, rhs: BigDecimal) -> Bool { lhs.compareTo(other: rhs) < 0 }
}

extension Double {
    /// Creates the nearest `Double` to `value`.
    public init(_ value: BigDecimal) {
        self = value.toDouble()
    }
}
