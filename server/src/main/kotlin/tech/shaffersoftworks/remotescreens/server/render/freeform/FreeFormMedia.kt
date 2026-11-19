@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.render.cards.light.HUES
import tech.shaffersoftworks.remotescreens.server.render.cards.light.KELVINS

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.widgets.MarkdownLines
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.cards.light.kelvinColor
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.key
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.server.render.widgets.toggleIcon

import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcCanvasScope
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcInteger
import androidx.compose.remote.creation.dsl.RcStrokeCap
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcText
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.horizontalWeight
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.AlarmVerb
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain

private data class GaugePaint(
    val track: RcColor,
    val fill: RcColor,
    val stroke: Float,
    val inset: Float,
    val px: Float,
)

private fun RcCanvasScope.clockHand(
    angle: RcFloat,
    length: Float,
    width: Float,
    colour: RcColor,
    mid: Float,
) {
    save {
        rotate(angle = angle, centerX = mid.rf, centerY = mid.rf)

        paint {
            color(colour)
            style(RcPaintStyle.Stroke)
            strokeWidth(width)
            strokeCap(RcStrokeCap.Round)
        }

        drawLine(x1 = mid, y1 = mid, x2 = mid, y2 = mid - mid * length)
    }
}

private data class AnalogFace(
    val doc: DocContext,
    val node: Node,
    val ink: RcColor,
    val dim: RcColor,
    val px: Float,
    val mid: Float,
)

private fun RcCanvasScope.analogNumbers(face: AnalogFace) {
    val doc = face.doc
    val node = face.node
    val ink = face.ink
    val dim = face.dim
    val px = face.px
    val mid = face.mid

    paint {
        color(dim)
        style(RcPaintStyle.Stroke)
        strokeWidth(px * Metrics.ANALOG_TICK_STROKE_RATIO)
        strokeCap(RcStrokeCap.Round)
    }

    for (i in 0 until Metrics.ANALOG_HOUR_MARKS) {
        val angle = i * 2 * PI / Metrics.ANALOG_HOUR_MARKS

        val inner = if (i % Metrics.ANALOG_QUARTER_STEP == 0) {
            Metrics.ANALOG_TICK_INNER_MAJOR
        } else {
            Metrics.ANALOG_TICK_INNER_MINOR
        }

        drawLine(
            x1 = mid + (sin(angle) * mid * inner).toFloat(),
            y1 = mid - (cos(angle) * mid * inner).toFloat(),
            x2 = mid + (sin(angle) * mid * Metrics.ANALOG_TICK_OUTER).toFloat(),
            y2 = mid - (cos(angle) * mid * Metrics.ANALOG_TICK_OUTER).toFloat(),
        )
    }

    val hours = when (node.props["numbers"]) {
        "all" -> 1..Metrics.ANALOG_HOUR_MARKS
        "quarters" -> listOf(
            Metrics.ANALOG_QUARTER_3,
            Metrics.ANALOG_QUARTER_6,
            Metrics.ANALOG_QUARTER_9,
            Metrics.ANALOG_QUARTER_12,
        )
        else -> emptyList()
    }

    if (hours.none()) return

    val size = px * Metrics.ANALOG_NUMBER_SIZE_RATIO
    paint { color(ink); style(RcPaintStyle.Fill); textSize(size) }

    for (hour in hours) {
        val angle = hour * 2 * PI / Metrics.ANALOG_HOUR_MARKS

        drawTextAnchored(
            text = doc.literal(hour.toString()),
            x = mid + (sin(angle) * mid * Metrics.ANALOG_NUMBER_RADIUS).toFloat(),
            y = mid - (cos(angle) * mid * Metrics.ANALOG_NUMBER_RADIUS).toFloat(),
            panX = 0f,
            panY = 0f,
        )
    }
}

