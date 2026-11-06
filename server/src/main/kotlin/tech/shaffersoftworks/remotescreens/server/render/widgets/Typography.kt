@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.widgets

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFontType
import androidx.compose.remote.creation.dsl.RcFontWeight
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcText
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.requiredWidthIn
import androidx.compose.remote.creation.dsl.semantics
import androidx.compose.remote.creation.dsl.size
import tech.shaffersoftworks.remotescreens.server.render.DocContext

private const val DIGIT_ADVANCE = 0.62f
private const val NUMBER_LINE_HEIGHT = 1.35f
private const val COMPACT_SCALE = 0.8f
private const val MINIMUM_SCALE = 0.6f
private const val LABEL_ADVANCE = 0.55f
private const val READING_GLYPHS = 6

/** The JVM Text API has no font-feature setting; monospace provides equal-width digits on every player. */
fun DocContext.numericLabel(
    text: RcText,
    sp: Float,
    color: RcColor,
    weight: Float = Weight.NORMAL,
    glyphs: Int = READING_GLYPHS,
) {
    val doc = this
    scope.FitBox(horizontal = RcHorizontalPositioning.Center, vertical = RcVerticalPositioning.Center) {
        for (scale in listOf(1f, COMPACT_SCALE, MINIMUM_SCALE)) {
            val fontPx = doc.units.sp(sp * scale)
            val width = fontPx * DIGIT_ADVANCE * glyphs
            val height = fontPx * NUMBER_LINE_HEIGHT
            Canvas(Modifier.size(width, height).requiredWidthIn(min = width).semantics { text(text) }) {
                save {
                    paint {
                        clearShader()
                        color(color)
                        alpha(1f)
                        antiAlias(true)
                        style(RcPaintStyle.Fill)
                        textSize(fontPx)
                        typeface(RcFontType.Monospace, RcFontWeight(weight))
                    }
                    drawTextAnchored(text, width / 2f, height / 2f, 0f, 0f)
                }
            }
        }
    }
}

/** Prefer full-size text, then a smaller label before ellipsizing. Width is a conservative hint. */
fun DocContext.fittingLabel(text: RcText, sample: String, sp: Float, color: RcColor) {
    val doc = this
    scope.FitBox {
        doc.label(
            text = text,
            sp = sp,
            color = color,
            modifier = Modifier.requiredWidthIn(min = doc.units.sp(sp) * sample.length * LABEL_ADVANCE),
        )
        doc.label(text = text, sp = sp * COMPACT_SCALE, color = color)
    }
}
