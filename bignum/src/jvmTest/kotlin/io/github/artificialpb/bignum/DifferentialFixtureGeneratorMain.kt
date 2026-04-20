package io.github.artificialpb.bignum

import java.io.File

fun main(args: Array<String>) {
    val seed = parseSeedArgument(args)
    DifferentialFixtureGenerator.writeTo(
        projectDir = File(".").canonicalFile,
        seed = seed ?: DifferentialFixtureGenerator.defaultSeed(),
    )
}

private fun parseSeedArgument(args: Array<String>): Long? {
    var index = 0
    while (index < args.size) {
        when (val argument = args[index]) {
            "--seed" -> {
                require(index + 1 < args.size) { "Missing value for --seed" }
                return DifferentialFixtureGenerator.configuredSeed(args[index + 1])
            }

            else -> {
                if (argument.startsWith("--seed=")) {
                    return DifferentialFixtureGenerator.configuredSeed(argument.substringAfter('='))
                }
                error("Unknown argument: $argument")
            }
        }
        index += 1
    }
    return null
}
