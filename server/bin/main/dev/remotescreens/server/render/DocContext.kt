@file:Suppress("RestrictedApi")

package dev.remotescreens.server.render

import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcImage
import androidx.compose.remote.creation.dsl.RcInteger
import androidx.compose.remote.creation.dsl.RcPath
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcText
import dev.remotescreens.server.I18n
import dev.remotescreens.server.ha.EntityState
import dev.remotescreens.server.ha.HaConfig
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.VarValue
import java.awt.image.BufferedImage

/**
 * Everything a renderer needs while it writes one document, and what it leaves behind: the named variables it
 * declared, how to compute their values from Home Assistant's states, the pictures to send, and the actions whose
 * effect can be shown before Home Assistant answers.
 */
class DocContext(
    val u: Units,
    val dark: Boolean,
    val i18n: I18n,
    val states: Map<String, EntityState>,
    val ha: HaConfig,
    val data: RenderData,
) {
    lateinit var scope: RcScope

    private val texts = HashMap<String, RcText>()
    private val ints = HashMap<String, RcInteger>()
    private val floats = HashMap<String, RcFloat>()
    private val colors = HashMap<String, RcColor>()
    private val images = HashMap<String, RcImage>()
    private val paths = HashMap<String, RcPath>()
    private val metas = HashMap<String, RcText>()

    val bindings = mutableListOf<Binding>()
    val pictures = mutableListOf<PictureSlot>()
    /** Action metadata to the action and the entity it changes. */
    val predicted = LinkedHashMap<String, Pair<ActionRef, String>>()
    /** What of an entity is drawn into the document itself (an icon, a list of modes), and what it was. */
    val structure = mutableListOf<Triple<String, (EntityState?) -> String, String>>()
    /** Rebuild after this many seconds (a forecast, a history graph), or 0 for never. */
    var refreshSeconds = 0

    /** Every declaration so far, in order, to replay at the top of the next build (see [build]). */
    private val declarations = mutableListOf<DocContext.() -> Unit>()

    private inline fun <T> declare(cache: HashMap<String, T>, key: String, noinline again: DocContext.() -> Unit, make: () -> T): T =
        cache.getOrPut(key) {
            declarations += again
            make()
        }

    fun text(name: String): RcText = declare(texts, name, { text(name) }) { scope.remoteNamedText(Rc.USER + name, "") }
    fun int(name: String, initial: Int = 0): RcInteger = declare(ints, name, { int(name, initial) }) { scope.remoteNamedInteger(Rc.USER + name, initial) }
    fun float(name: String, initial: Float = 0f): RcFloat = declare(floats, name, { float(name, initial) }) { scope.remoteNamedFloat(Rc.USER + name, initial) }
    fun color(name: String, initial: Int = 0): RcColor = declare(colors, name, { color(name, initial) }) { scope.remoteNamedColor(Rc.USER + name, initial) }
    fun fixed(argb: Int): RcColor = declare(colors, "#" + argb.toUInt().toString(16), { fixed(argb) }) { scope.remoteColor(argb) }
    fun role(r: Role): RcColor = fixed(r.argb)

    /** Whether the document has a named variable [name], so a value for it is worth sending. */
    fun declares(name: String): Boolean = name in texts || name in ints || name in floats || name in colors

    fun image(name: String, w: Int, h: Int): RcImage = declare(images, name, { image(name, w, h) }) {
        scope.remoteNamedBitmap(Rc.USER + name, BufferedImage(w.coerceAtLeast(1), h.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB))
    }

    fun path(icon: String?): RcPath {
        val svg = Icons.path(icon)
        return declare(paths, svg, { path(icon) }) { scope.remotePathData(Rc.path(svg)) }
    }

    fun meta(ref: ActionRef): RcText {
        val s = ref.encode()
        return declare(metas, s, { meta(ref) }) { scope.remoteText(s) }
    }

    fun bind(entityId: String?, compute: (Map<String, EntityState>) -> List<VarValue>) {
        bindings += Binding(entityId, compute)
    }

    fun predict(ref: ActionRef, entityId: String) {
        predicted[ref.encode()] = ref to entityId
    }

    fun picture(slot: PictureSlot) {
        if (pictures.none { it.name == slot.name }) pictures += slot
    }

    /** The document is built again when [what] of the entity changes, such as its icon or its list of modes. */
    fun structural(entityId: String, what: (EntityState?) -> String) {
        structure += Triple(entityId, what, what(states[entityId]))
    }

    fun refreshEvery(seconds: Int) {
        refreshSeconds = if (refreshSeconds == 0) seconds else minOf(refreshSeconds, seconds)
    }

    companion object {
        /**
         * Builds a document twice. Variables, colours, paths and texts declared inside a layout component are lost
         * when the player reads the document (alpha20), so a first pass finds what [content] declares and the
         * second declares all of it at the top before laying anything out.
         */
        fun build(
            widthPx: Int,
            heightPx: Int,
            description: String,
            newContext: () -> DocContext,
            content: RcScope.(DocContext) -> Unit,
        ): Pair<ByteArray, DocContext> {
            val dry = newContext()
            Rc.document(widthPx, heightPx, description) {
                dry.scope = this
                content(dry)
            }
            val ctx = newContext()
            val bytes = Rc.document(widthPx, heightPx, description) {
                ctx.scope = this
                for (d in dry.declarations) d(ctx)
                content(ctx)
            }
            return bytes to ctx
        }
    }
}

class Binding(val entityId: String?, val compute: (Map<String, EntityState>) -> List<VarValue>)

/** A picture the add-on fetches and sends as a `bitmap` message. */
data class PictureSlot(
    val name: String,
    /** A path Home Assistant serves (`/api/camera_proxy/camera.x`), or `map:<tile>` for a drawn map. */
    val source: String,
    val widthPx: Int,
    val heightPx: Int,
    /** 0 sends it once, and again when [source] changes. */
    val refreshSeconds: Int,
    val crop: Boolean = true,
    val round: Boolean = false,
)

/** Data a renderer draws into the document, fetched before it starts. */
data class RenderData(
    val forecasts: Map<String, List<Forecast>> = emptyMap(),
    val history: Map<String, List<Pair<Long, Double>>> = emptyMap(),
    val energy: EnergyPrefs? = null,
    val browse: Map<String, List<BrowseItem>> = emptyMap(),
)

data class Forecast(val time: java.time.ZonedDateTime, val condition: String, val high: Double?, val low: Double?, val rain: Int?)
data class EnergyPrefs(val solar: String?, val grid: String?, val battery: String?, val devices: List<Pair<String, String>>)
data class BrowseItem(val title: String, val contentId: String, val contentType: String, val canPlay: Boolean, val canExpand: Boolean, val thumbnail: String?)
