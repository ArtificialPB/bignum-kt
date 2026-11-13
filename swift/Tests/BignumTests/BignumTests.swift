import Bignum
import Foundation
import XCTest

// Smoke tests for the packaged XCFramework. Operations that route through LibTomMath are
// included so a framework missing the statically linked LibTomMath objects fails to link.
final class BignumTests: XCTestCase {
    func testBigIntegerArithmetic() throws {
        let a = try BigInteger(string: "123456789012345678901234567890")
        let b = try BigInteger(string: "987654321098765432109876543210")

        XCTAssertEqual((a + b).description, "1111111110111111111011111111100")
        XCTAssertEqual((b - a).description, "864197532086419753208641975320")
        XCTAssertEqual(
            (a * b).description,
            "121932631137021795226185032733622923332237463801111263526900"
        )
        XCTAssertEqual((b / a).description, "8")
        XCTAssertEqual((b % a).description, "9000000000900000000090")
        XCTAssertEqual((-a).description, "-123456789012345678901234567890")
    }

    func testLibTomMathBackedOperations() throws {
        let modulus = BigInteger(497)
        XCTAssertEqual(BigInteger(4).modPow(exponent: BigInteger(13), modulus: modulus), BigInteger(445))

        let mersenne127 = BigInteger(2).pow(exponent: 127) - BigInteger(1)
        XCTAssertTrue(mersenne127.isProbablePrime(certainty: 50))
        let multiple = try BigInteger(string: "510423550381407695195061911147652317181")
        XCTAssertEqual(mersenne127.gcd(other: multiple).description, "170141183460469231731687303715884105727")
    }

    func testBigIntegerInitializers() throws {
        XCTAssertEqual(BigInteger(0).description, "0")
        XCTAssertEqual(BigInteger(-1).description, "-1")
        XCTAssertEqual(BigInteger(Int.max).description, "9223372036854775807")
        XCTAssertEqual(BigInteger(Int.min).description, "-9223372036854775808")
        XCTAssertEqual(try BigInteger(string: "ff", radix: 16), BigInteger(255))
    }

    // Only failures that depend on the data throw. Precondition violations such as division by
    // zero or a negative bit index trap, like Swift's integer operations, so they aren't tested here.
    func testBigIntegerThrowingMethods() throws {
        XCTAssertThrowsError(try BigInteger(string: "12a"))
        XCTAssertThrowsError(try BigInteger(string: ""))
        XCTAssertThrowsError(try BigInteger(string: "12", radix: 99))
        XCTAssertThrowsError(try BigInteger(2).modInverse(modulus: BigInteger(4)))
        XCTAssertEqual(try BigInteger(3).modInverse(modulus: BigInteger(7)), BigInteger(5))
    }

    func testBigIntegerNonThrowingMethods() {
        XCTAssertEqual(BigInteger(17).divide(other: BigInteger(5)), BigInteger(3))
        XCTAssertEqual(BigInteger(17).mod(modulus: BigInteger(5)), BigInteger(2))
        XCTAssertEqual(BigInteger(16).sqrt(), BigInteger(4))
        XCTAssertTrue(BigInteger(5).testBit(n: 0))
        XCTAssertFalse(BigInteger(5).testBit(n: 1))
        XCTAssertEqual(BigInteger(5).setBit(n: 1), BigInteger(7))
        XCTAssertEqual(BigInteger(5).clearBit(n: 0), BigInteger(4))
        XCTAssertEqual(BigInteger(5).flipBit(n: 3), BigInteger(13))
        XCTAssertEqual(BigInteger(5).shiftRight(n: 1), BigInteger(2))
        XCTAssertEqual(BigInteger(14).nextProbablePrime(), BigInteger(17))
    }

    func testBigIntegerQuotientAndRemainder() {
        let (quotient, remainder) = BigInteger(-17).quotientAndRemainder(dividingBy: BigInteger(5))
        XCTAssertEqual(quotient, BigInteger(-3))
        XCTAssertEqual(remainder, BigInteger(-2))
    }

    func testBigIntegerComparableAndHashable() throws {
        let large = try BigInteger(string: "100000000000000000000")
        let values = [BigInteger(3), BigInteger(-7), large, BigInteger(0)]
        XCTAssertEqual(values.sorted().map(\.description), ["-7", "0", "3", "100000000000000000000"])
        XCTAssertTrue(BigInteger(2) < BigInteger(10))
        XCTAssertEqual(Set([BigInteger(1), BigInteger(1), BigInteger(2)]).count, 2)
    }

    func testBigIntegerCompoundAssignment() {
        var value = BigInteger(10)
        value += BigInteger(5)
        value *= BigInteger(3)
        value -= BigInteger(1)
        value /= BigInteger(4)
        value %= BigInteger(7)
        XCTAssertEqual(value, BigInteger(4))
    }

