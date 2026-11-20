package tech.shaffersoftworks.remotescreens.server.intercom.tessera

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.Base64
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

internal data class TesseraCandidate(
    val candidate: String,
    val sdpMid: String?,
    val line: Int,
)

/**
 * One call's aiortc process. It turns WebRTC Opus into 16 kHz PCM and the other way around.
 * The PCM itself is posted and read here, because that framing does not need aiortc.
 */
internal class TesseraMediaCall(
    private val scope: CoroutineScope,
    private val http: HttpClient,
    private val onCandidate: (TesseraCandidate) -> Unit,
    private val onFailed: (String) -> Unit,
    script: File = tesseraMediaScript() ?: File("tessera_media.py"),
    python: String = tesseraMediaPython(),
) {
    private val process = ProcessBuilder(python, script.path).redirectErrorStream(false).start()
    private val toProcess = process.outputStream.bufferedWriter()
    private val writeLock = Mutex()
    private val waitingSdp = AtomicReference<CompletableDeferred<String>?>(null)
    private val fromBrowser = Channel<ByteArray>(Channel.UNLIMITED)
    private var closed = false
    private var talkBody: ByteChannel? = null
    private var talkJob: Job? = null
    private var listenJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) { readErrors() }
        scope.launch(Dispatchers.IO) { readLines() }
    }

    suspend fun offer(): String = command(buildJsonObject { put("cmd", "offer") })

    suspend fun answer(offerSdp: String): String = command(buildJsonObject {
        put("cmd", "answer")
        put("sdp", offerSdp)
    })

    suspend fun accept(answerSdp: String) {
        write(buildJsonObject {
            put("cmd", "accept")
            put("sdp", answerSdp)
        })
    }

    suspend fun candidate(candidate: TesseraCandidate) {
        write(buildJsonObject {
            put("cmd", "candidate")
            put("candidate", candidate.candidate)
            put("sdpMid", candidate.sdpMid ?: "0")
            put("sdpMLineIndex", candidate.line)
        })
    }

    fun attach(listenUrl: String, talkUrl: String) {
        if (listenUrl.isEmpty() || talkUrl.isEmpty() || listenJob != null) return

        listenJob = scope.launch { listen(listenUrl) }
        talkJob = scope.launch { talk(talkUrl) }
    }

    fun close() {
        if (closed) return

        closed = true
        waitingSdp.getAndSet(null)?.cancel()
        listenJob?.cancel()
        talkJob?.cancel()
        talkBody?.close()
        scope.launch { write(buildJsonObject { put("cmd", "hangup") }) }
        process.destroy()
    }

    private suspend fun command(payload: JsonObject): String {
        val waiting = CompletableDeferred<String>()
        waitingSdp.set(waiting)
        write(payload)

        return withTimeout(SDP_WAIT_MS.milliseconds) { waiting.await() }
    }

    private suspend fun listen(url: String) {
        val audio = MjpegAudio()
        val framing = PcmFraming()

        http.prepareGet(url).execute { response ->
            readAudio(response.bodyAsChannel(), audio, framing)
        }
    }

    private suspend fun discard(channel: ByteReadChannel) {
        val buffer = ByteArray(READ_CHUNK_BYTES)

        while (true) {
            val count = channel.readAvailable(buffer)

            if (count < 0) return

            if (count == 0) channel.awaitContent()
        }
    }

    private suspend fun readAudio(channel: ByteReadChannel, audio: MjpegAudio, framing: PcmFraming) {
        val buffer = ByteArray(READ_CHUNK_BYTES)

        while (!closed) {
            val count = channel.readAvailable(buffer)

            if (count < 0) return

            if (count == 0) {
                channel.awaitContent()

                continue
            }

            val pieces = audio.feed(buffer.copyOf(count))

            for (piece in pieces) {
                for (part in framing.feed(piece)) sendPcm(part)
            }
        }
    }

    private suspend fun talk(url: String) {
        val body = ByteChannel()
        talkBody = body

        val posting = scope.launch {
            http.preparePost(url) { setBody(body) }.execute { response ->
                discard(response.bodyAsChannel())
            }
        }

        try {
            for (part in fromBrowser) body.writeFully(part)
        } finally {
            body.close()
            posting.cancel()
        }
    }

    private suspend fun sendPcm(part: ByteArray) {
        write(buildJsonObject {
            put("cmd", "pcm")
            put("data", Base64.getEncoder().encodeToString(part))
        })
    }

    private suspend fun readLines() {
        val lines = process.inputStream.bufferedReader()
        var line = withContext(Dispatchers.IO) { lines.readLine() }

        while (!closed && line != null) {
            val text = line

            val message = runCatching { JSON.parseToJsonElement(text) as JsonObject }.getOrNull()

            if (message != null) onLine(message)

            line = withContext(Dispatchers.IO) { lines.readLine() }
        }

        if (!closed) fail("media stopped")
    }

    private fun onLine(message: JsonObject) {
        val failed = message.text("failed")
        val sdp = message.text("sdp")
        val pcm = message.text("pcm")
        val candidate = message.text("candidate")

        when {
            failed.isNotEmpty() -> fail(failed)
            sdp.isNotEmpty() -> waitingSdp.getAndSet(null)?.complete(sdp)
            pcm.isNotEmpty() -> fromBrowser.trySend(Base64.getDecoder().decode(pcm))
            candidate.isNotEmpty() -> onCandidate(
                TesseraCandidate(
                    candidate = candidate,
                    sdpMid = message.text("sdpMid").ifEmpty { null },
                    line = message.int("sdpMLineIndex"),
                ),
            )
        }
    }

    private fun fail(message: String) {
        waitingSdp.getAndSet(null)?.completeExceptionally(IllegalStateException(message))

        if (!closed) onFailed(message)
    }

    private suspend fun readErrors() {
        val errors = process.errorStream.bufferedReader()

        while (!closed) {
            val line = withContext(Dispatchers.IO) { errors.readLine() } ?: return

            if (line.isNotBlank()) logger.info("Tessera media: {}", line)
        }
    }

    private suspend fun write(payload: JsonObject) = writeLock.withLock {
        withContext(Dispatchers.IO) {
            toProcess.write(JSON.encodeToString(JsonObject.serializer(), payload))
            toProcess.write("\n")
            toProcess.flush()
        }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        const val SDP_WAIT_MS = 5_000L
        const val READ_CHUNK_BYTES = 8_192
        private val logger = LoggerFactory.getLogger(TesseraMediaCall::class.java)
    }
}

internal fun tesseraMediaScript(): File? {
    val fromEnvironment = System.getenv("TESSERA_MEDIA_SCRIPT")?.let(::File)
    val candidates = listOfNotNull(fromEnvironment, File("/app/tessera_media.py"), File("addon/tessera_media.py"))

    return candidates.firstOrNull { file -> file.isFile }
}

internal fun tesseraMediaPython(): String = System.getenv("TESSERA_MEDIA_PYTHON") ?: "python3"

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
