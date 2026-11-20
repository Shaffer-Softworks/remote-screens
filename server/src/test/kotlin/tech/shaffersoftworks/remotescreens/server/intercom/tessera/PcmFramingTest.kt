package tech.shaffersoftworks.remotescreens.server.intercom.tessera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmFramingTest {
    @Test
    fun aShortChunkWaitsForAWholePart() {
        val framing = PcmFraming()
        val short = ByteArray(PcmAudio.PART_BYTES - 1) { 1 }

        assertTrue(framing.feed(short).isEmpty())
    }

    @Test
    fun aRemainderCompletesTheNextPart() {
        val framing = PcmFraming()
        framing.feed(ByteArray(LEADING_BYTES) { 1 })
        val parts = framing.feed(ByteArray(PcmAudio.PART_BYTES - LEADING_BYTES + FORTY) { 2 })
        val rest = framing.feed(ByteArray(PcmAudio.PART_BYTES - FORTY) { 3 })

        assertEquals(1, parts.size)
        assertEquals(PcmAudio.PART_BYTES, parts.single().size)
        assertEquals(1, rest.size)
        assertEquals(PART_BYTES, PcmAudio.PART_BYTES)
    }

    @Test
    fun anExactPartLeavesNothingWaiting() {
        val parts = PcmFraming().feed(ByteArray(PcmAudio.PART_BYTES) { 3 })

        assertEquals(1, parts.size)
        assertTrue(PcmFraming().feed(parts.single()).single().contentEquals(parts.single()))
    }

    @Test
    fun aLiveStreamYieldsItsAudioAndSkipsThePicture() {
        val audio = ByteArray(PcmAudio.PART_BYTES) { 7 }
        val picture = byteArrayOf(1, 2, 3, 4)

        val stream = part(type = "audio/x-s16le;rate=16000", body = audio) +
            part(type = "image/jpeg", body = picture)

        val found = MjpegAudio().feed(stream)

        assertEquals(1, found.size)
        assertTrue(found.single().contentEquals(audio))
    }

    @Test
    fun aPartSplitAcrossReadsIsStillWhole() {
        val audio = ByteArray(EIGHT) { 9 }
        val stream = part(type = "audio/x-s16le;rate=16000", body = audio)
        val parser = MjpegAudio()
        val split = stream.size / 2

        assertTrue(parser.feed(stream.copyOfRange(0, split)).isEmpty())
        val found = parser.feed(stream.copyOfRange(split, stream.size))

        assertTrue(found.single().contentEquals(audio))
    }

    private fun part(type: String, body: ByteArray): ByteArray {
        val header = "--frame\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nX-Made: 1\r\n\r\n"

        return header.encodeToByteArray() + body
    }

    private companion object {
        const val FORTY = 40
        const val EIGHT = 8
        const val LEADING_BYTES = 100
        const val PART_BYTES = 640
    }
}
