package tech.shaffersoftworks.remotescreens.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoProtocolTest {
    @Test
    fun aVideoReadsBackAsSent() {
        val video = ServerMessage.Video(Slot.ALERT, 7, 0x5C_0000, "/stream/hls/abc/master_playlist.m3u8", crop = true)

        assertEquals(video, Protocol.decodeServer(Protocol.encode(video)))
        assertEquals(
            """{"type":"video","slot":"alert","rev":7,"componentId":6029312,""" +
                """"path":"/stream/hls/abc/master_playlist.m3u8","crop":true}""",
            Protocol.encode(video),
        )
    }

    @Test
    fun aFailedVideoReadsBackWithOrWithoutItsHttpCode() {
        val expired = PlayerMessage.VideoFailed(Slot.OVERLAY, 3, 0x5C_0000, httpCode = 404)
        val stalled = PlayerMessage.VideoFailed(Slot.OVERLAY, 3, 0x5C_0000)

        assertEquals(expired, Protocol.decodePlayer(Protocol.encode(expired)))
        assertEquals(stalled, Protocol.decodePlayer(Protocol.encode(stalled)))
    }
}
