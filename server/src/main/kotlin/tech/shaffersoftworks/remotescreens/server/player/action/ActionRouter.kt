package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AGENDA_FIELD_SEPARATOR
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaCopy
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaSnapshot
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.ALARM_PIN_LENGTH
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.alarmDotVar
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.isAlarmPage
import tech.shaffersoftworks.remotescreens.shared.VarValue
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.CalendarDialogMode
import tech.shaffersoftworks.remotescreens.shared.ActionSpec
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.PredictSpec
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.TapType
import tech.shaffersoftworks.remotescreens.shared.TypeSpec
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

private const val MAX_CODE_LENGTH = 12
private const val ENTITY_TILE_PREFIX = "e:"

/** Turns a tap on a document into a Home Assistant service call, and foresees the state it will lead to. */
class ActionRouter(
    private val homeAssistant: HomeAssistant,
    private val i18n: () -> I18n,
    private val renderData: RenderDataRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(ActionRouter::class.java)
    private val alarmActions = AlarmActions(homeAssistant, i18n, renderData)
    private val lightsActions = LightsActions(homeAssistant, renderData)
    private val camerasActions = CamerasActions(renderData)

    fun target(ref: ActionRef, screen: Screen?): TapTarget? = when {
        ref.tile.startsWith(ENTITY_TILE_PREFIX) -> {
            val entityId = ref.tile.removePrefix(ENTITY_TILE_PREFIX)
            TapTarget(ref.tile, entityId, entityId.substringBefore('.'), null)
        }
        ref.tile == "card" -> null
        else -> screen?.pages
            ?.flatMap { it.allTiles() }
            ?.firstOrNull { it.id == ref.tile }
            ?.let { tile -> TapTarget(tile.id, tile.entityId, tile.effectiveKind(), tile) }
    }

    // Any failure of a tap is shown on the tablet rather than ending its session.
    @Suppress("TooGenericExceptionCaught")
    suspend fun handle(
        ref: ActionRef,
        screen: Screen?,
        memory: TapMemory,
        nowMs: Long = System.currentTimeMillis(),
    ): TapOutcome {
        val target = target(ref, screen) ?: return TapOutcome.Ignored
        val entity = target.entityId?.let { homeAssistant.states.value[it] }
        val verb = resolveVerb(ref.verb, target, entity)
        return try {
            if (verb == "calendar_create" || verb == "calendar_delete") {
                handleCalendar(verb, ref, target)
            } else {
                dispatchTap(verb, ref, target, entity, memory, nowMs)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            logger.warn("Action {} failed: {}", ref.encode(), e.message)
            TapOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun dispatchTap(
        verb: String,
        ref: ActionRef,
        target: TapTarget,
        entity: EntityState?,
        memory: TapMemory,
        nowMs: Long,
    ): TapOutcome = when (verb) {
        "card" -> TapOutcome.Card(
            target.entityId,
            if (ref.arg == "history") "history" else target.kind,
        )
        "run" -> runTileAction(target)
        "favourite" -> playFavourite(target)
        "event" -> fireEvent(ref.arg, target)
        "code_digit", "code_back", "code_clear" -> typeCode(verb, ref.arg, target, memory)
        "alarm_arm", "alarm_zone", "alarm_dismiss" -> alarmActions.handle(verb, ref.arg, target, memory)
        "lights_room", "lights_detail", "lights_dismiss", "lights_mode", "lights_palette",
        "lights_effects", "lights_effect",
        -> lightsActions.handle(verb, ref.arg, target)
        "cameras_select" -> camerasActions.handle(ref.arg, target)
        "browse_type", "listen_mode", "primary_tab", "load_more",
        "clear_queue", "play_uri", "enqueue_uri",
        "volume_up", "volume_down",
        -> handleMusic(verb, ref, target)
        else -> callService(verb, ref, target, entity, memory, nowMs)
    }

    /** See [ServiceDataBuilder.resolveArg]. */
    fun resolveArg(action: ActionSpec, arg: String, entity: EntityState?): String =
        ServiceDataBuilder.resolveArg(action, arg, entity)

    /** The state Home Assistant will most likely report once [ref] is done, or null when it cannot be told. */
    fun foresee(ref: ActionRef, target: TapTarget?, entity: EntityState): EntityState? {
        val spec = target?.spec ?: Catalogue.of(entity.domain)
        val verb = resolveVerb(
            ref.verb,
            target ?: TapTarget("", entity.entityId, entity.domain, null),
            entity,
        )
        val prediction = spec?.predict?.get(verb) ?: return null
        val action = spec.actions[verb]
        val arg = if (action != null) {
            ServiceDataBuilder.resolveArg(
                action,
                ref.arg,
                entity,
            )
        } else {
            ref.arg
        }
        val attributes = prediction.attributes.mapValues { (_, value) ->
            if (ServiceDataBuilder.isArgExpression(value)) {
                ServiceDataBuilder.argValue(value, arg)
            } else {
                JsonPrimitive(value)
            }
        }
        return entity.with(predictedState(prediction, spec, entity, arg), attributes)
    }

    private fun predictedState(prediction: PredictSpec, spec: TypeSpec, entity: EntityState, arg: String): String =
        when (val predicted = prediction.state) {
            null -> entity.state
            "!" -> if (entity.state in spec.onStates) "off" else spec.onStates.firstOrNull() ?: "on"
            "play_pause" -> if (entity.state == "playing") "paused" else "playing"
            else -> if (ServiceDataBuilder.isArgExpression(predicted)) {
                ServiceDataBuilder.argValue(predicted, arg).contentOrNull ?: entity.state
            } else {
                predicted
            }
        }

    private fun resolveVerb(verb: String, target: TapTarget, entity: EntityState?): String = when {
        target.kind == "lock" && verb == "toggle" -> if (entity?.state == "locked") "unlock" else "lock"
        else -> verb
    }

    private suspend fun callService(
        verb: String,
        ref: ActionRef,
        target: TapTarget,
        entity: EntityState?,
        memory: TapMemory,
        nowMs: Long,
    ): TapOutcome {
        val spec = if (target.kind == "music") Catalogue.of("media_player") else target.spec
        val action = spec?.actions?.get(verb)
        val entityId = target.entityId
        if (action == null || entityId == null) return TapOutcome.Ignored
        return confirmationNeeded(action, verb, target, memory, nowMs) ?: run {
            val code = memory.codes.remove(entityId)
            val data = ServiceDataBuilder.build(action, verb, ref.arg, entity, code)
            val (domain, service) = action.service.split('.', limit = 2)
            homeAssistant.callService(
                domain,
                service,
                JsonObject(data + ("entity_id" to JsonPrimitive(entityId))),
            )
            TapOutcome.Done
        }
    }

    private fun handleCalendar(verb: String, ref: ActionRef, target: TapTarget): TapOutcome {
        val agendas = renderData?.calendarAgenda ?: return TapOutcome.Ignored
        val snapshot = agendas.latest
        return when (verb) {
            "calendar_create" -> TapOutcome.OpenCalendar(
                tileId = target.tileId,
                mode = CalendarDialogMode.CREATE,
                calendars = snapshot.calendars.filter { it.canCreate },
            )
            "calendar_delete" -> deleteDialog(ref.arg, target.tileId, snapshot)
            else -> TapOutcome.Ignored
        }
    }

    private fun deleteDialog(arg: String, tileId: String, snapshot: AgendaSnapshot): TapOutcome {
        val parts = arg.split(AGENDA_FIELD_SEPARATOR)
        val entityId = parts.getOrNull(0).orEmpty()
        val uid = parts.getOrNull(1).orEmpty()
        if (entityId.isBlank() || uid.isBlank()) return TapOutcome.Ignored
        val recurrenceId = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
        val event = snapshot.events.firstOrNull { candidate ->
            candidate.entityId == entityId && candidate.uid == uid &&
                (recurrenceId == null || candidate.recurrenceId == recurrenceId)
        }
        return if (event == null || !event.canDelete) {
            TapOutcome.AgendaNotice(tileId, AgendaCopy.CANNOT_DELETE)
        } else {
            TapOutcome.OpenCalendar(tileId, CalendarDialogMode.DELETE, event = event)
        }
    }

    private suspend fun handleMusic(verb: String, ref: ActionRef, target: TapTarget): TapOutcome {
        val tileId = target.tileId
        val entityId = target.entityId
        val repo = renderData
        return if (entityId == null || repo == null) {
            TapOutcome.Ignored
        } else {
            musicVerb(verb, ref, target, tileId, entityId, repo)
        }
    }

    private suspend fun musicVerb(
        verb: String,
        ref: ActionRef,
        target: TapTarget,
        tileId: String,
        entityId: String,
        repo: RenderDataRepository,
    ): TapOutcome = when (verb) {
        "browse_type" -> {
            repo.musicUi.setBrowseType(tileId, ref.arg)
            TapOutcome.Rebuild
        }
        "listen_mode" -> {
            repo.musicUi.setListenMode(tileId, ref.arg)
            TapOutcome.Rebuild
        }
        "primary_tab" -> {
            repo.musicUi.setPrimarySection(tileId, ref.arg)
            TapOutcome.Rebuild
        }
        "load_more" -> musicLoadMore(repo, target, tileId, entityId)
        "clear_queue" -> {
            repo.music.clearPlaylist(entityId)
            repo.invalidateMusic(playerEntityId = entityId)
            TapOutcome.Rebuild
        }
        "play_uri" -> musicPlayUri(repo, entityId, ref.arg, enqueue = "play")
        "enqueue_uri" -> musicPlayUri(repo, entityId, ref.arg, enqueue = "add")
        "volume_up" -> musicVolume(entityId, "volume_up")
        "volume_down" -> musicVolume(entityId, "volume_down")
        else -> TapOutcome.Ignored
    }

    private suspend fun musicLoadMore(
        repo: RenderDataRepository,
        target: TapTarget,
        tileId: String,
        entityId: String,
    ): TapOutcome {
        val configEntryId = target.tile?.options?.get("config_entry_id")
            ?: repo.music.configEntryIdForEntity(entityId)
        if (configEntryId == null) return TapOutcome.Failed("No Music Assistant config entry")
        repo.loadMoreBrowse(tileId, configEntryId)
        return TapOutcome.Rebuild
    }

    private suspend fun musicPlayUri(
        repo: RenderDataRepository,
        entityId: String,
        uri: String,
        enqueue: String,
    ): TapOutcome {
        val ok = repo.music.playMediaUri(entityId, uri, enqueue = enqueue)
        if (!ok) {
            val message = if (enqueue == "add") "Could not add to queue" else "Could not start playback"
            return TapOutcome.Failed(message)
        }
        repo.invalidateMusic(playerEntityId = entityId)
        return TapOutcome.Rebuild
    }

    private suspend fun musicVolume(entityId: String, service: String): TapOutcome {
        homeAssistant.callService(
            "media_player",
            service,
            buildJsonObject { put("entity_id", entityId) },
        )
        return TapOutcome.Done
    }

    /** For an action that asks to be confirmed: the prompt on the first tap, null on a second tap in time. */
    private fun confirmationNeeded(
        action: ActionSpec,
        verb: String,
        target: TapTarget,
        memory: TapMemory,
        nowMs: Long,
    ): TapOutcome.Confirm? {
        if (!action.confirm) return null
        val key = "${target.tileId}|$verb"
        val pending = memory.pendingConfirm
        val isSecondTap = pending != null && pending.key == key && nowMs - pending.sinceMs <= CONFIRM_MS
        memory.pendingConfirm = if (isSecondTap) null else PendingConfirm(key, nowMs)
        return if (isSecondTap) {
            null
        } else {
            val text = i18n().t(if (verb == "open") "ui.tap_again_open" else "ui.tap_again_unlock")
            TapOutcome.Confirm("t.${target.tileId}.confirm", text)
        }
    }

    private suspend fun runTileAction(target: TapTarget): TapOutcome {
        val tap = target.tile?.let { tile -> listOf(
            tile.tap,
            tile.hold,
        ).firstOrNull { it.type == TapType.ACTION } }
        val service = tap?.service
        val serviceParts = service?.split('.', limit = 2)
        return when {
            serviceParts == null -> TapOutcome.Ignored
            serviceParts.size != 2 -> TapOutcome.Failed("Bad service $service")
            else -> {
                val data = (tap.data ?: JsonObject(emptyMap())).toMutableMap()
                if ("entity_id" !in data && target.entityId != null) {
                    data["entity_id"] = JsonPrimitive(target.entityId)
                }
                homeAssistant.callService(serviceParts[0], serviceParts[1], JsonObject(data))
                TapOutcome.Done
            }
        }
    }

    private suspend fun playFavourite(target: TapTarget): TapOutcome {
        val options = target.tile?.options
        val mediaPlayer = target.entityId
        val contentId = options?.get("content_id")
        if (mediaPlayer == null || contentId == null) return TapOutcome.Ignored
        val data = mapOf(
            "entity_id" to JsonPrimitive(mediaPlayer),
            "media_content_id" to JsonPrimitive(contentId),
            "media_content_type" to JsonPrimitive(options["content_type"] ?: "music"),
        )
        homeAssistant.callService("media_player", "play_media", JsonObject(data))
        return TapOutcome.Done
    }

    private suspend fun fireEvent(eventType: String, target: TapTarget): TapOutcome {
        if (eventType.isBlank()) return TapOutcome.Ignored
        homeAssistant.command(
            "fire_event",
            buildJsonObject {
                put("event_type", eventType)
                put(
                    "event_data",
                    buildJsonObject {
                        target.entityId?.let { put("entity_id", it) }
                        put("source", "remote_screens")
                    },
                )
            },
        )
        return TapOutcome.Done
    }

    private fun typeCode(verb: String, arg: String, target: TapTarget, memory: TapMemory): TapOutcome {
        val entityId = target.entityId ?: return TapOutcome.Ignored
        val typed = memory.codes[entityId].orEmpty()
        val next = when (verb) {
            "code_digit" -> (typed + arg.filter(Char::isDigit)).take(MAX_CODE_LENGTH)
            "code_back" -> typed.dropLast(1)
            else -> ""
        }
        memory.codes[entityId] = next
        val dots = if (target.tile?.isAlarmPage() == true) {
            (0 until ALARM_PIN_LENGTH).map { index ->
                VarValue.int(alarmDotVar(entityId, index), if (index < next.length) Vars.VISIBLE else Vars.GONE)
            }
        } else {
            emptyList()
        }
        return TapOutcome.Code("t.e:$entityId.code", "•".repeat(next.length), dots)
    }

    companion object {
        const val CONFIRM_MS = 4000L
    }
}