private fun RcCanvasScope.gaugeFace(paint: GaugePaint, sweep: RcFloat?, caption: RcText?, captionInk: RcColor) {
    val stroke = paint.stroke
    val inset = paint.inset
    val px = paint.px

    this.paint {
        color(paint.track)
        style(RcPaintStyle.Stroke)
        strokeWidth(stroke)
        strokeCap(RcStrokeCap.Round)
    }

    drawArc(
        left = inset,
        top = inset,
        right = px - inset,
        bottom = px - inset,
        startAngle = Metrics.GAUGE_ARC_START_DEG,
        sweepAngle = Metrics.GAUGE_ARC_SWEEP_DEG,
    )

    if (sweep != null) {
        this.paint {
            color(paint.fill)
            style(RcPaintStyle.Stroke)
            strokeWidth(stroke)
            strokeCap(RcStrokeCap.Round)
        }

        drawArc(
            left = inset.rf,
            top = inset.rf,
            right = (px - inset).rf,
            bottom = (px - inset).rf,
            startAngle = Metrics.GAUGE_ARC_START_DEG.rf,
            sweepAngle = sweep,
        )
    }

    if (caption != null) {
        this.paint {
            color(captionInk)
            style(RcPaintStyle.Fill)
            textSize(px * Metrics.GAUGE_CAPTION_SIZE_RATIO)
        }

        drawTextAnchored(
            text = caption,
            x = px / 2f,
            y = px - px * Metrics.GAUGE_CAPTION_Y_RATIO,
            panX = 0f,
            panY = 0f,
        )
    }
}

/** A ring three quarters round that fills with the entity's level, its value in the middle. */
internal fun DocContext.gaugeNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId

    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.GAUGE_MIN_DP,
        Metrics.GAUGE_MAX_DP,
    ) ?: Metrics.GAUGE_DEFAULT_DP

    val px = units.px(sizeDp)
    val stroke = px * Metrics.GAUGE_STROKE_RATIO
    val inset = stroke / 2f

    val fill = node.props["color"]?.let { paint(
        it,
    ) } ?: fixed(Pastel.forDomain(id?.let { EntityId(it).domain.value }).argb)

    val track = role(Role.TRACK)

    if (id != null) {
        bindEntity(id)
        bindLevel(node, id)
    }

    // In the gap under the ring: the entity's name for `@name`, else the words given.
    val caption = node.props["caption"]?.takeIf { it.isNotBlank() }?.let {
        if (it == "@name") {
            id?.let { entityId -> text(
                Vars.entity(entityId, Vars.KEY_NAME),
            ) }
        } else {
            literal(it)
        }
    }

    rcScope.Box(
        modifier = styled(node = node, axis = axis, pages = pages),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@gaugeNode

        Box(
            modifier = Modifier.size(px, px),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            Canvas(Modifier.size(px, px)) {
                gaugeFace(
                    paint = GaugePaint(track = track, fill = fill, stroke = stroke, inset = inset, px = px),
                    sweep = id?.let { doc.float(nodeLevel(node.id)) * Metrics.GAUGE_ARC_SWEEP_DEG },
                    caption = caption,
                    captionInk = doc.role(Role.INK_SOFT),
                )
            }

            if (id != null) {
                doc.label(
                    text = doc.text(Vars.entity(id, Vars.KEY_VALUE)),
                    sp = maxOf(sizeDp * Metrics.GAUGE_VALUE_SIZE_RATIO, Type.CAPTION),
                    color = doc.role(Role.INK),
                    weight = Weight.MEDIUM,
                    align = RcTextAlign.Center,
                )
            }
        }
    }
}

/** A clock face whose hands the player turns by its own clock, so it keeps time without the add-on. */
internal fun DocContext.analogNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.ANALOG_MIN_DP,
        Metrics.ANALOG_MAX_DP,
    ) ?: Metrics.ANALOG_DEFAULT_DP

    val px = units.px(sizeDp)
    val mid = px / 2f
    val face = node.props["color"]?.let { paint(it) } ?: role(Role.CARD)
    val ink = role(Role.INK)
    val dim = role(Role.INK_DIM)
    val accent = role(Role.ACCENT)
    val seconds = node.props["seconds"] == Tile.OPTION_TRUE

    rcScope.Box(
        modifier = styled(node = node, axis = axis, pages = pages),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@analogNode

        Canvas(Modifier.size(px, px)) {
            paint { color(face); style(RcPaintStyle.Fill) }
            drawCircle(centerX = mid, centerY = mid, radius = mid)

            analogNumbers(
                AnalogFace(doc = doc, node = node, ink = ink, dim = dim, px = px, mid = mid),
            )

            // minutes() is the minute of the day and seconds() the second of the hour.
            clockHand(
                angle = minutes() * Metrics.ANALOG_HOUR_DEG_PER_MIN,
                length = Metrics.ANALOG_HOUR_HAND_LEN,
                width = px * Metrics.ANALOG_HOUR_HAND_WIDTH,
                colour = ink,
                mid = mid,
            )

            clockHand(
                angle = seconds() * Metrics.ANALOG_MINUTE_DEG_PER_SEC,
                length = Metrics.ANALOG_MINUTE_HAND_LEN,
                width = px * Metrics.ANALOG_MINUTE_HAND_WIDTH,
                colour = ink,
                mid = mid,
            )

            if (seconds) {
                clockHand(
                    angle = continuousSeconds() * Metrics.ANALOG_SECOND_DEG,
                    length = Metrics.ANALOG_SECOND_HAND_LEN,
                    width = px * Metrics.ANALOG_SECOND_HAND_WIDTH,
                    colour = accent,
                    mid = mid,
                )
            }

            paint { color(ink); style(RcPaintStyle.Fill) }
            drawCircle(centerX = mid, centerY = mid, radius = px * Metrics.ANALOG_CENTER_DOT_RATIO)
        }
    }
}

