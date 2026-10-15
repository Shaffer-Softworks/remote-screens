package tech.shaffersoftworks.remotescreens.player.data.device

/** The size of the whole window in dp, without the hidden system bars, and whether the system is dark. */
data class DisplayInfo(
    val widthDp: Int = 0,
    val heightDp: Int = 0,
    val density: Float = 1f,
    val isSystemDark: Boolean = false,
) {
    fun hasSameSizeAs(other: DisplayInfo): Boolean =
        widthDp == other.widthDp && heightDp == other.heightDp && density == other.density
}
