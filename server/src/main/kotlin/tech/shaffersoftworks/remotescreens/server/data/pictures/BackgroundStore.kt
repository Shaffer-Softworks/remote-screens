package tech.shaffersoftworks.remotescreens.server.data.pictures

import tech.shaffersoftworks.remotescreens.shared.Page
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

private const val BACKGROUNDS_DIR = "backgrounds"
private const val MAX_ID_LENGTH = 64
private const val MAX_TOKEN_LENGTH = 20
private const val JPEG_FIRST_BYTE = 0xFF
private const val JPEG_SECOND_BYTE = 0xD8
private const val JPEG_SIGNATURE_LENGTH = 2
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,$MAX_ID_LENGTH}$")
private val TOKEN = Regex("^[0-9]{1,$MAX_TOKEN_LENGTH}$")
private val RELATIVE_FILE = Regex(
    "^[A-Za-z0-9_-]{1,$MAX_ID_LENGTH}/[A-Za-z0-9_-]{1,$MAX_ID_LENGTH}-[0-9]{1,$MAX_TOKEN_LENGTH}$",
)

/** A page wallpaper the editor uploaded. [isPng] is false for a JPEG. */
class StoredBackground(val bytes: ByteArray, val isPng: Boolean)

/** The picture was not a JPEG or PNG the add-on can keep. */
class BackgroundRejected(message: String) : IllegalArgumentException(message)

/**
 * Page wallpapers on disk, under `backgrounds/<screenId>/<pageId>-<token>.jpg`.
 * The layout stores only the token. A `file:` source names one of these and nothing outside the folder.
 */
class BackgroundStore(dataDir: File) {
    private val root = File(dataDir, BACKGROUNDS_DIR)

    /** Writes [bytes] and returns the token the page should store. */
    fun save(screenId: String, pageId: String, bytes: ByteArray): String {
        val reason = when {
            !isSafeId(screenId) || !isSafeId(pageId) -> "Unknown page"
            bytes.size > MAX_BYTES -> "Image must be 8 MB or smaller"
            else -> null
        }
        if (reason != null) throw BackgroundRejected(reason)
        val isPng = imageFormat(bytes) ?: throw BackgroundRejected("Use a JPEG or PNG")
        val token = System.currentTimeMillis().toString()
        val directory = File(root, screenId)
        directory.mkdirs()
        File(directory, fileName(pageId, token, isPng)).writeBytes(bytes)
        return token
    }

    /** The bytes for [token], or null when that page has no such file. */
    fun read(screenId: String, pageId: String, token: String): StoredBackground? {
        val file = locate(screenId, pageId, token) ?: return null
        return StoredBackground(file.readBytes(), file.extension == PNG_EXTENSION)
    }

    /**
     * The file [source] names (`file:<screenId>/<pageId>-<token>`), or null when it is missing or leaves this folder.
     */
    fun resolve(source: String): File? {
        val relative = source.removePrefix(FILE_PREFIX)
            .takeIf { source.startsWith(FILE_PREFIX) && RELATIVE_FILE.matches(it) }
        val file = relative?.let { path ->
            val stem = path.substringAfter('/')
            locate(
                path.substringBefore('/'),
                stem.substringBeforeLast('-'),
                stem.substringAfterLast('-'),
            )
        }
        val canonical = file?.canonicalFile
        val inside = canonical != null &&
            canonical.path.startsWith(root.canonicalFile.path + File.separator) &&
            canonical.isFile
        return canonical?.takeIf { inside }
    }

    /** Length and last-modified time, so replacing the file sends it to the tablet again. */
    fun sourceKey(source: String): String? {
        val file = resolve(source) ?: return null
        return "${file.length()}:${file.lastModified()}"
    }

    /** Deletes wallpapers in [screenId] that [pages] no longer name. */
    fun deleteUnreferenced(screenId: String, pages: List<Page>) {
        val directory = directoryOf(screenId) ?: return
        val kept = pages.mapNotNull { page ->
            val token = page.backgroundImage ?: return@mapNotNull null
            locate(screenId, page.id, token)?.name
        }.toSet()
        directory.listFiles()?.filter { it.isFile && it.name !in kept }?.forEach { it.delete() }
    }

    /** Deletes every wallpaper stored for [screenId]. */
    fun deleteScreen(screenId: String) {
        val directory = directoryOf(screenId) ?: return
        directory.deleteRecursively()
    }

    private fun directoryOf(screenId: String): File? {
        if (!isSafeId(screenId)) return null
        val directory = File(root, screenId)
        return directory.takeIf { it.isDirectory }
    }

    private fun locate(screenId: String, pageId: String, token: String): File? {
        if (!isSafeId(screenId) || !isSafeId(pageId) || !TOKEN.matches(token)) return null
        val directory = File(root, screenId)
        val png = File(directory, fileName(pageId, token, isPng = true))
        val jpeg = File(directory, fileName(pageId, token, isPng = false))
        return when {
            png.isFile -> png
            jpeg.isFile -> jpeg
            else -> null
        }
    }

    private fun imageFormat(bytes: ByteArray): Boolean? {
        val png = when {
            isPng(bytes) -> true
            isJpeg(bytes) -> false
            else -> null
        }
        val decoded = png?.let { runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() }
        return if (isImage(decoded)) png else null
    }

    private fun isImage(image: BufferedImage?): Boolean = image != null && image.width >= 1 && image.height >= 1

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val FILE_PREFIX = "file:"

        /** The picture source for a page wallpaper. Null when an id could leave the backgrounds folder. */
        fun pictureSource(screenId: String, pageId: String, token: String): String? {
            if (!isSafeId(screenId) || !isSafeId(pageId) || !TOKEN.matches(token)) return null
            return "$FILE_PREFIX$screenId/${fileName(pageId, token, isPng = false).substringBeforeLast('.')}"
        }
    }
}

private fun isSafeId(value: String): Boolean = SAFE_ID.matches(value)

private fun fileName(pageId: String, token: String, isPng: Boolean): String {
    val extension = if (isPng) PNG_EXTENSION else JPEG_EXTENSION
    return "$pageId-$token.$extension"
}

private fun isPng(bytes: ByteArray): Boolean =
    bytes.size >= PNG_SIGNATURE.size && bytes.copyOf(PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)

private fun isJpeg(bytes: ByteArray): Boolean =
    bytes.size >= JPEG_SIGNATURE_LENGTH &&
        (bytes[0].toInt() and JPEG_FIRST_BYTE) == JPEG_FIRST_BYTE &&
        (bytes[1].toInt() and JPEG_FIRST_BYTE) == JPEG_SECOND_BYTE

private const val PNG_EXTENSION = "png"
private const val JPEG_EXTENSION = "jpg"
