package tech.shaffersoftworks.remotescreens.server.render

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Material Design Icons, the icon set Home Assistant uses, as SVG path data on a 24 by 24 grid. */
object Icons {
    private val data by lazy {
        val stream = checkNotNull(
            Icons::class.java.getResourceAsStream("/mdi.json"),
        ) { "mdi.json is not bundled" }
        val text = stream.bufferedReader().use { it.readText() }
        val root = Json.parseToJsonElement(text).jsonObject
        checkNotNull(
            root["icons"],
        ) { "mdi.json has no icons" }.jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    const val FALLBACK = "help-circle-outline"

    /** `mdi:lightbulb-on`, `lightbulb-on` and `mdiLightbulbOn` all find the same icon. */
    fun key(name: String): String = name.removePrefix(
        "mdi:",
    ).removePrefix("mdi").replace("-", "").lowercase()

    fun path(name: String?): String = name?.let { data[key(it)] } ?: data.getValue(key(FALLBACK))

    fun exists(name: String): Boolean = data.containsKey(key(name))

    fun search(query: String, limit: Int = 60): List<String> {
        val q = key(query)
        return data.keys.asSequence().filter { q in it }.take(limit).toList()
    }
}
