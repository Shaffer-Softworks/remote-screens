package tech.shaffersoftworks.remotescreens.server.player

import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.PlayerRecord

/** Whether a Hello may open a session, and whether it matches a stored pairing. */
object PlayerAuth {
    /** True when [token] is the secret whose hash is [tokenHash]. */
    fun isTrusted(token: String?, tokenHash: String?): Boolean =
        tokenHash != null && token != null && PlayerTokens.hash(token) == tokenHash

    /**
     * A new or unpaired device may connect. A device that already has a token must present that token;
     * a wrong or missing one must not replace its session or pairing.
     */
    fun mayConnect(hello: PlayerMessage.Hello, record: PlayerRecord?): Boolean {
        val tokenHash = record?.tokenHash ?: return true
        return isTrusted(hello.token, tokenHash)
    }
}
