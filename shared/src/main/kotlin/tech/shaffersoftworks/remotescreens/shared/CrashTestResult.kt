package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.Serializable

/** accepted means the ingestion endpoint acknowledged the test; indexing in Sentry may take longer. */
@Serializable
data class CrashTestResult(val status: String, val eventId: String? = null)