private fun DocContext.iconButtonColours(node: Node, id: String?, domain: Domain?): Pair<RcColor, RcColor> {
    if (id == null) return (node.props["color"]?.let { paint(it) } ?: role(Role.KEY)) to role(Role.INK)

    bindEntity(id)

    bindTileColors(
        tileId = node.id,
        entityId = id,
        accent = accent(node.props["color"], Pastel.forDomain(domain?.value))
    )

    return color(Vars.tile(node.id, Vars.KEY_BG)) to color(Vars.tile(node.id, Vars.KEY_BG_INK))
}

/** A round key with an icon that lights up in the entity's colour while it is on. */
internal fun DocContext.iconButtonNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.ICON_BUTTON_MIN_DP,
        Metrics.ICON_BUTTON_MAX_DP,
    ) ?: Metrics.ICON_BUTTON_DEFAULT_DP

    val id = node.bind?.entityId
    val domain = id?.let { EntityId(it).domain }

    val (bg, ink) = iconButtonColours(node = node, id = id, domain = domain)

    val name = node.props["icon"]?.takeIf { it.isNotEmpty() }
        ?: id?.let { states[it]?.common?.icon }
        ?: domain?.let { Catalogue.of(it.value)?.icon }
        ?: "gesture-tap"

    var base = sized(node, axis)

    if (node.size.width == "wrap" && node.size.height == "wrap") {
        base = base.size(
            units.px(sizeDp),
            units.px(sizeDp),
        )
    }

    base = base.clip(rounded(if (node.style.corner > 0f) node.style.corner else sizeDp / 2f))

    if (node.style.background == null) base = base.background(bg)

    rcScope.Box(
        modifier = styled(node = node, axis = axis, pages = pages, base = base),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@iconButtonNode
        val on = node.props["iconOn"]

        if (id != null && on != null) {
            doc.toggleIcon(
                entityId = id,
                off = name,
                on = on,
                sizeDp = sizeDp * Metrics.ICON_BUTTON_ICON_RATIO,
                color = ink,
            )
        } else {
            doc.icon(name = name, sizeDp = sizeDp * Metrics.ICON_BUTTON_ICON_RATIO, color = ink)
        }
    }
}

/** The keys an entity's remote has: a player's transport, a cover's open, stop and close, a vacuum's, a lock's. */
internal fun DocContext.keysOf(entityState: EntityState?, domain: Domain?): List<Pair<String, String>> = when (domain) {
    Domain.MEDIA_PLAYER -> listOf(
        "previous" to "skip-previous",
        "play_pause" to "play-pause",
        "next" to "skip-next",
    )
    Domain.COVER -> listOf("open" to "arrow-up", "stop" to "stop", "close" to "arrow-down")
    Domain.VACUUM -> listOf(
        "start" to "play",
        "pause" to "pause",
        "dock" to "home-import-outline",
        "locate" to "map-marker-outline",
    )
    Domain.LOCK -> listOf("lock" to "lock", "unlock" to "lock-open-variant")
    Domain.ALARM_PANEL -> (entityState?.features() ?: 0).let { features ->
        listOfNotNull(
            (AlarmVerb.ARM_HOME to "shield-home").takeIf { features and Metrics.ALARM_FEAT_ARM_HOME != 0 },
            (AlarmVerb.ARM_AWAY to "shield-lock").takeIf { features and Metrics.ALARM_FEAT_ARM_AWAY != 0 },
            (AlarmVerb.ARM_NIGHT to "shield-moon").takeIf { features and Metrics.ALARM_FEAT_ARM_NIGHT != 0 },
        ) + (AlarmVerb.DISARM to "shield-off")
    }
    Domain.LIGHT -> listOf("off" to "lightbulb-off-outline", "on" to "lightbulb-on")
    else -> emptyList()
}

