package io.github.eyuppastirmaci.dioptra.infrastructure.redis.codec

object HexDumpFormatter {

    private const val BYTES_PER_LINE = 16

    fun lines(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) {
            return listOf("(empty)")
        }

        val result = mutableListOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + BYTES_PER_LINE, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            val hexPart = chunk.joinToString(separator = " ") { byte ->
                byte.toUByte().toString(radix = 16).padStart(length = 2, padChar = '0')
            }
            result += "%08x  %s".format(offset, hexPart)
            offset = end
        }
        return result
    }
}
