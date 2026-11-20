package tech.shaffersoftworks.remotescreens.server.service

import io.ktor.server.plugins.BadRequestException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ServiceData
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ServiceRef
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.player.action.ActionRouter
import tech.shaffersoftworks.remotescreens.server.player.action.TapMemory
import tech.shaffersoftworks.remotescreens.server.player.action.TapOutcome
import tech.shaffersoftworks.remotescreens.server.render.freeform.LvglDocument
import tech.shaffersoftworks.remotescreens.server.render.freeform.LvglExport
import tech.shaffersoftworks.remotescreens.server.render.freeform.LvglPage
import tech.shaffersoftworks.remotescreens.server.render.freeform.lvglSources
import tech.shaffersoftworks.remotescreens.server.render.theme.Palette
import tech.shaffersoftworks.remotescreens.server.render.tiles.tapRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.DeviceSize
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.Screen

private val DEVICE_NAME = Regex("^[a-z0-9_]+$")
private const val MAX_DEVICE_NAME = 64

/** The free-form page an ESPHome panel draws, and the taps that come back from it. */
class EspHomePanelService(
    private val homeAssistant: HomeAssistant,
    private val screens: () -> List<Screen>,
    private val paletteOf: (String) -> Palette,
    private val actions: ActionRouter,
    private val words: () -> I18n,
) {
    /** YAML to compile into ESPHome, and the payload [send] pushes to a panel that is already running. */
    fun export(request: EspHomeExportRequest): LvglDocument {
        val widthOk = request.widthPx in DeviceSize.MIN..DeviceSize.MAX
        val heightOk = request.heightPx in DeviceSize.MIN..DeviceSize.MAX

        if (!widthOk || !heightOk) throw BadRequestException("Panel size is out of range")

        return LvglExport.render(
            page = LvglPage(
                root = request.root,
                palette = paletteOf(request.themeId),
                widthPx = request.widthPx,
                heightPx = request.heightPx,
                screenId = request.screenId,
                pageId = request.pageId,
                sources = lvglSources(states = homeAssistant.states.value, i18n = words()),
            ),
        )
    }

    /** Calls `esphome.<device>_set_layout` with the payload. Returns that service name. */
    suspend fun send(device: String, payload: String): String {
        val name = device.trim()

        if (!DEVICE_NAME.matches(name) || name.length > MAX_DEVICE_NAME) {
            throw BadRequestException("Use the ESPHome device name: letters, numbers and underscores")
        }

        if (payload.isBlank()) throw BadRequestException("There is no layout to send")

        val serviceName = "${name}_set_layout"

        homeAssistant.callService(
            service = ServiceRef(domain = Domain("esphome"), service = serviceName),
            data = ServiceData(fields = buildJsonObject { put(key = "layout", value = payload) }),
        )

        return "esphome.$serviceName"
    }

    /** Runs the component's tap through the same router as a tablet. Null when [screenId] is not a screen. */
    suspend fun tap(screenId: String, pageId: String, nodeId: String, hold: Boolean): String? {
        val screen = screens().firstOrNull { it.id == screenId }

        return screen?.let { outcomeOf(screen = it, pageId = pageId, nodeId = nodeId, hold = hold) }
    }

    private suspend fun outcomeOf(screen: Screen, pageId: String, nodeId: String, hold: Boolean): String {
        val page = screen.pages.firstOrNull { it.id == pageId }
            ?: throw BadRequestException("That page is not on the screen")

        val node = page.root?.walk()?.firstOrNull { it.id == nodeId }
            ?: throw BadRequestException("That component is not on the page")

        val tile = node.asTile()
        val spec = tile?.let { Catalogue.of(it.effectiveKind()) }
        val action = if (hold) tile?.hold else tile?.tap

        val ref = if (tile == null || action == null) {
            null
        } else {
            tapRef(
                tile = tile,
                action = action,
                spec = spec,
                pageIndex = { id -> screen.pages.indexOfFirst { it.id == id }.takeIf { it >= 0 } },
                hold = hold,
            )
        }

        val outcome = if (ref == null) null else actions.handle(ref = ref, screen = screen, memory = TapMemory())

        return when (outcome) {
            null, TapOutcome.Ignored -> "ignored"
            is TapOutcome.Failed -> "failed"
            else -> "ran"
        }
    }
}

@Serializable
data class EspHomeExportRequest(
    val root: Node,
    val themeId: String = "rpi",
    val widthPx: Int,
    val heightPx: Int,
    val screenId: String,
    val pageId: String,
)

@Serializable
data class EspHomeSendRequest(val device: String, val payload: String)

@Serializable
data class EspHomeSendResponse(val service: String)

@Serializable
data class PanelTapRequest(
    val screenId: String,
    val pageId: String,
    val nodeId: String,
    val hold: Boolean = false,
)

@Serializable
data class PanelTapResponse(val outcome: String)
