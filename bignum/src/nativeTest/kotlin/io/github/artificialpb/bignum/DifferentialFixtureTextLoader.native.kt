package io.github.artificialpb.bignum

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.F_OK
import platform.posix.SEEK_END
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getenv
import platform.posix.rewind

actual object DifferentialFixtureTextLoader {
    @OptIn(ExperimentalForeignApi::class)
    actual fun load(operation: DifferentialOperation): String {
        val fileName = operation.fixtureFileName
        val directory = getenv("BIGNUM_DIFFERENTIAL_FIXTURE_DIR")?.toKString()
        val candidates = listOfNotNull(
            directory?.let { "$it/$fileName" },
            "src/commonTest/resources/differential/$fileName",
            "bignum/src/commonTest/resources/differential/$fileName",
        )

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
}