/** A row of the entity's remote keys; a lock asks for a second tap before it opens, as on its tile. */
internal fun DocContext.keysNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val domain = id?.let { EntityId(it).domain }
    val keys = id?.let { keysOf(states[it], domain) }.orEmpty()

    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.KEYS_MIN_DP,
        Metrics.KEYS_MAX_DP,
    ) ?: Metrics.KEYS_DEFAULT_DP

    val modifier = styled(node = node, axis = axis, pages = pages)

    if (id == null || keys.isEmpty()) {
        rcScope.Box(
            modifier.size(
                units.px(sizeDp * Metrics.KEYS_COLS + Metrics.KEYS_GAP_TOTAL_DP),
                units.px(sizeDp),
            ).clip(rounded(Metrics.KEYS_EMPTY_CORNER_DP)).background(role(Role.TRACK)),
        )

        return
    }

    bindEntity(id)
    val spec = Catalogue.of(EntityId(id).domain.value)
    val stretch = node.size.width != "wrap"

    rcScope.Column(modifier, horizontal = RcHorizontalPositioning.Center) {
        val doc = this@keysNode

        Row(
            modifier = if (stretch) Modifier.fillMaxWidth() else Modifier,
            horizontal = RcRowHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            keys.forEachIndexed { i, (verb, icon) ->
                if (i > 0) Spacer(Modifier.width(doc.units.px(Metrics.KEYS_GAP_DP)))

                val ref = ActionRef(node.id, verb)

                if (spec?.predict?.containsKey(verb) == true) doc.predict(ref, id)

                doc.key(
                    ref = ref,
                    icon = icon,
                    heightDp = sizeDp,
                    widthDp = if (stretch) null else sizeDp,
                    round = !stretch,
                    modifier = if (stretch) Modifier.horizontalWeight(1f) else Modifier,
                )
            }
        }

        if (domain == Domain.LOCK) {
            doc.label(
                text = doc.text(Vars.tile(node.id, Vars.KEY_CONFIRM)),
                sp = Type.CAPTION,
                color = doc.role(Role.WARN),
                weight = Weight.SEMI_BOLD,
            )
        }
    }
}

/** Round swatches that set a light's colour, or its white from warm to cool. */
internal fun DocContext.swatchesNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.SWATCH_MIN_DP,
        Metrics.SWATCH_MAX_DP,
    ) ?: Metrics.SWATCH_DEFAULT_DP

    val white = node.props["show"] == "white"

    val colours = if (white) {
        KELVINS.map { kelvinColor(
            it,
        ) to ActionRef(tile = node.id, verb = "kelvin", arg = it.toString()) }
    } else {
        HUES.map { hue -> java.awt.Color.HSBtoRGB(
            hue / Metrics.HUE_DEGREES,
            Metrics.HUE_SATURATION,
            1f,
        ) to ActionRef(tile = node.id, verb = "hue", arg = hue.toString()) }
    }

    val stretch = node.size.width != "wrap"

    rcScope.Row(
        modifier = styled(node = node, axis = axis, pages = pages),
        horizontal = if (stretch) RcRowHorizontalPositioning.SpaceBetween else RcRowHorizontalPositioning.Start,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@swatchesNode

        colours.forEachIndexed { i, (argb, ref) ->
            if (i > 0 && !stretch) Spacer(Modifier.width(doc.units.px(Metrics.SWATCH_GAP_DP)))

            Box(
                doc.tap(
                    Modifier.size(
                        doc.units.px(sizeDp),
                        doc.units.px(sizeDp),
                    ).clip(
                        doc.rounded(sizeDp / 2f),
                    ).background(doc.fixed(argb or Metrics.OPAQUE_ARGB)),
                    if (node.bind != null) ref else null,
                ),
            )
        }
    }
}

