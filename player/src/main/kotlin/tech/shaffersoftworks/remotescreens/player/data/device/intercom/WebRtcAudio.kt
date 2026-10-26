package tech.shaffersoftworks.remotescreens.player.data.device.intercom

/** Audio-only peer connection. The call machine talks to this so tests never load the native WebRTC library. */
interface WebRtcAudio {
    fun start(listener: AudioListener)
    suspend fun createOffer(): String
    suspend fun acceptOffer(sdp: String): String
    suspend fun acceptAnswer(sdp: String)
    suspend fun rollbackAndAnswer(offer: String): String?
    fun addCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)
    fun setMuted(isMuted: Boolean)
    fun stop()
}

/** What the peer connection reports back while a call is up. Called from WebRTC's own threads. */
interface AudioListener {
    fun onCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)
    fun onConnected()
    fun onFailed()
}
