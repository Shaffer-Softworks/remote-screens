package tech.shaffersoftworks.remotescreens.server.player.action

/** A tap on the intercom page. The add-on sends it back to that tablet; it is not a Home Assistant call. */
object IntercomActions {
    fun handle(verb: String, clientId: String): TapOutcome = when (verb) {
        "intercom_call" -> if (clientId.isBlank()) TapOutcome.Ignored else TapOutcome.Intercom("call", clientId)
        "intercom_hangup" -> TapOutcome.Intercom("hangup", "")
        "intercom_mute" -> TapOutcome.Intercom("mute", "")
        else -> TapOutcome.Ignored
    }
}
