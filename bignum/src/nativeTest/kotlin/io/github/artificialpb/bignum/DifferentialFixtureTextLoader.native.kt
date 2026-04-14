package io.github.artificialpb.bignum

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.Foundation.NSProcessInfo
import platform.posix.F_OK
import platform.posix.PATH_MAX
import platform.posix.SEEK_END
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getenv
import platform.posix.getcwd
import platform.posix.rewind

actual object DifferentialFixtureTextLoader {
    @OptIn(ExperimentalForeignApi::class)
    actual fun load(operation: DifferentialOperation): String {
        val fileName = operation.fixtureFileName
        val candidates = candidatePaths(fileName)

        val path = candidates.firstOrNull { access(it, F_OK) == 0 } ?: error(
            "Unable to locate differential fixture $fileName in $candidates",
        )
        return readText(path)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readText(path: String): String {
        val file = fopen(path, "rb") ?: error("Unable to open differential fixture $path")
        try {
            check(fseek(file, 0, SEEK_END) == 0) { "Unable to seek differential fixture $path" }
            val size = ftell(file)
            check(size >= 0) { "Unable to size differential fixture $path" }
            rewind(file)

            val bytes = ByteArray(size.toInt())
            if (bytes.isNotEmpty()) {
                val read = bytes.usePinned { pinned ->
                    fread(pinned.addressOf(0), 1.convert(), size.convert(), file).toLong()
                }
                check(read == size) { "Unable to read differential fixture $path" }
            }
            return bytes.decodeToString()
        } finally {
            fclose(file)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun candidatePaths(fileName: String): List<String> {
        val candidates = linkedSetOf<String>()

        getenv("BIGNUM_DIFFERENTIAL_FIXTURE_DIR")?.toKString()?.let { directory ->
            candidates += "$directory/$fileName"
        }

        val searchRoots = linkedSetOf<String>()
        currentWorkingDirectory()?.let { workingDirectory ->
            ancestorDirectories(workingDirectory).forEach(searchRoots::add)
        }
        executablePath()?.let { path ->
            parentDirectory(path)?.let { executableDirectory ->
                ancestorDirectories(executableDirectory).forEach(searchRoots::add)
            }
        }

        searchRoots.forEach { root ->
            candidates += "$root/src/commonTest/resources/differential/$fileName"
            candidates += "$root/bignum/src/commonTest/resources/differential/$fileName"
            candidates += "$root/differential/$fileName"
            nativeProcessedResourceTargets.forEach { target ->
                candidates += "$root/build/processedResources/$target/test/differential/$fileName"
                candidates += "$root/processedResources/$target/test/differential/$fileName"
            }
        }

        return candidates.toList()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun currentWorkingDirectory(): String? = memScoped {
        val buffer = allocArray<ByteVar>(PATH_MAX)
        getcwd(buffer, PATH_MAX.toULong())?.toKString()
    }

    private fun executablePath(): String? = NSProcessInfo.processInfo.arguments.firstOrNull()?.toString()

    private fun ancestorDirectories(start: String): List<String> {
        if (start.isEmpty()) return emptyList()

        val directories = mutableListOf<String>()
        var current = start.removeSuffix("/").ifEmpty { "/" }
        while (true) {
            directories += current
            if (current == "/") break
            val parent = parentDirectory(current) ?: break
            if (parent == current) break
            current = parent
        }
        return directories
    }

    private fun parentDirectory(path: String): String? {
        val normalized = path.removeSuffix("/").ifEmpty { return "/" }
        val separatorIndex = normalized.lastIndexOf('/')
        return when {
            separatorIndex < 0 -> null
            separatorIndex == 0 -> "/"
            else -> normalized.substring(0, separatorIndex)
        }
    }

    private val nativeProcessedResourceTargets = listOf(
        "iosSimulatorArm64",
        "iosX64",
        "iosArm64",
        "macosArm64",
    )
}
