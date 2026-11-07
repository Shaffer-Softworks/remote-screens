package tech.shaffersoftworks.remotescreens.shared

import java.net.URI
import kotlinx.serialization.Serializable

private const val MAX_ENVIRONMENT_LENGTH = 64
private const val MAX_DSN_LENGTH = 2048

/** Opt-in crash reporting for paired tablets. The DSN is a public ingestion key, never an auth token. */
@Serializable
data class SentrySettings(
    val enabled: Boolean = false,
    val dsn: String = "",
    val environment: String = "production",
    val anrEnabled: Boolean = true,
) {
    fun validated(): SentrySettings {
        val clean = copy(dsn = dsn.trim(), environment = environment.trim().ifEmpty { "production" })
        require(
            clean.environment.length <= MAX_ENVIRONMENT_LENGTH &&
                clean.environment.none { it.isWhitespace() || it == '/' },
        ) {
            "Sentry environment must be at most 64 characters without spaces or slashes"
        }
        require(clean.dsn.length <= MAX_DSN_LENGTH) { "Sentry DSN is too long" }
        require(!clean.enabled || clean.dsn.isNotEmpty()) { "Sentry needs a DSN when enabled" }
        if (clean.dsn.isNotEmpty()) {
            val uri = runCatching { URI(clean.dsn) }.getOrNull()
            require(uri != null) { "Enter a valid Sentry DSN" }
            require(
                uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() &&
                    !uri.userInfo.isNullOrBlank() && !uri.userInfo.contains(':') &&
                    uri.path.orEmpty().substringAfterLast('/').matches(Regex("[0-9]+")) &&
                    uri.query == null && uri.fragment == null,
            ) { "Enter a public Sentry DSN such as https://key@host/123" }
        }
        return clean
    }
}
