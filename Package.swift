// swift-tools-version: 5.9
import PackageDescription

// Updated by scripts/release-version.sh during the Release workflow.
let releaseVersion = "1.0.5"
let releaseChecksum = "0000000000000000000000000000000000000000000000000000000000000000"

// Set BIGNUM_USE_LOCAL_XCFRAMEWORK=1 to build against the XCFramework produced by
// `./gradlew :bignum:assembleBignumKtReleaseXCFramework` instead of the released binary.
let kotlinBinary: Target
if Context.environment["BIGNUM_USE_LOCAL_XCFRAMEWORK"] == "1" {
    kotlinBinary = .binaryTarget(
        name: "BignumKt",
        path: "bignum/build/XCFrameworks/release/BignumKt.xcframework"
    )
} else {
    kotlinBinary = .binaryTarget(
        name: "BignumKt",
        url: "https://github.com/ArtificialPB/bignum-kt/releases/download/v\(releaseVersion)/BignumKt.xcframework.zip",
        checksum: releaseChecksum
    )
}

let package = Package(
    name: "Bignum",
    platforms: [
        .iOS(.v15),
        .macOS(.v12),
    ],
    products: [
        .library(name: "Bignum", targets: ["Bignum"]),
    ],
    targets: [
        kotlinBinary,
        .target(
            name: "Bignum",
            dependencies: ["BignumKt"],
            path: "swift/Sources/Bignum",
            linkerSettings: [.linkedLibrary("c++")]
        ),
        .testTarget(
            name: "BignumTests",
            dependencies: ["Bignum"],
            path: "swift/Tests/BignumTests"
        ),
    ]
)
