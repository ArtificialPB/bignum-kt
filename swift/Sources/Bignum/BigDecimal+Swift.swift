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

    /// Returns the integral quotient and the remainder of dividing by `divisor`.
    /// Traps if `divisor` is zero.
    public func quotientAndRemainder(dividingBy divisor: BigDecimal) -> (quotient: BigDecimal, remainder: BigDecimal) {
        let result = __divideAndRemainder(other: divisor)
        return (result.get(index: 0)!, result.get(index: 1)!)
    }

    /// Returns the integral quotient and the remainder of dividing by `divisor`, rounding the quotient
    /// according to `mathContext`. Throws if `divisor` is zero, if the integral quotient needs more
    /// digits than `mathContext.precision`, or if it needs rounding under `RoundingMode.unnecessary`.
    public func quotientAndRemainder(
        dividingBy divisor: BigDecimal,
        mathContext: MathContext
    ) throws -> (quotient: BigDecimal, remainder: BigDecimal) {
        let result = try __divideAndRemainder(other: divisor, mathContext: mathContext)
        return (result.get(index: 0)!, result.get(index: 1)!)
    }

    public static func + (lhs: BigDecimal, rhs: BigDecimal) -> BigDecimal { lhs.add(other: rhs) }

    public static func - (lhs: BigDecimal, rhs: BigDecimal) -> BigDecimal { lhs.subtract(other: rhs) }

    public static func * (lhs: BigDecimal, rhs: BigDecimal) -> BigDecimal { lhs.multiply(other: rhs) }

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
