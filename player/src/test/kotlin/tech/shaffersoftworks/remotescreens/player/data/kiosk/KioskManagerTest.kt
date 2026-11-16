package tech.shaffersoftworks.remotescreens.player.data.kiosk

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import tech.shaffersoftworks.remotescreens.player.data.device.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KioskManagerTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val devicePolicyManager: DevicePolicyManager = mock()
    private val activityManager: ActivityManager = mock()
    private val deviceRepository: DeviceRepository = mock {
        on { disableSystemAutoRotate(any()) } doReturn true
    }
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
        verify(devicePolicyManager, never()).setPermissionGrantState(any(), any(), any(), any())
        scheduler.runCurrent()

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

        manager().startLockTaskMode(activity())
        scheduler.runCurrent()

        verify(devicePolicyManager).setPermissionGrantState(
            any(),
            eq(PACKAGE),
            eq(Manifest.permission.RECORD_AUDIO),
            eq(DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED),
        )
    }

    @Test
    fun lockTaskDisablesSystemAutoRotateBeforeStarting() {
        whenever(devicePolicyManager.isDeviceOwnerApp(PACKAGE)).thenReturn(false)

        manager().startLockTaskMode(activity(rotation = Surface.ROTATION_90))

        verify(deviceRepository).disableSystemAutoRotate(Surface.ROTATION_90)
    }

    @Test
    fun aPinnedTabletDoesNotGrantPermissions() {
        whenever(devicePolicyManager.isDeviceOwnerApp(PACKAGE)).thenReturn(false)

        manager().grantRuntimePermissions()
        verify(devicePolicyManager, never()).setPermissionGrantState(any(), any(), any(), any())
        scheduler.runCurrent()

        verify(devicePolicyManager, never()).setPermissionGrantState(any(), any(), any(), any())
    }

    @Test
    fun alreadyGrantedPoliciesAreNotGrantedAgain() {
        whenever(devicePolicyManager.isDeviceOwnerApp(PACKAGE)).thenReturn(true)
        whenever(devicePolicyManager.getPermissionGrantState(any(), any(), any()))
            .thenReturn(DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)

        manager().grantRuntimePermissions()
        scheduler.runCurrent()

        verify(devicePolicyManager, never()).setPermissionGrantState(any(), any(), any(), any())
    }

    private fun manager(): KioskManager = KioskManager(
        context,
        mock(),
        deviceRepository,
        CoroutineScope(SupervisorJob() + dispatcher),
    )

    private fun activity(rotation: Int = Surface.ROTATION_0): Activity {
        val display: Display = mock {
            on { getRotation() } doReturn rotation
        }
        val windowManager: WindowManager = mock {
            on { defaultDisplay } doReturn display
        }
        return mock {
            on { getWindowManager() } doReturn windowManager
        }
    }

    private companion object {
        const val PACKAGE = "tech.shaffersoftworks.remotescreens.player"
    }
}
