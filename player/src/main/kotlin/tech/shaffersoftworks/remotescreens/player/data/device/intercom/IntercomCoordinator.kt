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
    private var sessionScope: CoroutineScope? = null

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
        // A kiosk relaunch can replace the ViewModel and cancel the session's original scope.
        if (current?.isActive == true && signature == next && sessionScope === scope) {
            current.attachReport(onReport)
            current.publishAgain()
            return
        }
        stop()
        signature = next
        sessionScope = scope
        val opened = IntercomSession(
            link = link,
            audio = audio,
            scope = scope,
            registration = IntercomRegistration(url, clientId, displayName),
            onReport = onReport,
            onWake = onWake,
        )
        session = opened
        opened.start()
    }

    fun stop() {
        session?.stop()
        session = null
        signature = null
        sessionScope = null
    }

    fun command(verb: String, clientId: String) {
        session?.command(verb, clientId)
    }
}
