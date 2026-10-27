package tech.shaffersoftworks.remotescreens.player.data.kiosk

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KioskManagerTest {
    private val devicePolicyManager: DevicePolicyManager = mock()
    private val activityManager: ActivityManager = mock()
    private val context: Context = mock {
        on { packageName } doReturn PACKAGE
        on { getSystemService(DevicePolicyManager::class.java) } doReturn devicePolicyManager
        on { getSystemService(ActivityManager::class.java) } doReturn activityManager
    }

    @Test
    fun aDeviceOwnerGrantsRuntimePermissionsWithoutAPrompt() {
        whenever(devicePolicyManager.isDeviceOwnerApp(PACKAGE)).thenReturn(true)
        whenever(
            devicePolicyManager.setPermissionGrantState(any(), any(), any(), any()),
        ).thenReturn(true)

        manager().grantRuntimePermissions()

        for (permission in KioskManager.RUNTIME_PERMISSIONS) {
            verify(devicePolicyManager).setPermissionGrantState(
                any(),
                eq(PACKAGE),
                eq(permission),
                eq(DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED),
            )
        }
    }

    @Test
    fun aDeviceOwnerGrantsRuntimePermissionsWhenLockTaskStarts() {
        whenever(devicePolicyManager.isDeviceOwnerApp(PACKAGE)).thenReturn(true)
        whenever(
            devicePolicyManager.setPermissionGrantState(any(), any(), any(), any()),
        ).thenReturn(true)

        manager().startLockTaskMode(mock<Activity>())

        verify(devicePolicyManager).setPermissionGrantState(
            any(),
            eq(PACKAGE),
            eq(Manifest.permission.RECORD_AUDIO),
            eq(DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED),
        )
    }

    @Test
    fun aPinnedTabletDoesNotGrantPermissions() {
        whenever(devicePolicyManager.isDeviceOwnerApp(PACKAGE)).thenReturn(false)

        manager().grantRuntimePermissions()

        verify(devicePolicyManager, never()).setPermissionGrantState(any(), any(), any(), any())
    }

    private fun manager(): KioskManager = KioskManager(
        context,
        mock(),
        mock(),
        CoroutineScope(SupervisorJob()),
    )

    private companion object {
        const val PACKAGE = "tech.shaffersoftworks.remotescreens.player"
    }
}
