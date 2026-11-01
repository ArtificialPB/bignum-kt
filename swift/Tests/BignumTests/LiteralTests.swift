import Bignum
import XCTest

final class LiteralTests: XCTestCase {
    func testBigIntegerIntegerLiterals() {
        let answer: BigInteger = 42
        let negative: BigInteger = -7
        let max: BigInteger = 9_223_372_036_854_775_807
        let min: BigInteger = -9_223_372_036_854_775_808

        XCTAssertEqual(answer.description, "42")
        XCTAssertEqual(negative.description, "-7")
        XCTAssertEqual(max.description, "9223372036854775807")
        XCTAssertEqual(min.description, "-9223372036854775808")
    }

    func testBigIntegerStringLiterals() {
        let large: BigInteger = "123456789012345678901234567890"
        let negative: BigInteger = "-98765432109876543210"

        XCTAssertEqual(large.description, "123456789012345678901234567890")
        XCTAssertEqual(negative.description, "-98765432109876543210")
    }

    func testBigIntegerLiteralsInExpressions() {
        let value: BigInteger = "100000000000000000000"
        XCTAssertEqual(value + 5, "100000000000000000005")
        XCTAssertEqual(value / 4, "25000000000000000000")
        XCTAssertTrue(value > 1)
        let unsorted: [BigInteger] = [3, 1, 2]
        XCTAssertEqual(unsorted.sorted(), [1, 2, 3])

        var counter: BigInteger = 10
        counter += 5
        XCTAssertEqual(counter, 15)
    }

    func testBigDecimalLiterals() {
        let whole: BigDecimal = 5
        let price: BigDecimal = "19.99"
        let scaled: BigDecimal = "2.50"

        XCTAssertEqual(whole.description, "5")
        XCTAssertEqual(price.description, "19.99")
        XCTAssertEqual(scaled.scale(), 2)
        XCTAssertEqual((price * 3).description, "59.97")
        XCTAssertEqual(price + 1, "20.99")
        XCTAssertTrue(price < 20)
    }
}
