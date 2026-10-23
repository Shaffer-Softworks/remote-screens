package tech.shaffersoftworks.remotescreens.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fails when a project rule exists and the always-on index does not name it. */
class ProjectRuleIndexTest {
    @Test
    fun everyProjectRuleIsNamedByTheAlwaysOnIndex() {
        val rulesDir = File("../.cursor/rules")
        val index = File(rulesDir, INDEX_FILE)
        val text = index.readText()
        assertTrue(text.contains("alwaysApply: true"), "The rule index must always apply")
        val missing = rulesDir.listFiles { file -> file.extension == "mdc" && file.name != INDEX_FILE }
            .orEmpty()
            .map { it.name }
            .filter { name -> !text.contains(name) }
            .sorted()
        assertEquals(emptyList(), missing, "Add these rule files to $INDEX_FILE")
    }

    private companion object {
        const val INDEX_FILE = "follow-project-rules.mdc"
    }
}