/** The digits of an alarm's or a lock's code, kept by the add-on and sent with the next arm or disarm. */
internal fun DocContext.keypadNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId

    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.KEYPAD_MIN_DP,
        Metrics.KEYPAD_MAX_DP,
    ) ?: Metrics.KEYPAD_DEFAULT_DP

    val keyW = sizeDp * Metrics.KEYPAD_KEY_WIDTH_RATIO
    val codeVar = Vars.tile("e:${id ?: node.id}", "code")

    if (id != null) bind(id) { listOf(VarValue.string(codeVar, "")) }

    rcScope.Column(styled(node = node, axis = axis, pages = pages), horizontal = RcHorizontalPositioning.Center) {
        val doc = this@keypadNode

        Box(
            Modifier.height(doc.units.px(Metrics.KEYPAD_DISPLAY_H)),
            vertical = RcVerticalPositioning.Center,
        ) {
            if (id != null) {
                doc.label(
                    text = doc.text(codeVar),
                    sp = Type.VALUE,
                    color = doc.role(Role.INK),
                    weight = Weight.MEDIUM,
                    align = RcTextAlign.Center,
                )
            }
        }

        for (row in listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("clear", "0", "back"),
        )) {
            Row(
                Modifier.padding(start = 0f, top = 0f, end = 0f, bottom = doc.units.px(Metrics.KEYPAD_ROW_GAP_DP)),
                horizontal = RcRowHorizontalPositioning.Center,
            ) {
                row.forEachIndexed { i, digit ->
                    if (i > 0) Spacer(Modifier.width(doc.units.px(Metrics.KEYPAD_KEY_GAP_DP)))

                    val ref = when (digit) {
                        "clear" -> ActionRef(node.id, "code_clear")
                        "back" -> ActionRef(node.id, "code_back")
                        else -> ActionRef(tile = node.id, verb = "code_digit", arg = digit)
                    }

                    val icon = when (digit) { "clear" -> "close"; "back" -> "backspace-outline"; else -> null }

                    doc.key(
                        ref = ref,
                        text = if (icon == null) digit else null,
                        icon = icon,
                        heightDp = sizeDp,
                        widthDp = keyW,
                    )
                }
            }
        }
    }
}

/** A circular level ring with the bound value in the middle and minus/plus keys under it. */
@Suppress("CyclomaticComplexMethod")
internal fun DocContext.dialNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val domain = id?.let { EntityId(it).domain }

    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.DIAL_MIN_DP,
        Metrics.DIAL_MAX_DP,
    )
        ?: Metrics.DIAL_DEFAULT_DP

    val px = units.px(sizeDp)
    val stroke = px * Metrics.DIAL_STROKE_RATIO
    val inset = stroke / 2f
    val fill = node.props["color"]?.let { paint(it) } ?: fixed(Pastel.forDomain(domain?.value).argb)
    val how = id?.let { stepOf(node = node, entityState = states[it], domain = domain) }

    if (id != null) {
        bindEntity(id)
        bindLevel(node, id)
    }

    val step = how?.second?.let { if (it % 1.0 == 0.0) {
        it.toInt().toString()
    } else {
        "%.1f".format(
            Locale.ROOT,
            it,
        )
    } }

    val down = how?.let { ActionRef(tile = node.id, verb = it.first, arg = "-$step") }
    val up = how?.let { ActionRef(tile = node.id, verb = it.first, arg = "+$step") }
    val isPredictable = how != null && domain?.let { Catalogue.of(it.value) }?.predict?.containsKey(how.first) == true

    if (id != null && isPredictable) {
        down?.let { predict(it, id) }
        up?.let { predict(it, id) }
    }

    rcScope.Column(styled(node = node, axis = axis, pages = pages), horizontal = RcHorizontalPositioning.Center) {
        val doc = this@dialNode

        Box(
            modifier = Modifier.size(px, px),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            Canvas(Modifier.size(px, px)) {
                gaugeFace(
                    paint = GaugePaint(
                        track = doc.role(Role.TRACK),
                        fill = fill,
                        stroke = stroke,
                        inset = inset,
                        px = px,
                    ),
                    sweep = id?.let { doc.float(nodeLevel(node.id)) * Metrics.GAUGE_ARC_SWEEP_DEG },
                    caption = null,
                    captionInk = doc.role(Role.INK),
                )
            }

            doc.dialValue(id, sizeDp)
        }

        doc.dialKeys(down, up)
    }
}

