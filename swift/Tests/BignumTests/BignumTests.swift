import Bignum
import XCTest

// Smoke tests for the packaged XCFramework. Operations that route through LibTomMath are
// included so a framework missing the statically linked LibTomMath objects fails to link.
final class BignumTests: XCTestCase {
    func testBigIntegerArithmetic() {
        let a = BigInteger(value: "123456789012345678901234567890")
        let b = BigInteger(value: "987654321098765432109876543210")

        XCTAssertEqual(a.add(other: b).description, "1111111110111111111011111111100")
        XCTAssertEqual(
            a.multiply(other: b).description,
            "121932631137021795226185032733622923332237463801111263526900"
        )
    }

    func testLibTomMathBackedOperations() {
        let modulus = BigInteger(value: "497")
        XCTAssertEqual(BigInteger(value: "4").modPow(exponent: BigInteger(value: "13"), modulus: modulus).description, "445")

        let mersenne127 = BigInteger(value: "2").pow(exponent: 127).subtract(other: BigInteger(value: "1"))
        XCTAssertTrue(mersenne127.isProbablePrime(certainty: 50))
        XCTAssertEqual(
            mersenne127.gcd(other: BigInteger(value: "170141183460469231731687303715884105727")).description,
            "170141183460469231731687303715884105727"
        )
    }

    func testBigDecimalArithmetic() {
        let a = BigDecimal(value: BigInteger(value: "125"), scale: 2)
        let b = BigDecimal(value: BigInteger(value: "250"), scale: 2)

        XCTAssertEqual(a.add(other: b).description, "3.75")
        XCTAssertEqual(a.multiply(other: b).description, "3.1250")
    }
}
