package io.github.artificialpb.bignum

import java.io.File

fun main() {
    DifferentialFixtureGenerator.writeTo(File(".").canonicalFile)
}
