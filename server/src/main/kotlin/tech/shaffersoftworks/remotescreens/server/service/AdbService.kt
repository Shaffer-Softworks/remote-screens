package tech.shaffersoftworks.remotescreens.server.service

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.shaffersoftworks.remotescreens.server.data.apk.PlayerApkRepository

private const val PLAYER_PACKAGE = "tech.shaffersoftworks.remotescreens.player"
private const val OUTPUT_LIMIT = 16 * 1024 * 1024
private const val COMMAND_TIMEOUT_SECONDS = 120L
private const val MAX_IDENTIFIER_LENGTH = 200
private const val MAX_PORT = 65535
private const val ERROR_LIMIT = 2000
private const val READ_BUFFER_SIZE = 8192

@Serializable
data class AdbDevice(val serial: String, val state: String, val description: String)

@Serializable
data class AdbStatus(val devices: List<AdbDevice>, val associations: Map<String, String>)

/** Fixed ADB operations only. A private ADB server keeps add-on keys separate from workstation keys. */
class AdbService(
    dataDir: File,
    private val apks: PlayerApkRepository,
    private val executable: String = System.getenv("REMOTE_SCREENS_ADB") ?: "adb",
    private val commandRunner: (suspend (List<String>, String) -> ByteArray)? = null,
) {
    private val home = File(dataDir, "adb").also { it.mkdirs() }
    private val associationsFile = File(home, "tablets.json")
    private val mutex = Mutex()
    private val associations = if (associationsFile.exists()) {
        Json.decodeFromString<Map<String, String>>(associationsFile.readText()).toMutableMap()
    } else {
        mutableMapOf()
    }

    suspend fun status(): AdbStatus = mutex.withLock {
        val lines = run(listOf("devices", "-l")).decodeToString().lineSequence()
        val devices = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("List ") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 3)
                if (parts.size < 2) null else AdbDevice(parts[0], parts[1], parts.getOrElse(2) { "" })
            }.toList()
        AdbStatus(devices, associations.toMap())
    }

    suspend fun pair(endpoint: String, code: String): String {
        require(code.matches(Regex("[0-9]{6}"))) { "Enter the six-digit pairing code" }
        return run(listOf("pair", endpoint(endpoint)), code + "\n").decodeToString()
    }

    suspend fun connect(endpoint: String): String =
        run(listOf("connect", endpoint(endpoint))).decodeToString()

    suspend fun disconnect(serial: String): String =
        run(listOf("disconnect", endpoint(serial))).decodeToString()

    suspend fun associate(playerId: String, serial: String) = mutex.withLock {
        require(playerId.isNotBlank() && playerId.length <= MAX_IDENTIFIER_LENGTH) { "Invalid tablet" }
        if (serial.isBlank()) associations.remove(playerId) else associations[playerId] = serial(serial)
        withContext(Dispatchers.IO) {
            val temporary = File(home, "tablets.json.tmp")
            temporary.writeText(Json.encodeToString(associations.toMap()))
            java.nio.file.Files.move(
                temporary.toPath(),
                associationsFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }

    suspend fun restart(serial: String): String {
        device(serial, listOf("shell", "am", "force-stop", PLAYER_PACKAGE))
        return device(serial, listOf("shell", "am", "start", "-W", "-n", "$PLAYER_PACKAGE/.MainActivity"))
            .decodeToString()
    }

    suspend fun install(serial: String): String {
        val apk = checkNotNull(apks.current()) { "No packaged player APK is available" }
        return device(serial, listOf("install", "-r", apk.file.absolutePath)).decodeToString()
    }

    suspend fun screenshot(serial: String): ByteArray =
        device(serial, listOf("exec-out", "screencap", "-p"))

    suspend fun logs(serial: String): ByteArray =
        device(serial, listOf("logcat", "-d", "-t", "500", "-v", "threadtime"))

    private suspend fun device(serial: String, args: List<String>): ByteArray =
        run(listOf("-s", serial(serial)) + args)

    private fun serial(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9._:\\[\\]%-]{1,200}")) && !value.startsWith("-")) {
            "Invalid ADB device serial"
        }
        return value
    }

    private fun endpoint(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]*:[0-9]{1,5}"))) {
            "Enter a tablet address as host:port"
        }
        require(value.substringAfterLast(':').toInt() in 1..MAX_PORT) { "Invalid port" }
        return value
    }

    /** Drain both pipes concurrently, cap output, and always terminate unfinished processes. */
    private suspend fun run(args: List<String>, input: String = ""): ByteArray = coroutineScope {
        commandRunner?.let { return@coroutineScope it(args, input) }
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(listOf(executable, "-P", "5038") + args).apply {
                environment()["ANDROID_USER_HOME"] = home.absolutePath
                environment()["HOME"] = home.absolutePath
                environment().remove("ADB_SERVER_SOCKET")
                environment().remove("ADB_VENDOR_KEYS")
            }.start()
            try {
                val stdout = async(Dispatchers.IO) { readOutput(process.inputStream, process) }
                val stderr = async(Dispatchers.IO) { readOutput(process.errorStream, process) }
                process.outputStream.bufferedWriter().use { it.write(input) }
                check(process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "ADB command timed out" }
                val output = stdout.await()
                val error = stderr.await().decodeToString()
                check(process.exitValue() == 0) { error.ifBlank { output.decodeToString() }.take(ERROR_LIMIT) }
                output
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }

    private fun readOutput(stream: java.io.InputStream, process: Process): ByteArray = stream.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            if (output.size() + count > OUTPUT_LIMIT) {
                process.destroyForcibly()
                error("ADB output exceeded the size limit")
            }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}
