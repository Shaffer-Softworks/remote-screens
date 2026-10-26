package tech.shaffersoftworks.remotescreens.player.data.device

/**
 * Rockchip's rk30board sensor HAL (the PX30 office keypad) advertises a binary proximity
 * range of 9 cm, then reports the raw `0`/`1` index instead of centimetres. On the STK3A5X,
 * `0` is far and `1` is near, so `distance < maximumRange` is true for both and the panel
 * looks occupied the whole time. A sensor that really does report 9 cm for far proves it by
 * emitting that value; until then a 9 cm binary sensor is read as the index.
 */
internal class ProximityScale {
    private var reportsCentimetres: Boolean? = null

    fun isNear(distance: Float, maximumRange: Float, resolution: Float): Boolean {
        if (isRockchipBinaryTemplate(maximumRange, resolution)) {
            if (distance >= maximumRange) {
                reportsCentimetres = true
            } else if (distance > 0f) {
                reportsCentimetres = false
            }
            if (reportsCentimetres != true) {
                return distance > 0f
            }
        }
        return distance < maximumRange
    }

    private fun isRockchipBinaryTemplate(maximumRange: Float, resolution: Float): Boolean =
        maximumRange == ROCKCHIP_BINARY_RANGE_CM && resolution == ROCKCHIP_BINARY_RANGE_CM

    private companion object {
        const val ROCKCHIP_BINARY_RANGE_CM = 9f
    }
}
