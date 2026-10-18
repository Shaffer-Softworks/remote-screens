@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.render.cards.HUES
import tech.shaffersoftworks.remotescreens.server.render.cards.KELVINS

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.MarkdownLines
import tech.shaffersoftworks.remotescreens.server.render.Pages
import tech.shaffersoftworks.remotescreens.server.render.Pastel
import tech.shaffersoftworks.remotescreens.server.render.Role
import tech.shaffersoftworks.remotescreens.server.render.Type
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.Weight
import tech.shaffersoftworks.remotescreens.server.render.accent
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.cards.kelvinColor
import tech.shaffersoftworks.remotescreens.server.render.gap
import tech.shaffersoftworks.remotescreens.server.render.icon
import tech.shaffersoftworks.remotescreens.server.render.key
import tech.shaffersoftworks.remotescreens.server.render.label
import tech.shaffersoftworks.remotescreens.server.render.node
import tech.shaffersoftworks.remotescreens.server.render.paint
import tech.shaffersoftworks.remotescreens.server.render.rounded
import tech.shaffersoftworks.remotescreens.server.render.sized
import tech.shaffersoftworks.remotescreens.server.render.styled
import tech.shaffersoftworks.remotescreens.server.render.tap
import tech.shaffersoftworks.remotescreens.server.render.toggleIcon

