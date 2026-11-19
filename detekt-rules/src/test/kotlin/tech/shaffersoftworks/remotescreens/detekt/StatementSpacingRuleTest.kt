package tech.shaffersoftworks.remotescreens.detekt

import dev.detekt.api.Config
import dev.detekt.api.modifiedText
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import dev.detekt.test.utils.compileContentForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl

class StatementSpacingRuleTest {
    private val rule: StatementSpacingRule = StatementSpacingRule(Config.empty)

    @Test
    fun reportsStackedMultilineStatementsControlFlowAndReturn() {
        val findings = rule.lint(
            """
            fun sample(): Int {
                val first = listOf(
                    1,
                    2,
                )
                val second = listOf(
                    3,
                    4,
                )
                foo(
                    1,
                    2,
                )
                if (first.isNotEmpty()) {
                    println(first)
                }
                for (item in second) {
                    println(item)
                }
                return first.size
            }
            """.trimIndent(),
        )

        assertEquals(STACKED_FINDING_COUNT, findings.size)
    }

    @Test
    fun allowsSpacedBlocksWithLeadingComments() {
        val findings = rule.lint(
            """
            fun sample(): Int {
                val first = listOf(
                    1,
                    2,
                )

                val second = listOf(
                    3,
                    4,
                )

                foo(
                    1,
                    2,
                )

                // Guard the values before branching.
                if (first.isNotEmpty()) {
                    println(first)
                }

                // Walk every collected item.
                for (item in second) {
                    println(item)
                }

                return first.size
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun allowsGroupedSingleLineDeclarationsAndCalls() {
        val findings = rule.lint(
            """
            fun sample(flag: Boolean) {
                if (flag) {
                    val first = 1
                    val second = 2
                    println(first)
                    println(second)
                    var total = first
                    total = second
                    println(total)
                }
            }
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun reportsWhenTheBlankLineIsMissingAboveALeadingComment() {
        val findings = rule.lint(
            """
            fun sample() {
                val name = "room"
                // Ready to branch.
                if (name.isNotEmpty()) {
                    println(name)
                }
            }
            """.trimIndent(),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun insertsTheMissingBlankLineWhenAutoCorrectIsOn() {
        val source = """
            fun sample(): Int {
                val first = listOf(
                    1,
                    2,
                )
                return first.size
            }
        """.trimIndent()

        val file = compileContentForTest(source)
        val correcting = StatementSpacingRule(TestConfig("autoCorrect" to true))
        correcting.visitFile(file, LanguageVersionSettingsImpl.DEFAULT)
        val corrected = file.modifiedText ?: error("auto-correct did not change the file")
        val findings = StatementSpacingRule(Config.empty).lint(corrected)
        assertEquals(0, findings.size)
    }

    private companion object {
        const val STACKED_FINDING_COUNT = 5
    }
}
