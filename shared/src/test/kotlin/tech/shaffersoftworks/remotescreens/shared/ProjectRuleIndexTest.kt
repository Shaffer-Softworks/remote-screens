package tech.shaffersoftworks.remotescreens.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fails when a project rule exists and the always-on index does not name it. */
class ProjectRuleIndexTest {
    @Test
    fun everyProjectRuleIsNamedByTheAlwaysOnIndex() {
        val rulesDir = File("../.cursor/rules")
        val index = File(rulesDir, INDEX_FILE)
        val text = index.readText()
        assertTrue("The rule index must always apply", text.contains("alwaysApply: true"))
        val missing = rulesDir.listFiles { file -> file.extension == "mdc" && file.name != INDEX_FILE }
            .orEmpty()
            .map { it.name }
            .filter { name -> !text.contains(name) }
            .sorted()
        assertEquals("Add these rule files to $INDEX_FILE", emptyList<String>(), missing)
    }

    private companion object {
        const val INDEX_FILE = "follow-project-rules.mdc"
    }
}
