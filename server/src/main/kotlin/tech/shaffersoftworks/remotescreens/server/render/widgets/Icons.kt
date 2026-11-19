package tech.shaffersoftworks.remotescreens.server.render.widgets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Material Design Icons, the icon set Home Assistant uses, as SVG path data on a 24 by 24 grid. */
object Icons {
    private val data by lazy {
        val stream = checkNotNull(
            Icons::class.java.getResourceAsStream("/mdi.json"),
        ) { "mdi.json is not bundled" }

        val text = stream.bufferedReader().use { it.readText() }
        MDI_JSON.decodeFromString(MdiFile.serializer(), text).icons
    }

    /** `mdi.json`: the set's version and each icon's path data by name. */
    @Serializable
    private data class MdiFile(val icons: Map<String, String>)

    private val MDI_JSON = Json { ignoreUnknownKeys = true }

    const val FALLBACK = "help-circle-outline"

    /** `mdi:lightbulb-on`, `lightbulb-on` and `mdiLightbulbOn` all find the same icon. */
    fun key(name: String): String = name.removePrefix(
        "mdi:",
    ).removePrefix("mdi").replace("-", "").lowercase()

    fun path(name: String?): String = name?.let { data[key(it)] } ?: data.getValue(key(FALLBACK))

    fun exists(name: String): Boolean = data.containsKey(key(name))

    fun search(query: String, limit: Int = 60): List<String> {
        val queryKey = key(query)

        return data.keys.asSequence().filter { queryKey in it }.take(limit).toList()
    }
}
