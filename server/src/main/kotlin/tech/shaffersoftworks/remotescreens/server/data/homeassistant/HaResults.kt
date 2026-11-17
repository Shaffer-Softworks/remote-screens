package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive

// What Home Assistant answers to the commands in HaRequests.kt. Lists drop the entries they cannot read.

@Serializable
data class AreaEntry(@SerialName("area_id") val areaId: String, val name: String? = null) {
    val displayName: String get() = name ?: areaId
}

@Serializable
data class DeviceEntry(val id: String, @SerialName("area_id") val areaId: String? = null)

/** `config/entity_registry/list_for_display`, which names its fields with two letters. */
@Serializable
data class DisplayEntityRegistry(
    @Serializable(with = DisplayEntityEntries::class) val entities: List<DisplayEntityEntry> = emptyList(),
) {
    /** Each entity's area: its own, else its device's. */
    fun entityAreaIds(devices: List<DeviceEntry>): Map<String, String> {
        val deviceAreas = devices.mapNotNull { device -> device.areaId?.let { device.id to it } }.toMap()
        return entities.mapNotNull { entry ->
            (entry.areaId ?: entry.deviceId?.let(deviceAreas::get))?.let { entry.entityId to it }
        }.toMap()
    }
}

@Serializable
data class DisplayEntityEntry(
    @SerialName("ei") val entityId: String,
    @SerialName("ai") val areaId: String? = null,
    @SerialName("di") val deviceId: String? = null,
    @SerialName("ic") val icon: String? = null,
    @SerialName("hb") val hiddenBy: Boolean? = null,
    @SerialName("ec") val entityCategory: Int? = null,
) {
    /** Hidden by a user or integration, or a config or diagnostic entity. */
    val isHidden: Boolean get() = hiddenBy != null || entityCategory != null
}

/** `config/entity_registry/get`. */
@Serializable
data class EntityRegistryEntry(
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("config_entry_id") val configEntryId: String? = null,
    val options: Options = Options(),
) {
    @Serializable
    data class Options(val vacuum: VacuumOptions? = null)

    /** The vacuum's Home Assistant areas mapped to its own segments. */
    @Serializable
    data class VacuumOptions(@SerialName("area_mapping") val areaMapping: Map<String, List<JsonPrimitive>> = emptyMap())
}

/** One row of a minimal history: state [state] at [lastUpdated] (or [lastChanged]) seconds. */
@Serializable
data class HistoryRow(
    @SerialName("s") val state: String? = null,
    @SerialName("lu") val lastUpdated: Double? = null,
    @SerialName("lc") val lastChanged: Double? = null,
)

/** One recorder statistics row: the period's [start] in ms and its [change]. */
@Serializable
data class StatisticRow(val start: Long, val change: Double)

/** `energy/get_prefs`: the sources and the individual devices on the Energy dashboard. */
@Serializable
data class EnergyPreferences(
    @SerialName("energy_sources")
    @Serializable(with = EnergySources::class)
    val energySources: List<EnergySource> = emptyList(),
    @SerialName("device_consumption")
    @Serializable(with = EnergyDevices::class)
    val deviceConsumption: List<EnergyDevice> = emptyList(),
)

@Serializable
data class EnergySource(
    val type: String,
    @SerialName("stat_energy_from") val statEnergyFrom: String? = null,
    @SerialName("number_energy_price") val numberEnergyPrice: Double? = null,
    @SerialName("stat_rate") val statRate: String? = null,
    @SerialName("stat_power") val statPower: String? = null,
) {
    /** The live power sensor. */
    val powerSensor: String? get() = statRate ?: statPower

    companion object {
        const val GRID = "grid"
        const val SOLAR = "solar"
        const val BATTERY = "battery"
    }
}

@Serializable
data class EnergyDevice(
    @SerialName("stat_consumption") val statConsumption: String,
    val name: String? = null,
    @SerialName("included_in_stat") val includedInStat: String? = null,
    @SerialName("stat_rate") val statRate: String? = null,
    @SerialName("stat_power") val statPower: String? = null,
) {
    val powerSensor: String? get() = statRate ?: statPower
}

/** `media_player/browse_media`. */
@Serializable
data class BrowseMediaResult(@Serializable(with = BrowseChildren::class) val children: List<BrowseChild> = emptyList())

@Serializable
data class BrowseChild(
    val title: String,
    @SerialName("media_content_id") val mediaContentId: String,
    @SerialName("media_content_type") val mediaContentType: String? = null,
    @SerialName("can_play") val canPlay: Boolean = false,
    @SerialName("can_expand") val canExpand: Boolean = false,
    val thumbnail: String? = null,
)

/** `frontend/get_translations`: flat keys to text. */
@Serializable
data class TranslationsResult(val resources: Map<String, JsonPrimitive> = emptyMap()) {
    val texts: Map<String, String> get() = resources.mapValues { it.value.content }
}

@Serializable
data class CameraStreamResult(val url: String? = null)

internal object DisplayEntityEntries : SkippingListSerializer<DisplayEntityEntry>(DisplayEntityEntry.serializer())

internal object EnergySources : SkippingListSerializer<EnergySource>(EnergySource.serializer())

internal object EnergyDevices : SkippingListSerializer<EnergyDevice>(EnergyDevice.serializer())

internal object BrowseChildren : SkippingListSerializer<BrowseChild>(BrowseChild.serializer())

internal val HISTORY_RESULT: KSerializer<Map<String, List<HistoryRow>>> =
    MapSerializer(String.serializer(), SkippingListSerializer(HistoryRow.serializer()))

internal val STATISTICS_RESULT: KSerializer<Map<String, List<StatisticRow>>> =
    MapSerializer(String.serializer(), SkippingListSerializer(StatisticRow.serializer()))

internal val FOSSIL_RESULT: KSerializer<Map<String, Double?>> =
    MapSerializer(String.serializer(), Double.serializer().nullable)
