package com.example

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HomeOverrideServiceTest {

    @Test
    fun buildHomeLaunchIntentTargetsMainActivityWithTaskResetFlags() {
        val context = RuntimeEnvironment.getApplication()
        val intent = HomeOverrideService.buildHomeLaunchIntent(context)

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        val expectedFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(expectedFlags, intent.flags and expectedFlags)
    }

    @Test
    fun shouldIgnoreLauncherWindowClassFiltersRecentsAndSetupWindowsOnly() {
        assertTrue(HomeOverrideService.shouldIgnoreLauncherWindowClass("com.android.quickstep.RecentsActivity"))
        assertTrue(HomeOverrideService.shouldIgnoreLauncherWindowClass("com.android.settings.FallbackHome"))
        assertTrue(HomeOverrideService.shouldIgnoreLauncherWindowClass("com.android.wallpaper.PickerActivity"))

        assertFalse(HomeOverrideService.shouldIgnoreLauncherWindowClass("com.android.launcher3.uioverrides.QuickstepLauncher"))
        assertFalse(HomeOverrideService.shouldIgnoreLauncherWindowClass("com.amazon.tv.launcher.ui.HomeActivity"))
        assertFalse(HomeOverrideService.shouldIgnoreLauncherWindowClass(""))
    }
}
