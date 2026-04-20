package io.github.artificialpb.bignum

import java.io.File

fun main(args: Array<String>) {
    val seed = parseBigDecimalSeedArgument(args)
    BigDecimalDifferentialFixtureGenerator.writeTo(
        projectDir = File(".").canonicalFile,
        seed = seed ?: BigDecimalDifferentialFixtureGenerator.defaultSeed(),
    )
}

private fun parseBigDecimalSeedArgument(args: Array<String>): Long? {
    var index = 0
    while (index < args.size) {
        when (val argument = args[index]) {
            "--seed" -> {
                require(index + 1 < args.size) { "Missing value for --seed" }
                return BigDecimalDifferentialFixtureGenerator.configuredSeed(args[index + 1])
            }

            else -> {
                if (argument.startsWith("--seed=")) {
                    return BigDecimalDifferentialFixtureGenerator.configuredSeed(argument.substringAfter('='))
                }
                error("Unknown argument: $argument")
            }
        }
        index += 1
    }
    return null
}
