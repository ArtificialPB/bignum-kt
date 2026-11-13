@file:OptIn(ExperimentalForeignApi::class)

package io.github.artificialpb.bignum

import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

internal fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) {
        result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
    return result
}

internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}
