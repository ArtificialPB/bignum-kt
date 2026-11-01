// Literal support. Integer literals are limited to the `Int` range (arbitrary-size
// `StaticBigInt` literals need iOS 16.4 / macOS 13.3); use a string literal for larger values.
// String literals trap on malformed input (the message includes the parse error), like the
// arithmetic operators.
//
// `BigDecimal` deliberately has no float literal support: Swift would pass the literal through
// `Double`, losing trailing zeros and digits beyond its precision. Use a string literal instead.

extension BigInteger: ExpressibleByIntegerLiteral {
    public convenience init(integerLiteral value: Int) {
        self.init(value)
    }
}

extension BigInteger: ExpressibleByStringLiteral {
    public convenience init(stringLiteral value: String) {
        try! self.init(string: value)
    }
}

extension BigDecimal: ExpressibleByIntegerLiteral {
    public convenience init(integerLiteral value: Int) {
        self.init(value)
    }
}

extension BigDecimal: ExpressibleByStringLiteral {
    public convenience init(stringLiteral value: String) {
        try! self.init(string: value)
    }
}
