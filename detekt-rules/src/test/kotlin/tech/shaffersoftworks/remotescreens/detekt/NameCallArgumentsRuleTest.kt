package tech.shaffersoftworks.remotescreens.detekt

import dev.detekt.api.modifiedText
import dev.detekt.test.TestConfig
import dev.detekt.test.utils.KotlinAnalysisApiEngine
import dev.detekt.test.utils.createEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl

class NameCallArgumentsRuleTest {
    @Test
    fun namesEveryArgumentOnAThreeArgumentCall() {
        val source = """
            fun card(group: String, label: String, entity: String) = group + label + entity

            fun sample() {
                card("Cards", "light", "sensor.power")
            }
        """.trimIndent()
        val environment = createEnvironment()

        KotlinAnalysisApiEngine().use { engine ->
            val file = engine.compile(
                code = source,
                jvmClasspathRoots = environment.jvmClasspathRoots,
                languageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
            )
            val rule = NameCallArgumentsRule(TestConfig("autoCorrect" to true))
            rule.visitFile(file, LanguageVersionSettingsImpl.DEFAULT)
            assertEquals(
                """
                fun card(group: String, label: String, entity: String) = group + label + entity

                fun sample() {
                    card(group = "Cards", label = "light", entity = "sensor.power")
                }
                """.trimIndent(),
                file.modifiedText ?: source,
            )
        }
    }

    @Test
    fun leavesRepeatedVarargArgumentsPositional() {
        val source = """
            fun sample() {
                listOf("armed_away", "armed_home", "armed_night")
            }
        """.trimIndent()
        val environment = createEnvironment()

        KotlinAnalysisApiEngine().use { engine ->
            val file = engine.compile(
                code = source,
                jvmClasspathRoots = environment.jvmClasspathRoots,
                languageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
            )
            val rule = NameCallArgumentsRule(TestConfig("autoCorrect" to true))
            rule.visitFile(file, LanguageVersionSettingsImpl.DEFAULT)
            kotlin.test.assertNull(file.modifiedText)
        }
    }

    @Test
    fun leavesACallThatPassesAVarargUnnamed() {
        val source = """
            fun format(locale: Any, pattern: String, vararg args: Any?): String = pattern

            fun sample() {
                format(locale = "ROOT", pattern = "%.2f", 1.0)
            }
        """.trimIndent()
        val environment = createEnvironment()

        KotlinAnalysisApiEngine().use { engine ->
            val file = engine.compile(
                code = source,
                jvmClasspathRoots = environment.jvmClasspathRoots,
                languageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
            )
            val rule = NameCallArgumentsRule(TestConfig("autoCorrect" to true))
            rule.visitFile(file, LanguageVersionSettingsImpl.DEFAULT)
            kotlin.test.assertEquals(source, file.modifiedText ?: source)
        }
    }
}
