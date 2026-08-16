package io.github.artificialpb.bignum

actual val verifiesJvmBigDecimalDoubleFactoryCorpus: Boolean = true

actual object DifferentialFixtureTextLoader {
    actual val retainsCases: Boolean = false

    actual fun load(operation: DifferentialOperation): String = loadFixtureText(
        directoryName = "differential",
        fileName = operation.fixtureFileName,
        environmentName = "BIGNUM_DIFFERENTIAL_FIXTURE_DIR",
    )
}

actual object BigDecimalDifferentialFixtureTextLoader {
    actual fun load(operation: BigDecimalDifferentialOperation): String = loadFixtureText(
        directoryName = "differential-bigdecimal",
        fileName = operation.fixtureFileName,
        environmentName = "BIGNUM_BIGDECIMAL_DIFFERENTIAL_FIXTURE_DIR",
    )
}

private fun loadFixtureText(
    directoryName: String,
    fileName: String,
    environmentName: String,
): String {
    val workingDirectory = processWorkingDirectory()
    val configuredDirectory = processEnvironment(environmentName)
    val candidates = buildList {
        if (configuredDirectory != null) add("$configuredDirectory/$fileName")
        add("$workingDirectory/kotlin/$directoryName/$fileName")
        add("$workingDirectory/src/commonTest/resources/$directoryName/$fileName")
        add("$workingDirectory/bignum/src/commonTest/resources/$directoryName/$fileName")
        add("$workingDirectory/build/processedResources/js/test/$directoryName/$fileName")
        add("$workingDirectory/bignum/build/processedResources/js/test/$directoryName/$fileName")
    }
    val path = candidates.firstOrNull(::fileExists)
        ?: error("Unable to locate fixture $fileName in $candidates")
    return readUtf8File(path)
}

@Suppress("UnsafeCastFromDynamic")
private fun processWorkingDirectory(): String = js("process.cwd()")

@Suppress("UnsafeCastFromDynamic")
private fun processEnvironment(name: String): String? = js("process.env[name]")

@Suppress("UnsafeCastFromDynamic")
private fun fileExists(path: String): Boolean = js("require('fs').existsSync(path)")

@Suppress("UnsafeCastFromDynamic")
private fun readUtf8File(path: String): String = js("require('fs').readFileSync(path, 'utf8')")
