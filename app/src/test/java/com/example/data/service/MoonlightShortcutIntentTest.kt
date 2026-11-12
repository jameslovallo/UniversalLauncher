package com.example.data.service

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoonlightShortcutIntentTest {

    private val pcUuid = "207d8f6d-5176-43fd-9ce5-990a254e7e76"

    @Test
    fun `rebuilds the trampoline intent from the shortcut id`() {
        val intent = buildMoonlightShortcutIntent(
            packageName = "com.limelight",
            shortcutId = pcUuid + "5517696",
            appName = "Portal 2"
        )

        requireNotNull(intent)
        assertEquals("com.limelight", intent.component?.packageName)
        assertEquals("com.limelight.ShortcutTrampoline", intent.component?.className)
        assertEquals(Intent.ACTION_DEFAULT, intent.action)
        assertEquals(pcUuid, intent.getStringExtra("UUID"))
        assertEquals("5517696", intent.getStringExtra("AppId"))
        assertEquals("Portal 2", intent.getStringExtra("AppName"))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `blank app name is omitted`() {
        val intent = buildMoonlightShortcutIntent("com.limelight", pcUuid + "42", appName = " ")

        requireNotNull(intent)
        assertFalse(intent.hasExtra("AppName"))
        assertEquals("42", intent.getStringExtra("AppId"))
    }

    @Test
    fun `bare pc shortcut is not a game`() {
        assertNull(buildMoonlightShortcutIntent("com.limelight", pcUuid))
    }

    @Test
    fun `non numeric app id is rejected`() {
        assertNull(buildMoonlightShortcutIntent("com.limelight", pcUuid + "Portal"))
    }

    @Test
    fun `unrelated shortcut id is rejected`() {
        assertNull(buildMoonlightShortcutIntent("com.limelight", "not-a-moonlight-shortcut"))
    }
}
