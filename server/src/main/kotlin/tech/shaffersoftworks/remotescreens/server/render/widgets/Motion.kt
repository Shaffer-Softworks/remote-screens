@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.widgets

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcAnimationCurve
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.size
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.shared.VarValue
import kotlin.math.PI

private const val STATE_DURATION_SECONDS = 0.25f
private const val FAN_DEGREES_PER_SECOND = 120f
private const val PULSE_MIN_ALPHA = 0.65f
private const val PULSE_AMPLITUDE = 0.175f

data class IconMotion(val angle: RcFloat? = null, val opacity: RcFloat? = null)

/** Short, bounded interpolation for live readings; action dispatch remains immediate. */
fun RcFloat.smooth(): RcFloat = anim(duration = STATE_DURATION_SECONDS, curve = RcAnimationCurve.CubicStandard)

/** Motion follows live state, including changes that do not rebuild the document. */
fun DocContext.statusIcon(entityId: String, name: String?, sizeDp: Float, color: RcColor) {
    val domain = entityId.substringBefore('.')
    val moving = Vars.entity(entityId, "icon_moving")
    val resting = Vars.entity(entityId, "icon_resting")
    bind(entityId) { states ->
        val entity = states[entityId]
        val active = when (domain) {
            "fan" -> entity?.state == "on"
            "climate" -> entity?.state !in setOf(null, "off", "unknown", "unavailable") &&
                entity?.attr("hvac_action") in setOf("heating", "preheating", "cooling")
            "alarm_control_panel" -> entity?.state in setOf("arming", "pending", "triggered")
            else -> false
        }
        listOf(
            VarValue.int(moving, if (active) Vars.VISIBLE else Vars.GONE),
            VarValue.int(resting, if (active) Vars.GONE else Vars.VISIBLE),
        )
    }
    scope.Box(Modifier.size(units.px(sizeDp), units.px(sizeDp))) {
        val doc = this@statusIcon
        doc.icon(name, sizeDp, color, doc.int(resting, Vars.VISIBLE))
        // Three fan blades repeat every 120 degrees, so the minute-clock wrap is seamless.
        val angle = if (domain == "fan") continuousSeconds() * FAN_DEGREES_PER_SECOND else null
        val pulse = if (domain != "fan") {
            PULSE_MIN_ALPHA.rf + (sin(continuousSeconds() * PI.toFloat()) + 1f) * PULSE_AMPLITUDE
        } else {
            null
        }
        doc.icon(name, sizeDp, color, doc.int(moving), motion = IconMotion(angle, pulse))
    }
}

