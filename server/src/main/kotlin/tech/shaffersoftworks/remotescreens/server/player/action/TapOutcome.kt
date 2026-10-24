package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaCalendar
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaEvent
import tech.shaffersoftworks.remotescreens.shared.VarValue

/** What came of a tap. */
sealed interface TapOutcome {
    data object Done : TapOutcome

    data object Ignored : TapOutcome

    /** Rebuild the main document (Music Assistant browse type / listen mode / load more). */
    data object Rebuild : TapOutcome

    /** Open the card of [entityId] (or of a kind with no entity, such as energy). */
    data class Card(val entityId: String?, val kind: String?) : TapOutcome

    /** The action needs a second tap; show [text] in the variable [varName] until it comes. */
    data class Confirm(val varName: String, val text: String) : TapOutcome

    /** A key of a keypad was pressed; [shown] is the code so far, masked. [extra] lights an alarm page's pin dots. */
    data class Code(val varName: String, val shown: String, val extra: List<VarValue> = emptyList()) : TapOutcome

    data class Failed(val message: String) : TapOutcome

    /** Open the native calendar create or delete dialog. */
    data class OpenCalendar(
        val tileId: String,
        val mode: String,
        val calendars: List<AgendaCalendar> = emptyList(),
        val event: AgendaEvent? = null,
    ) : TapOutcome

    /** Show [message] under the agenda title, then clear it. */
    data class AgendaNotice(val tileId: String, val message: String) : TapOutcome

    /** Show RPI's dialog with [title] and [message] over the alarm page of [tileId] until it is dismissed. */
    data class AlarmNotice(val tileId: String, val title: String, val message: String) : TapOutcome
}
