package tech.shaffersoftworks.remotescreens.shared

/**
 * What a tile of each entity type can do. Never written by hand: `catalogue/<domain>.yaml` is the source, and
 * `tools/generate_catalogue.py` writes [Catalogue] here and `editor/src/generated/catalogue.ts` from it.
 */
data class TypeSpec(
    val domain: String,
    val name: String,
    val icon: String,
    val iconOn: String? = null,
    /** Tapping toggles between [onStates] and the rest. */
    val toggle: Boolean = false,
    val onStates: List<String> = emptyList(),
    /** What a tap does by default: `toggle`, `card` or `action` (runs [defaultVerb]). */
    val tap: String = "card",
    val defaultVerb: String? = null,
    /** The card a long press opens, or `none`. */
    val card: String = "none",
    val controls: List<String> = emptyList(),
    val sizes: List<String> = listOf("1x1"),
    val options: List<String> = emptyList(),
    val actions: Map<String, ActionSpec> = emptyMap(),
    val predict: Map<String, PredictSpec> = emptyMap(),
    /** For kinds that are not a domain: whether the tile still names an entity. */
    val needsEntity: Boolean = true,
)

/**
 * A verb a tile may send. [data] values may hold `$arg` (the action's argument, `$arg*2.55` and `$arg/100` scale it)
 * and `$code` (a code typed on the screen's keypad).
 */
data class ActionSpec(
    val service: String,
    val data: Map<String, Any> = emptyMap(),
    /** Asks for a second tap before it is sent. */
    val confirm: Boolean = false,
    /**
     * Where an argument of `+1` or `-0.5` counts from: an attribute, `state`, or either scaled (`volume_level*100`).
     * The result stays within the entity's `min`/`max` (`min_temp`/`max_temp` for climate).
     */
    val relative: String? = null,
)

/** What a verb will change, shown at once before Home Assistant answers. `!` flips on and off. */
data class PredictSpec(
    val state: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)
