package dev.remotescreens.server.player.action

/** What came of a tap. */
sealed interface TapOutcome {
    data object Done : TapOutcome

    data object Ignored : TapOutcome

    /** Open the card of [entityId] (or of a kind with no entity, such as energy). */
    data class Card(val entityId: String?, val kind: String?) : TapOutcome

    /** The action needs a second tap; show [text] in the variable [varName] until it comes. */
    data class Confirm(val varName: String, val text: String) : TapOutcome

    /** A key of a keypad was pressed; [shown] is the code so far, masked. */
    data class Code(val varName: String, val shown: String) : TapOutcome

    data class Failed(val message: String) : TapOutcome
}
