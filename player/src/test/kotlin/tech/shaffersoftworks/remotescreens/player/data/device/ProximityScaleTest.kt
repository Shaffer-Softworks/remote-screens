package tech.shaffersoftworks.remotescreens.player.data.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityScaleTest {
    @Test
    fun `px30 index 0 is far and 1 is near`() {
        val scale = ProximityScale()

        assertFalse(scale.isNear(distance = 0f, maximumRange = ROCKCHIP_RANGE_CM, resolution = ROCKCHIP_RANGE_CM))
        assertTrue(scale.isNear(distance = 1f, maximumRange = ROCKCHIP_RANGE_CM, resolution = ROCKCHIP_RANGE_CM))
        assertFalse(scale.isNear(distance = 0f, maximumRange = ROCKCHIP_RANGE_CM, resolution = ROCKCHIP_RANGE_CM))
    }

    @Test
    fun `a 9 cm sensor that reports its range stays on the android comparison`() {
        val scale = ProximityScale()

        assertFalse(scale.isNear(distance = 9f, maximumRange = ROCKCHIP_RANGE_CM, resolution = ROCKCHIP_RANGE_CM))
        assertTrue(scale.isNear(distance = 0f, maximumRange = ROCKCHIP_RANGE_CM, resolution = ROCKCHIP_RANGE_CM))
    }

    @Test
    fun `other binary sensors keep near below the advertised range`() {
        val scale = ProximityScale()

        assertTrue(scale.isNear(distance = 0f, maximumRange = PHONE_RANGE_CM, resolution = PHONE_RANGE_CM))
        assertFalse(scale.isNear(distance = PHONE_RANGE_CM, maximumRange = PHONE_RANGE_CM, resolution = PHONE_RANGE_CM))
        assertTrue(scale.isNear(distance = 0f, maximumRange = 1f, resolution = 1f))
        assertFalse(scale.isNear(distance = 1f, maximumRange = 1f, resolution = 1f))
    }

    private companion object {
        const val ROCKCHIP_RANGE_CM = 9f
        const val PHONE_RANGE_CM = 5f
    }
}
