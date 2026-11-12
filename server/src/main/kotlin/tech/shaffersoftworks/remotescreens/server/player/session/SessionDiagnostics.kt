package tech.shaffersoftworks.remotescreens.server.player.session

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import tech.shaffersoftworks.remotescreens.shared.CrashTestResult
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import kotlin.time.Duration.Companion.milliseconds

private const val TEST_REPLY_TIMEOUT_MS = 15_000L

/** One explicit test at a time; late responses cannot complete a later request. */
internal class SessionDiagnostics(private val send: suspend (ServerMessage) -> Unit) {
    private data class Pending(val id: String, val reply: CompletableDeferred<CrashTestResult>)
    private val pending = AtomicReference<Pending?>()

    suspend fun test(): CrashTestResult {
        val request = Pending(UUID.randomUUID().toString(), CompletableDeferred())
        if (!pending.compareAndSet(null, request)) return CrashTestResult("busy")

        return try {
            send(ServerMessage.SentryTest(request.id))
            withTimeoutOrNull(TEST_REPLY_TIMEOUT_MS.milliseconds) {
                request.reply.await()
            } ?: CrashTestResult("timeout")
        } finally {
            pending.compareAndSet(request, null)
        }
    }

    fun receive(message: PlayerMessage.SentryTestResult) {
        pending.get()?.takeIf {
            it.id == message.requestId
        }?.reply?.complete(message.result)
    }

    fun stop() {
        pending.getAndSet(null)?.reply?.complete(CrashTestResult("offline"))
    }
}
