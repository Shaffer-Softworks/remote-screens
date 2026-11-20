package tech.shaffersoftworks.remotescreens.server.intercom.tessera

/** 16 kHz 16-bit mono, one part every 20 ms. Tessera drops a part that is cut mid-sample. */
internal object PcmAudio {
    const val SAMPLE_RATE_HZ = 16_000
    const val BYTES_PER_SAMPLE = 2
    const val PART_MS = 20
    const val PART_BYTES = SAMPLE_RATE_HZ * BYTES_PER_SAMPLE * PART_MS / 1_000
    const val MAX_STREAM_BYTES = 512 * 1_024
}

/** Holds back a trailing fragment until a whole [PcmAudio.PART_BYTES] part is in hand. */
internal class PcmFraming {
    private var spare = ByteArray(0)

    fun feed(chunk: ByteArray): List<ByteArray> {
        val combined = spare + chunk
        val whole = combined.size - combined.size % PcmAudio.PART_BYTES
        spare = combined.copyOfRange(whole, combined.size)

        if (whole == 0) return emptyList()

        return List(whole / PcmAudio.PART_BYTES) { index ->
            val start = index * PcmAudio.PART_BYTES

            combined.copyOfRange(start, start + PcmAudio.PART_BYTES)
        }
    }
}

/**
 * The audio parts of a Tessera live stream. Each part is `--frame`, a header, then `Content-Length` bytes.
 * Pictures are skipped. A part that is not whole yet stays buffered.
 */
internal class MjpegAudio {
    private var pending = ByteArray(0)

    fun feed(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()

        pending += chunk
        val audio = mutableListOf<ByteArray>()
        var more = true

        while (more) more = takePart(audio)

        if (pending.size > PcmAudio.MAX_STREAM_BYTES) pending = ByteArray(0)

        return audio
    }

    private fun takePart(audio: MutableList<ByteArray>): Boolean {
        val start = indexOf(pending, FRAME)

        if (start < 0) {
            pending = tail(pending, FRAME.size - 1)

            return false
        }

        return consumePart(audio, start)
    }

    private fun consumePart(audio: MutableList<ByteArray>, start: Int): Boolean {
        if (start > 0) pending = pending.copyOfRange(start, pending.size)

        val headerEnd = indexOf(pending, HEADER_END)

        val header = headerEnd.takeIf { end -> end >= 0 }?.let { end ->
            pending.copyOfRange(0, end).decodeToString()
        }

        val length = header?.let(::contentLength)
        val bodyStart = headerEnd + HEADER_END.size

        return when {
            headerEnd < 0 -> false
            length == null -> skipBrokenMarker()
            pending.size < bodyStart + length -> false
            else -> emitAudio(audio, header.orEmpty(), bodyStart, length)
        }
    }

    private fun skipBrokenMarker(): Boolean {
        pending = pending.copyOfRange(FRAME.size, pending.size)

        return true
    }

    private fun emitAudio(audio: MutableList<ByteArray>, header: String, bodyStart: Int, length: Int): Boolean {
        val body = pending.copyOfRange(bodyStart, bodyStart + length)
        pending = pending.copyOfRange(bodyStart + length, pending.size)

        if (header.contains(AUDIO_TYPE)) audio += body

        return true
    }

    private fun contentLength(header: String): Int? {
        val line = header.lineSequence().firstOrNull { item ->
            item.startsWith(LENGTH_PREFIX, ignoreCase = true)
        } ?: return null

        return line.substringAfter(":").trim().toIntOrNull()
    }

    private fun indexOf(data: ByteArray, needle: ByteArray): Int {
        if (data.size < needle.size) return -1

        val found = (0..data.size - needle.size).firstOrNull { index -> matches(data, index, needle) }

        return found ?: -1
    }

    private fun matches(data: ByteArray, start: Int, needle: ByteArray): Boolean {
        for (offset in needle.indices) {
            if (data[start + offset] != needle[offset]) return false
        }

        return true
    }

    private fun tail(data: ByteArray, keep: Int): ByteArray {
        if (data.size <= keep) return data

        return data.copyOfRange(data.size - keep, data.size)
    }

    private companion object {
        val FRAME = "--frame\r\n".encodeToByteArray()
        val HEADER_END = "\r\n\r\n".encodeToByteArray()
        const val AUDIO_TYPE = "audio/x-s16le"
        const val LENGTH_PREFIX = "Content-Length:"
    }
}
