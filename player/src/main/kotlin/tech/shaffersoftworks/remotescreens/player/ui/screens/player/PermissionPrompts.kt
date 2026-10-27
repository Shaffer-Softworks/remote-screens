package tech.shaffersoftworks.remotescreens.player.ui.screens.player

/**
 * Shows one system permission dialog at a time.
 * A second [androidx.activity.result.ActivityResultLauncher.launch] while the first dialog is open
 * cancels that dialog, so the microphone request never finishes when the camera prompt starts with it.
 */
internal enum class PermissionPrompt {
    Camera,
    Microphone,
    Location,
}

internal class PermissionPrompts {
    private val waiting = ArrayDeque<PermissionPrompt>()
    private var current: PermissionPrompt? = null

    /** Shows [prompt], or remembers it until the dialog already on screen closes. */
    var show: (PermissionPrompt) -> Unit = {}

    fun request(prompt: PermissionPrompt) {
        if (prompt == current || prompt in waiting) return
        if (current != null) {
            waiting.add(prompt)
            return
        }
        current = prompt
        show(prompt)
    }

    /** The dialog closed. Show the next one that arrived while it was up. */
    fun finished() {
        val next = waiting.removeFirstOrNull()
        current = next
        if (next != null) show(next)
    }
}
