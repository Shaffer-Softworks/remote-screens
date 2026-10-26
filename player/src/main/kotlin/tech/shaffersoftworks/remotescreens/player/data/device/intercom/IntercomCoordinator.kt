package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage

/** Opens and closes the tablet's intercom as the add-on turns the page on and off. */
@Singleton
class IntercomCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val link: SignalingLink,
    private val audio: WebRtcAudio,
) {
    private var session: IntercomSession? = null
    private var signature: String? = null

    fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(
        scope: CoroutineScope,
        url: String,
        clientId: String,
        displayName: String,
        onReport: (PlayerMessage.IntercomReport) -> Unit,
        onWake: () -> Unit,
    ) {
        val next = "$url|$clientId|$displayName"
        val current = session
        if (current != null && signature == next) {
            current.attachReport(onReport)
            current.publishAgain()
            return
        }
        stop()
        signature = next
        val opened = IntercomSession(
            link,
            audio,
            scope,
            IntercomRegistration(url, clientId, displayName),
            onReport,
            onWake,
        )
        session = opened
        opened.start()
    }

    fun stop() {
        session?.stop()
        session = null
        signature = null
    }

    fun command(verb: String, clientId: String) {
        session?.command(verb, clientId)
    }
}
