package io.github.eyuppastirmaci.dioptra.infrastructure.redis.codec

class BinaryValueDetector {

    /**
     * Detects likely binary values by checking null bytes and non-printable control characters.
     */
    fun isBinary(value: ByteArray): Boolean {
        if (value.isEmpty()) {
            return false
        }

        if (value.any { byte -> byte.toInt() == 0 }) {
            return true
        }

        val controlCharacterCount = value.count { byte ->
            val unsignedByte = byte.toInt() and 0xFF

            unsignedByte < 0x09 ||
                    unsignedByte in 0x0E..0x1F ||
                    unsignedByte == 0x7F
        }

        return controlCharacterCount > value.size / 10
    }
}