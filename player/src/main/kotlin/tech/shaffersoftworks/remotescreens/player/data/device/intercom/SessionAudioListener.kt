package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Posts peer-connection events back onto the call's coroutine. WebRTC calls these from its own threads. */
internal class SessionAudioListener(
    private val scope: CoroutineScope,
    private val peerId: () -> String?,
    private val sendCandidate: suspend (String, String, String?, Int) -> Unit,
    private val connected: suspend () -> Unit,
    private val failed: suspend () -> Unit,
) : AudioListener {
    override fun onCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val target = peerId() ?: return
        scope.launch { sendCandidate(target, candidate, sdpMid, sdpMLineIndex) }
    }

    override fun onConnected() {
        scope.launch { connected() }
    }

    override fun onFailed() {
        scope.launch { failed() }
    }
}
