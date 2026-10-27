package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionPromptsTest {
    @Test
    fun `a prompt that arrives while one is open waits until that dialog closes`() {
        val shown = mutableListOf<PermissionPrompt>()
        val prompts = PermissionPrompts().apply { show = { prompt -> shown.add(prompt) } }

        prompts.request(PermissionPrompt.Camera)
        prompts.request(PermissionPrompt.Microphone)

        assertEquals(listOf(PermissionPrompt.Camera), shown)
        prompts.finished()
        assertEquals(listOf(PermissionPrompt.Camera, PermissionPrompt.Microphone), shown)
    }

    @Test
    fun `the same prompt is not queued twice`() {
        val shown = mutableListOf<PermissionPrompt>()
        val prompts = PermissionPrompts().apply { show = { prompt -> shown.add(prompt) } }

        prompts.request(PermissionPrompt.Camera)
        prompts.request(PermissionPrompt.Microphone)
        prompts.request(PermissionPrompt.Microphone)
        prompts.finished()

        assertEquals(listOf(PermissionPrompt.Camera, PermissionPrompt.Microphone), shown)
    }
}