private fun DocContext.dialValue(id: String?, sizeDp: Float) {
    val valueSp = maxOf(sizeDp * Metrics.DIAL_VALUE_SIZE_RATIO, Type.CAPTION)

    if (id != null) {
        label(
            text = text(Vars.entity(id, Vars.KEY_VALUE)),
            sp = valueSp,
            color = role(Role.INK),
            weight = Weight.MEDIUM,
            align = RcTextAlign.Center,
        )
    } else {
        label(
            text = "–",
            sp = valueSp,
            color = role(Role.INK_DIM),
            weight = Weight.MEDIUM,
            align = RcTextAlign.Center,
        )
    }
}

private fun DocContext.dialKeys(down: ActionRef?, up: ActionRef?) {
    spacer(Metrics.KEYS_GAP_DP)

    rcScope.Row(vertical = RcVerticalPositioning.Center) {
        val key = { ref: ActionRef?, icon: String ->
            if (ref != null) {
                this@dialKeys.key(
                    ref = ref,
                    icon = icon,
                    heightDp = Metrics.STEPPER_KEY_DP,
                    widthDp = Metrics.STEPPER_KEY_DP,
                    round = true,
                )
            } else {
                val knob = units.px(Metrics.STEPPER_KEY_DP)

                rcScope.Box(
                    Modifier.size(knob, knob)
                        .clip(rounded(Metrics.STEPPER_KEY_CORNER_DP))
                        .background(role(Role.KEY)),
                )
            }
        }

        key(down, "minus")
        Spacer(Modifier.width(units.px(Metrics.KEYS_GAP_TOTAL_DP)))
        key(up, "plus")
    }
}

/** A timer's remaining time, or static text from props. */
internal fun DocContext.countdownNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId

    if (id != null) bindEntity(id)

    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.DISPLAY
    val weight = node.props["weight"]?.toFloatOrNull() ?: Weight.LIGHT
    val alignSpec = node.props["align"] ?: "center"
    val textAlign = align(alignSpec)

    rcScope.Box(
        modifier = styled(node = node, axis = axis, pages = pages),
        horizontal = horizontal(alignSpec),
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@countdownNode

        when {
            id != null -> doc.label(
                text = doc.text(Vars.entity(id, Vars.KEY_VALUE)),
                sp = sp,
                color = ink,
                weight = weight,
                align = textAlign,
            )
            else -> doc.label(
                text = node.props["text"] ?: "0:00",
                sp = sp,
                color = ink,
                weight = weight,
                align = textAlign,
            )
        }
    }
}

/** A QR code of static text, or of a bound entity's state (`qr:@entity.id`). */
internal fun DocContext.qrNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(Metrics.QR_MIN_DP, Metrics.QR_MAX_DP)
        ?: Metrics.QR_DEFAULT_DP

    val inner = (sizeDp - Metrics.QR_PAD_DP * 2f).coerceAtLeast(Metrics.QR_MIN_DP)
    val px = units.px(inner).toInt()
    val id = node.bind?.entityId

    val text = when {
        id != null -> {
            structural(id) { it?.state.orEmpty() }
            "@$id"
        }
        else -> node.props["text"].orEmpty()
    }

    val slot = "qr.${node.id}"

    picture(
        PictureSlot(
            name = slot,
            source = "qr:$text",
            widthPx = px,
            heightPx = px,
            refreshSeconds = 0,
            crop = false,
        ),
    )

    // Mockup: white rounded plate around the code.
    val plate = styled(node = node, axis = axis, pages = pages)
        .clip(rounded(Metrics.QR_CORNER_DP))
        .background(role(Role.INK))
        .padding(units.px(Metrics.QR_PAD_DP))

    rcScope.Box(
        modifier = plate,
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        Image(
            image = this@qrNode.image(name = slot, width = px, height = px),
            modifier = Modifier.size(units.px(inner), units.px(inner)),
            contentScale = RcContentScale.Fit,
        )
    }
}

