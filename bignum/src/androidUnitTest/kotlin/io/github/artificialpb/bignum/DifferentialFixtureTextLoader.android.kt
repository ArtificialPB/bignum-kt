package io.github.artificialpb.bignum

import java.io.File

actual object DifferentialFixtureTextLoader {
    actual fun load(operation: DifferentialOperation): String {
        val directory = System.getProperty("bignum.differential.fixtureDir")
        if (directory != null) {
            val file = File(directory, operation.fixtureFileName)
            if (file.isFile) {
                return file.readText()
            }
        }

        val resourcePath = "/differential/${operation.fixtureFileName}"
        val resourceText = DifferentialFixtureTextLoader::class.java.getResourceAsStream(resourcePath)
            ?.bufferedReader()
            ?.use { it.readText() }
        if (resourceText != null) {
            return resourceText
        }

        error("Unable to load differential fixture ${operation.fixtureFileName}")
    }
}
