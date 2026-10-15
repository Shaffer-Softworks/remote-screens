package tech.shaffersoftworks.remotescreens.server.player

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val TOKEN_BYTES = 32
private const val PAIRING_CODE_MIN = 100_000
private const val PAIRING_CODE_RANGE = 900_000

/** The secrets of pairing: the six-digit code a new player shows, and the token an adopted one keeps. */
class PlayerTokens(private val random: SecureRandom = SecureRandom()) {

    /** A six-digit code that none of [taken] uses. */
    fun newPairingCode(taken: Set<String>): String {
        while (true) {
            val code = (PAIRING_CODE_MIN + random.nextInt(PAIRING_CODE_RANGE)).toString()
            if (code !in taken) return code
        }
    }

    fun newToken(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))

    companion object {
        /** Only the hash of a token is stored, so a copy of the layout does not let anyone pose as a player. */
        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
