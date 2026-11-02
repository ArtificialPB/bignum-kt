import Foundation

extension BigInteger {
    /// Creates a value equal to `value`.
    public convenience init(_ value: Int) {
        let bytes = KotlinByteArray(size: 8)
        var remaining = Int64(value)
        for index in stride(from: 7, through: 0, by: -1) {
            bytes.set(index: Int32(index), value: Int8(truncatingIfNeeded: remaining))
            remaining >>= 8
        }
        try! self.init(bytes: bytes)
    }

    /// Creates a value from its big-endian two's-complement representation, as produced by
    /// ``twosComplement``. Throws if `data` is empty.
    public convenience init(twosComplement data: Data) throws {
        let bytes = KotlinByteArray(size: Int32(data.count))
        for (index, byte) in data.enumerated() {
            bytes.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        try self.init(bytes: bytes)
    }

    /// The minimal big-endian two's-complement representation of this value.
    public var twosComplement: Data {
        let bytes = toByteArray()
        var data = Data(count: Int(bytes.size))
        for index in 0..<Int(bytes.size) {
            data[index] = UInt8(bitPattern: bytes.get(index: Int32(index)))
        }
        return data
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