/** Constrained markdown: one Text line per source line, with whole-line bold or italic. */
internal fun DocContext.markdownNode(node: Node, axis: Axis, pages: Pages) {
    val lines = MarkdownLines.parse(node.props["text"].orEmpty())
    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.BODY
    val textAlign = align(node.props["align"])

    rcScope.Column(styled(node = node, axis = axis, pages = pages), horizontal = horizontal(node.props["align"])) {
        val doc = this@markdownNode

        for (line in lines) {
            val weight = when {
                line.bold -> Weight.BOLD
                line.italic -> Weight.NORMAL
                else -> Weight.NORMAL
            }

            doc.label(text = line.text.ifEmpty { " " }, sp = sp, color = ink, weight = weight, align = textAlign)
        }
    }
}

/** Tab strip plus a StateLayout of children; the player keeps the selected index in `n.<id>.tab`. */
internal fun DocContext.tabsNode(node: Node, self: Dims, axis: Axis, pages: Pages) {
    val children = node.children.filter { !it.hidden }

    val labels = node.props["labels"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: children.mapIndexed { index, child -> child.props["label"] ?: child.props["text"] ?: "Tab ${index + 1}" }

    val shown = labels.take(minOf(labels.size, children.size).coerceAtLeast(0))
    val tabVar = "n.${node.id}.tab"
    val selected = int(tabVar, 0)
    val gap = node.props["gap"]?.toFloatOrNull()?.coerceAtLeast(0f) ?: Metrics.KEYS_GAP_DP
    val stripH = Metrics.TABS_STRIP_H
    val bodyH = (self.h - node.style.padY - stripH - gap).coerceAtLeast(1f)
    val bodyW = (self.w - node.style.padX).coerceAtLeast(1f)

    rcScope.Column(
        styled(node = node, axis = axis, pages = pages, base = measured(node = node, axis = axis, self = self))
    ) {
        val doc = this@tabsNode
        doc.tabStrip(shown = shown, tabVar = tabVar, selected = selected, stripH = stripH)
        Spacer(Modifier.height(doc.units.px(gap)))

        StateLayout(selected, Modifier.size(doc.units.px(bodyW), doc.units.px(bodyH))) {
            for (child in children) {
                Box(Modifier.fillMaxSize()) {
                    doc.node(node = child, self = Dims(bodyW, bodyH), axis = Axis.BOX, pages = pages)
                }
            }
        }
    }
}

/** Mockup strip: "Note | Light" with a teal underline on the active label. */
internal fun DocContext.tabStrip(
    shown: List<String>,
    tabVar: String,
    selected: RcInteger,
    stripH: Float,
) {
    rcScope.StateLayout(selected, Modifier.fillMaxWidth().height(units.px(stripH))) {
        for (sel in shown.indices) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontal = RcRowHorizontalPositioning.Start,
                vertical = RcVerticalPositioning.Center,
            ) {
                val doc = this@tabStrip

                shown.forEachIndexed { index, label ->
                    if (index > 0) {
                        Spacer(Modifier.width(doc.units.px(Metrics.TABS_PIPE_GAP_DP)))
                        doc.label(text = "|", sp = Type.BODY, color = doc.role(Role.INK_DIM))
                        Spacer(Modifier.width(doc.units.px(Metrics.TABS_PIPE_GAP_DP)))
                    }

                    doc.tabLabel(
                        label = label,
                        active = index == sel,
                        ref = ActionRef(tile = "_", verb = "set", arg = "$tabVar|$index")
                    )
                }
            }
        }
    }
}

internal fun DocContext.tabLabel(label: String, active: Boolean, ref: ActionRef) {
    val underlineW = maxOf(label.length * Metrics.TABS_CHAR_W_DP, Metrics.TABS_UNDERLINE_MIN_W)

    rcScope.Column(tap(Modifier, ref), horizontal = RcHorizontalPositioning.Center) {
        val doc = this@tabLabel

        doc.label(
            text = label,
            sp = Type.BODY,
            color = if (active) doc.role(Role.INK) else doc.role(Role.INK_DIM),
            weight = if (active) Weight.SEMI_BOLD else Weight.NORMAL,
        )

        Spacer(Modifier.height(doc.units.px(Metrics.TABS_UNDERLINE_GAP_DP)))

        Box(
            Modifier.size(doc.units.px(underlineW), doc.units.px(Metrics.TABS_UNDERLINE_H))
                .background(if (active) doc.role(Role.ACCENT) else doc.role(Role.PAGE)),
        )
    }
}