import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcInteger
import androidx.compose.remote.creation.dsl.RcStrokeCap
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcPaintStyle
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
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
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.VarValue
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    ) } ?: fixed(Pastel.forDomain(id?.substringBefore('.')).argb)
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
    scope.Box(
        styled(node, axis, pages),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@gaugeNode
        Box(
            Modifier.size(px, px),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            Canvas(Modifier.size(px, px)) {
                paint { color(
                    track,
                ); style(RcPaintStyle.Stroke); strokeWidth(stroke); strokeCap(RcStrokeCap.Round) }
                drawArc(
                    inset,
                    inset,
                    px - inset,
                    px - inset,
                    Metrics.GAUGE_ARC_START_DEG,
                    Metrics.GAUGE_ARC_SWEEP_DEG,
                )
                if (id != null) {
                    paint { color(
                        fill,
                    ); style(
                        RcPaintStyle.Stroke,
                    ); strokeWidth(stroke); strokeCap(RcStrokeCap.Round) }
                    drawArc(
                        inset.rf,
                        inset.rf,
                        (px - inset).rf,
                        (px - inset).rf,
                        Metrics.GAUGE_ARC_START_DEG.rf,
                        doc.float(nodeLevel(node.id)) * Metrics.GAUGE_ARC_SWEEP_DEG,
                    )
                }
                if (caption != null) {
                    paint { color(
                        doc.role(Role.INK_SOFT),
                    ); style(RcPaintStyle.Fill); textSize(px * Metrics.GAUGE_CAPTION_SIZE_RATIO) }
                    drawTextAnchored(
                        caption,
                        px / 2f,
                        px - px * Metrics.GAUGE_CAPTION_Y_RATIO,
                        0f,
                        0f,
                    )
                }
            }
            if (id != null) {
                doc.label(
                    doc.text(Vars.entity(id, Vars.KEY_VALUE)),
                    maxOf(sizeDp * Metrics.GAUGE_VALUE_SIZE_RATIO, Type.CAPTION),
                    doc.role(Role.INK),
                    Weight.MEDIUM,
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
    val seconds = node.props["seconds"] == "true"
    scope.Box(
        styled(node, axis, pages),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@analogNode
        Canvas(Modifier.size(px, px)) {
            paint { color(face); style(RcPaintStyle.Fill) }
            drawCircle(mid, mid, mid)
            paint { color(
                dim,
            ); style(
                RcPaintStyle.Stroke,
            ); strokeWidth(px * Metrics.ANALOG_TICK_STROKE_RATIO); strokeCap(RcStrokeCap.Round) }
            for (i in 0 until Metrics.ANALOG_HOUR_MARKS) {
                val angle = i * 2 * PI / Metrics.ANALOG_HOUR_MARKS
                val inner = if (i % Metrics.ANALOG_QUARTER_STEP == 0) Metrics.ANALOG_TICK_INNER_MAJOR else Metrics.ANALOG_TICK_INNER_MINOR
                drawLine(
                    mid + (sin(angle) * mid * inner).toFloat(),
                    mid - (cos(angle) * mid * inner).toFloat(),
                    mid + (sin(angle) * mid * Metrics.ANALOG_TICK_OUTER).toFloat(),
                    mid - (cos(angle) * mid * Metrics.ANALOG_TICK_OUTER).toFloat(),
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
            if (hours.any()) {
                val size = px * Metrics.ANALOG_NUMBER_SIZE_RATIO
                paint { color(ink); style(RcPaintStyle.Fill); textSize(size) }
                for (hour in hours) {
                    val angle = hour * 2 * PI / Metrics.ANALOG_HOUR_MARKS
                    drawTextAnchored(
                        doc.literal(hour.toString()),
                        mid + (sin(angle) * mid * Metrics.ANALOG_NUMBER_RADIUS).toFloat(),
                        mid - (cos(angle) * mid * Metrics.ANALOG_NUMBER_RADIUS).toFloat(),
                        0f,
                        0f,
                    )
                }
            }
            // minutes() is the minute of the day and seconds() the second of the hour.
            fun hand(angle: RcFloat, length: Float, width: Float, colour: RcColor) {
                save {
                    rotate(angle, mid.rf, mid.rf)
                    paint { color(
                        colour,
                    ); style(
                        RcPaintStyle.Stroke,
                    ); strokeWidth(width); strokeCap(RcStrokeCap.Round) }
                    drawLine(mid, mid, mid, mid - mid * length)
                }
            }
            hand(
                minutes() * Metrics.ANALOG_HOUR_DEG_PER_MIN,
                Metrics.ANALOG_HOUR_HAND_LEN,
                px * Metrics.ANALOG_HOUR_HAND_WIDTH,
                ink,
            )
            hand(
                seconds() * Metrics.ANALOG_MINUTE_DEG_PER_SEC,
                Metrics.ANALOG_MINUTE_HAND_LEN,
                px * Metrics.ANALOG_MINUTE_HAND_WIDTH,
                ink,
            )
            if (seconds) {
                hand(
                    continuousSeconds() * Metrics.ANALOG_SECOND_DEG,
                    Metrics.ANALOG_SECOND_HAND_LEN,
                    px * Metrics.ANALOG_SECOND_HAND_WIDTH,
                    accent,
                )
            }
            paint { color(ink); style(RcPaintStyle.Fill) }
            drawCircle(mid, mid, px * Metrics.ANALOG_CENTER_DOT_RATIO)
        }
    }
}

/** A round key with an icon that lights up in the entity's colour while it is on. */
internal fun DocContext.iconButtonNode(node: Node, axis: Axis, pages: Pages) {
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.ICON_BUTTON_MIN_DP,
        Metrics.ICON_BUTTON_MAX_DP,
    ) ?: Metrics.ICON_BUTTON_DEFAULT_DP
    val id = node.bind?.entityId
    val domain = id?.substringBefore('.')
    val (bg, ink) = if (id != null) {
        bindEntity(id)
        bindTileColors(node.id, id, accent(node.props["color"], Pastel.forDomain(domain)))
        color(Vars.tile(node.id, Vars.KEY_BG)) to color(Vars.tile(node.id, Vars.KEY_BG_INK))
    } else {
        (node.props["color"]?.let { paint(it) } ?: role(Role.KEY)) to role(Role.INK)
    }
    val name = node.props["icon"]?.takeIf { it.isNotEmpty() } ?: id?.let { states[it]?.attr(
        "icon",
    ) } ?: domain?.let { Catalogue.of(it)?.icon } ?: "gesture-tap"
    var base = sized(node, axis)
    if (node.size.width == "wrap" && node.size.height == "wrap") {
        base = base.size(
            units.px(sizeDp),
            units.px(sizeDp),
        )
    }
    base = base.clip(rounded(if (node.style.corner > 0f) node.style.corner else sizeDp / 2f))
    if (node.style.background == null) base = base.background(bg)
    scope.Box(
        styled(node, axis, pages, base),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@iconButtonNode
        val on = node.props["iconOn"]
        if (id != null && on != null) {
            doc.toggleIcon(
                id,
                name,
                on,
                sizeDp * Metrics.ICON_BUTTON_ICON_RATIO,
                ink,
            )
        } else {
            doc.icon(name, sizeDp * Metrics.ICON_BUTTON_ICON_RATIO, ink)
        }
    }
}

/** The keys an entity's remote has: a player's transport, a cover's open, stop and close, a vacuum's, a lock's. */
internal fun DocContext.keysOf(entityState: EntityState?, domain: String?): List<Pair<String, String>> = when (domain) {
    "media_player" -> listOf(
        "previous" to "skip-previous",
        "play_pause" to "play-pause",
        "next" to "skip-next",
    )
    "cover" -> listOf("open" to "arrow-up", "stop" to "stop", "close" to "arrow-down")
    "vacuum" -> listOf(
        "start" to "play",
        "pause" to "pause",
        "dock" to "home-import-outline",
        "locate" to "map-marker-outline",
    )
    "lock" -> listOf("lock" to "lock", "unlock" to "lock-open-variant")
    "alarm_control_panel" -> (entityState?.features() ?: 0).let { features ->
        listOfNotNull(
            ("arm_home" to "shield-home").takeIf { features and Metrics.ALARM_FEAT_ARM_HOME != 0 },
            ("arm_away" to "shield-lock").takeIf { features and Metrics.ALARM_FEAT_ARM_AWAY != 0 },
            ("arm_night" to "shield-moon").takeIf { features and Metrics.ALARM_FEAT_ARM_NIGHT != 0 },
        ) + ("disarm" to "shield-off")
    }
    "light" -> listOf("off" to "lightbulb-off-outline", "on" to "lightbulb-on")
    else -> emptyList()
}

/** A row of the entity's remote keys; a lock asks for a second tap before it opens, as on its tile. */
internal fun DocContext.keysNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    val domain = id?.substringBefore('.')
    val keys = id?.let { keysOf(states[it], domain) }.orEmpty()
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.KEYS_MIN_DP,
        Metrics.KEYS_MAX_DP,
    ) ?: Metrics.KEYS_DEFAULT_DP
    val modifier = styled(node, axis, pages)
    if (id == null || keys.isEmpty()) {
        scope.Box(
            modifier.size(
                units.px(sizeDp * Metrics.KEYS_COLS + Metrics.KEYS_GAP_TOTAL_DP),
                units.px(sizeDp),
            ).clip(rounded(Metrics.KEYS_EMPTY_CORNER_DP)).background(role(Role.TRACK)),
        )
        return
    }
    bindEntity(id)
    val spec = Catalogue.of(id.substringBefore('.'))
    val stretch = node.size.width != "wrap"
    scope.Column(modifier, horizontal = RcHorizontalPositioning.Center) {
        val doc = this@keysNode
        Row(
            if (stretch) Modifier.fillMaxWidth() else Modifier,
            horizontal = RcRowHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            keys.forEachIndexed { i, (verb, icon) ->
                if (i > 0) Spacer(Modifier.width(doc.units.px(Metrics.KEYS_GAP_DP)))
                val ref = ActionRef(node.id, verb)
                if (spec?.predict?.containsKey(verb) == true) doc.predict(ref, id)
                doc.key(
                    ref,
                    icon = icon,
                    heightDp = sizeDp,
                    widthDp = if (stretch) null else sizeDp,
                    round = !stretch,
                    modifier = if (stretch) Modifier.horizontalWeight(1f) else Modifier,
                )
            }
        }
        if (domain == "lock") {
            doc.label(
                doc.text(Vars.tile(node.id, Vars.KEY_CONFIRM)),
                Type.CAPTION,
                doc.role(Role.WARN),
                Weight.SEMI_BOLD,
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
        ) to ActionRef(node.id, "kelvin", it.toString()) }
    } else {
        HUES.map { hue -> java.awt.Color.HSBtoRGB(
            hue / Metrics.HUE_DEGREES,
            Metrics.HUE_SATURATION,
            1f,
        ) to ActionRef(node.id, "hue", hue.toString()) }
    }
    val stretch = node.size.width != "wrap"
    scope.Row(
        styled(node, axis, pages),
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
    scope.Column(styled(node, axis, pages), horizontal = RcHorizontalPositioning.Center) {
        val doc = this@keypadNode
        Box(
            Modifier.height(doc.units.px(Metrics.KEYPAD_DISPLAY_H)),
            vertical = RcVerticalPositioning.Center,
        ) {
            if (id != null) {
                doc.label(
                    doc.text(codeVar),
                    Type.VALUE,
                    doc.role(Role.INK),
                    Weight.MEDIUM,
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
                Modifier.padding(0f, 0f, 0f, doc.units.px(Metrics.KEYPAD_ROW_GAP_DP)),
                horizontal = RcRowHorizontalPositioning.Center,
            ) {
                row.forEachIndexed { i, digit ->
                    if (i > 0) Spacer(Modifier.width(doc.units.px(Metrics.KEYPAD_KEY_GAP_DP)))
                    val ref = when (digit) {
                        "clear" -> ActionRef(node.id, "code_clear")
                        "back" -> ActionRef(node.id, "code_back")
                        else -> ActionRef(node.id, "code_digit", digit)
                    }
                    val icon = when (digit) { "clear" -> "close"; "back" -> "backspace-outline"; else -> null }
                    doc.key(
                        ref,
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
    val domain = id?.substringBefore('.')
    val sizeDp = node.props["size"]?.toFloatOrNull()?.coerceIn(
        Metrics.DIAL_MIN_DP,
        Metrics.DIAL_MAX_DP,
    )
        ?: Metrics.DIAL_DEFAULT_DP
    val px = units.px(sizeDp)
    val stroke = px * Metrics.DIAL_STROKE_RATIO
    val inset = stroke / 2f
    val fill = node.props["color"]?.let { paint(it) } ?: fixed(Pastel.forDomain(domain).argb)
    val how = id?.let { stepOf(node, states[it], domain) }
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
    val down = how?.let { ActionRef(node.id, it.first, "-$step") }
    val up = how?.let { ActionRef(node.id, it.first, "+$step") }
    val isPredictable = how != null && domain?.let { Catalogue.of(
        it,
    ) }?.predict?.containsKey(how.first) == true
    if (id != null && isPredictable) {
        down?.let { predict(it, id) }
        up?.let { predict(it, id) }
    }
    scope.Column(styled(node, axis, pages), horizontal = RcHorizontalPositioning.Center) {
        val doc = this@dialNode
        Box(
            Modifier.size(px, px),
            horizontal = RcHorizontalPositioning.Center,
            vertical = RcVerticalPositioning.Center,
        ) {
            Canvas(Modifier.size(px, px)) {
                paint {
                    color(doc.role(Role.TRACK)); style(RcPaintStyle.Stroke)
                    strokeWidth(stroke); strokeCap(RcStrokeCap.Round)
                }
                drawArc(
                    inset,
                    inset,
                    px - inset,
                    px - inset,
                    Metrics.GAUGE_ARC_START_DEG,
                    Metrics.GAUGE_ARC_SWEEP_DEG,
                )
                if (id != null) {
                    paint {
                        color(fill); style(RcPaintStyle.Stroke)
                        strokeWidth(stroke); strokeCap(RcStrokeCap.Round)
                    }
                    val sweep = doc.float(nodeLevel(node.id)) * Metrics.GAUGE_ARC_SWEEP_DEG
                    drawArc(
                        inset.rf,
                        inset.rf,
                        (px - inset).rf,
                        (px - inset).rf,
                        Metrics.GAUGE_ARC_START_DEG.rf,
                        sweep,
                    )
                }
            }
            val valueSp = maxOf(sizeDp * Metrics.DIAL_VALUE_SIZE_RATIO, Type.CAPTION)
            if (id != null) {
                doc.label(
                    doc.text(Vars.entity(id, Vars.KEY_VALUE)),
                    valueSp,
                    doc.role(Role.INK),
                    Weight.MEDIUM,
                    align = RcTextAlign.Center,
                )
            } else {
                doc.label(
                    "–",
                    valueSp,
                    doc.role(Role.INK_DIM),
                    Weight.MEDIUM,
                    align = RcTextAlign.Center,
                )
            }
        }
        doc.gap(Metrics.KEYS_GAP_DP)
        Row(vertical = RcVerticalPositioning.Center) {
            fun key(ref: ActionRef?, icon: String) {
                if (ref != null) {
                    doc.key(
                        ref,
                        icon = icon,
                        heightDp = Metrics.STEPPER_KEY_DP,
                        widthDp = Metrics.STEPPER_KEY_DP,
                        round = true,
                    )
                } else {
                    val knob = doc.units.px(Metrics.STEPPER_KEY_DP)
                    Box(
                        Modifier.size(knob, knob)
                            .clip(doc.rounded(Metrics.STEPPER_KEY_CORNER_DP))
                            .background(doc.role(Role.KEY)),
                    )
                }
            }
            key(down, "minus")
            Spacer(Modifier.width(doc.units.px(Metrics.KEYS_GAP_TOTAL_DP)))
            key(up, "plus")
        }
    }
}

/** A timer's remaining time, or static text from props. */
internal fun DocContext.countdownNode(node: Node, axis: Axis, pages: Pages) {
    val id = node.bind?.entityId
    if (id != null) bindEntity(id)
    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.DISPLAY
    scope.Box(
        styled(node, axis, pages),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        val doc = this@countdownNode
        when {
            id != null -> doc.label(
                doc.text(Vars.entity(id, Vars.KEY_VALUE)),
                sp,
                ink,
                Weight.LIGHT,
                align = RcTextAlign.Center,
            )
            else -> doc.label(
                node.props["text"] ?: "0:00",
                sp,
                ink,
                Weight.LIGHT,
                align = RcTextAlign.Center,
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
    picture(PictureSlot(slot, "qr:$text", px, px, 0, crop = false))
    // Mockup: white rounded plate around the code.
    val plate = styled(node, axis, pages)
        .clip(rounded(Metrics.QR_CORNER_DP))
        .background(role(Role.INK))
        .padding(units.px(Metrics.QR_PAD_DP))
    scope.Box(
        plate,
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        Image(
            this@qrNode.image(slot, px, px),
            Modifier.size(units.px(inner), units.px(inner)),
            contentScale = RcContentScale.Fit,
        )
    }
}

/** Constrained markdown: one Text line per source line, with whole-line bold or italic. */
internal fun DocContext.markdownNode(node: Node, axis: Axis, pages: Pages) {
    val lines = MarkdownLines.parse(node.props["text"].orEmpty())
    val ink = node.props["color"]?.let { paint(it) } ?: role(Role.INK)
    val sp = node.props["fontSize"]?.toFloatOrNull() ?: Type.BODY
    scope.Column(styled(node, axis, pages)) {
        val doc = this@markdownNode
        for (line in lines) {
            val weight = when {
                line.bold -> Weight.BOLD
                line.italic -> Weight.NORMAL
                else -> Weight.NORMAL
            }
            doc.label(line.text.ifEmpty { " " }, sp, ink, weight)
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
    scope.Column(styled(node, axis, pages, measured(node, axis, self))) {
        val doc = this@tabsNode
        doc.tabStrip(shown, tabVar, selected, stripH)
        Spacer(Modifier.height(doc.units.px(gap)))
        StateLayout(selected, Modifier.size(doc.units.px(bodyW), doc.units.px(bodyH))) {
            for (child in children) {
                Box(Modifier.fillMaxSize()) {
                    doc.node(child, Dims(bodyW, bodyH), Axis.BOX, pages)
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
    scope.StateLayout(selected, Modifier.fillMaxWidth().height(units.px(stripH))) {
        for (sel in shown.indices) {
            Row(
                Modifier.fillMaxWidth(),
                horizontal = RcRowHorizontalPositioning.Start,
                vertical = RcVerticalPositioning.Center,
            ) {
                val doc = this@tabStrip
                shown.forEachIndexed { index, label ->
                    if (index > 0) {
                        Spacer(Modifier.width(doc.units.px(Metrics.TABS_PIPE_GAP_DP)))
                        doc.label("|", Type.BODY, doc.role(Role.INK_DIM))
                        Spacer(Modifier.width(doc.units.px(Metrics.TABS_PIPE_GAP_DP)))
                    }
                    doc.tabLabel(label, index == sel, ActionRef("_", "set", "$tabVar|$index"))
                }
            }
        }
    }
}

internal fun DocContext.tabLabel(label: String, active: Boolean, ref: ActionRef) {
    val underlineW = maxOf(label.length * Metrics.TABS_CHAR_W_DP, Metrics.TABS_UNDERLINE_MIN_W)
    scope.Column(tap(Modifier, ref), horizontal = RcHorizontalPositioning.Center) {
        val doc = this@tabLabel
        doc.label(
            label,
            Type.BODY,
            if (active) doc.role(Role.INK) else doc.role(Role.INK_DIM),
            if (active) Weight.SEMI_BOLD else Weight.NORMAL,
        )
        Spacer(Modifier.height(doc.units.px(Metrics.TABS_UNDERLINE_GAP_DP)))
        Box(
            Modifier.size(doc.units.px(underlineW), doc.units.px(Metrics.TABS_UNDERLINE_H))
                .background(if (active) doc.role(Role.ACCENT) else doc.role(Role.PAGE)),
        )
    }
}
