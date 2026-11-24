package tech.shaffersoftworks.remotescreens.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * A live reading or an already-sent picture must not rebuild the screen.
 * `structural` rebuilds the one document that holds every page. Resending a picture the tablet
 * already has decodes it on the visible page. See session-document-rebuild-context.mdc.
 */
class DocumentFlashRule(config: Config) : Rule(
    config,
    "Live readings stay variables, and a new document does not resend a picture the player already has.",
) {
    override val ruleName: RuleName = RuleName("DocumentFlash")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.calleeExpression?.text != STRUCTURAL) return

        val body = expression.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression?.text ?: return
        val reading = LIVE_READINGS.firstOrNull { name -> isWholeWord(name, body) } ?: return

        report(
            Finding(
                Entity.from(expression),
                "$reading is a live reading. Send it with bind(), not structural(). " +
                    "structural() rebuilds every page and flashes the screen.",
            ),
        )
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        if (function.name !in PICTURE_SENDS) return

        val forced = function.valueParameters.firstOrNull { parameter -> parameter.name == FORCED } ?: return

        report(Finding(Entity.from(forced), FORCED_MESSAGE))
    }

    private companion object {
        const val STRUCTURAL = "structural"
        const val FORCED = "isForced"

        val PICTURE_SENDS = setOf("beginPictureFetch", "sendPictures")

        val LIVE_READINGS = listOf(
            "temperature",
            "temperatureUnit",
            "currentTemperature",
            "humidity",
            "pressure",
            "windSpeed",
            "numericState",
            "updatedLabel",
            "lastChanged",
        )

        const val FORCED_MESSAGE =
            "Do not resend a picture because the document was replaced. " +
                "The player already has that bitmap; fetching it again flashes the screen."

        fun isWholeWord(name: String, text: String): Boolean =
            Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(text)
    }
}
