package tech.shaffersoftworks.remotescreens.player.utils

import java.util.Base64

/** Decodes base64 from the add-on, tolerating line breaks and missing padding. Null when it is not base64. */
fun decodeBase64OrNull(base64Data: String): ByteArray? =
    runCatching { Base64.getMimeDecoder().decode(base64Data) }.getOrNull()
