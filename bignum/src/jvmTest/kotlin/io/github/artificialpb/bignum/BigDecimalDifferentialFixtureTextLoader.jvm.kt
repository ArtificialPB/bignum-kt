package io.github.artificialpb.bignum

import java.io.File

actual object BigDecimalDifferentialFixtureTextLoader {
    actual fun load(operation: BigDecimalDifferentialOperation): String {
        val directory = System.getProperty("bignum.bigdecimal.differential.fixtureDir")
        if (directory != null) {
            val file = File(directory, operation.fixtureFileName)
            if (file.isFile) {
                return file.readText()
            }
        }

        val resourcePath = "/differential-bigdecimal/${operation.fixtureFileName}"
        return BigDecimalDifferentialFixtureTextLoader::class.java.getResourceAsStream(resourcePath)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Unable to load BigDecimal differential fixture ${operation.fixtureFileName}")
    }
}
