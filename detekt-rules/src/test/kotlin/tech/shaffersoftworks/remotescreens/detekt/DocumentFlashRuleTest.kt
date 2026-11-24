package tech.shaffersoftworks.remotescreens.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentFlashRuleTest {
    private val rule = DocumentFlashRule(Config.empty)

    @Test
    fun reportsATemperatureBakedIntoTheDocument() {
        val findings = rule.lint(
            """
            fun sample() {
                structural(id) { s -> s?.weather?.temperature.toString() }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("temperature"))
    }

    @Test
    fun allowsAConditionThatChangesTheDrawnIcon() {
        val findings = rule.lint(
            """
            fun sample() {
                structural(id) { s -> s?.state.orEmpty() }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun reportsAForcedPictureResend() {
        val findings = rule.lint(
            """
            fun beginPictureFetch(name: String, isForced: Boolean): Boolean = isForced
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("picture"))
    }

    @Test
    fun allowsAPictureFetchThatKeepsTheBitmapAlreadySent() {
        val findings = rule.lint(
            """
            fun beginPictureFetch(name: String, sourceKey: String?): Boolean = sourceKey != null
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }
}