    func testBigIntegerConversions() throws {
        let large = try BigInteger(string: "-98765432109876543210")
        XCTAssertEqual(try BigInteger(twosComplement: large.twosComplement), large)
        XCTAssertEqual(BigInteger(-129).twosComplement, Data([0xFF, 0x7F]))
        XCTAssertThrowsError(try BigInteger(twosComplement: Data()))

        XCTAssertEqual(Int(exactly: BigInteger(Int.min)), Int.min)
        XCTAssertNil(Int(exactly: large))
        XCTAssertEqual(Int(truncatingIfNeeded: BigInteger(Int.max) + BigInteger(1)), Int.min)
        XCTAssertEqual(Double(BigInteger(1).shiftLeft(n: 70)), 0x1p70)
    }

    func testBigDecimalArithmetic() throws {
        let a = BigDecimal(unscaledValue: BigInteger(125), scale: 2)
        let b = try BigDecimal(string: "2.50")

        XCTAssertEqual((a + b).description, "3.75")
        XCTAssertEqual((b - a).description, "1.25")
        XCTAssertEqual((a * b).description, "3.1250")
        XCTAssertEqual((-a).description, "-1.25")
        XCTAssertEqual(try b.divide(other: a).description, "2")
    }

    func testBigDecimalInitializers() throws {
        XCTAssertEqual(BigDecimal(42).description, "42")
        XCTAssertEqual(BigDecimal(int32: -7).description, "-7")
        XCTAssertEqual(BigDecimal(bigInteger: BigInteger(9)).description, "9")
        XCTAssertEqual(try BigDecimal.valueOf(0.1).description, "0.1")
        XCTAssertEqual(try BigDecimal.valueOf(1e10).description, "1.0E+10")
        XCTAssertThrowsError(try BigDecimal.valueOf(.nan))
        XCTAssertThrowsError(try BigDecimal(string: "1.2.3"))
    }

    func testBigDecimalThrowingMethods() throws {
        let one = BigDecimal(1)
        let three = BigDecimal(3)
        XCTAssertThrowsError(try one.divide(other: three))
        XCTAssertThrowsError(try one.divide(other: BigDecimal(0)))
        XCTAssertEqual(
            try one.divide(other: three, mathContext: MathContext(precision: 5)).description,
            "0.33333"
        )
        XCTAssertEqual(
            try one.divide(other: three, scale: 2, roundingMode: .halfUp).description,
            "0.33"
        )
        XCTAssertThrowsError(try BigDecimal(string: "1.25").setScale(newScale: 1))
        XCTAssertThrowsError(try BigDecimal(string: "1.25").round(mathContext: MathContext(precision: 2, roundingMode: .unnecessary)))
        XCTAssertThrowsError(try BigDecimal(string: "1.5").toBigIntegerExact())
        XCTAssertEqual(try BigDecimal(string: "1.00").toIntExact(), 1)
        XCTAssertThrowsError(try MathContext(string: "precision=x"))
    }

    func testBigDecimalNonThrowingMethods() throws {
        let value = try BigDecimal(string: "12.50")
        XCTAssertEqual(MathContext(precision: 7).precision, 7)
        XCTAssertEqual(value.remainder(other: BigDecimal(5)).description, "2.50")
        XCTAssertEqual(value.divideToIntegralValue(other: BigDecimal(5)).description, "2.00")
        XCTAssertEqual(value.pow(exponent: 2).description, "156.2500")
        XCTAssertEqual(value.movePointLeft(n: 1).description, "1.250")
        XCTAssertEqual(value.movePointRight(n: 1).description, "125.0")
        XCTAssertEqual(value.scaleByPowerOfTen(n: 2).description, "1250")
        XCTAssertEqual(value.stripTrailingZeros().description, "12.5")
    }

    func testBigDecimalQuotientAndRemainder() throws {
        let (quotient, remainder) = try BigDecimal(string: "7.5").quotientAndRemainder(dividingBy: BigDecimal(2))
        XCTAssertEqual(quotient.description, "3.0")
        XCTAssertEqual(remainder.description, "1.5")
    }

    func testBigDecimalComparisonIgnoresScaleButEqualityDoesNot() throws {
        let twoPointZero = try BigDecimal(string: "2.0")
        let twoPointZeroZero = try BigDecimal(string: "2.00")
        XCTAssertNotEqual(twoPointZero, twoPointZeroZero)
        XCTAssertFalse(twoPointZero < twoPointZeroZero)
        XCTAssertFalse(twoPointZeroZero < twoPointZero)
        XCTAssertTrue(twoPointZero < BigDecimal(3))
        XCTAssertEqual(Double(try BigDecimal(string: "0.25")), 0.25)
    }
}
