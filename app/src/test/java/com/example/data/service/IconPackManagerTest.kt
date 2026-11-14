package com.example.data.service

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Parsing rules for the `appfilter.xml` shipped by icon packs.
 *
 * Packs are inconsistent: newer ones use `package="..."`, older ones only expose
 * `component="ComponentInfo{pkg/activity}"`, and a few use `icon` instead of `drawable`.
 * All three forms have to resolve to the same `package -> drawable` mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconPackManagerTest {

    private fun parse(xml: String): IconPackManager.AppFilter {
        val parser = android.util.Xml.newPullParser()
        parser.setInput(StringReader(xml))
        return IconPackManager.parseAppFilter(parser)
    }

    @Test
    fun `package attribute maps to drawable`() {
        val filter = parse(
            """
            <resources>
                <item component="ComponentInfo{com.spotify.music/com.spotify.Main}" drawable="spotify" />
                <item package="com.google.android.youtube" drawable="youtube" />
            </resources>
            """.trimIndent()
        )

        assertEquals("spotify", filter.packages["com.spotify.music"])
        assertEquals("youtube", filter.packages["com.google.android.youtube"])
    }

    @Test
    fun `legacy icon attribute is accepted`() {
        val filter = parse(
            """
            <resources>
                <item component="ComponentInfo{com.example.app/com.example.app.Main}" icon="example_app" />
            </resources>
            """.trimIndent()
        )

        assertEquals("example_app", filter.packages["com.example.app"])
    }

    @Test
    fun `drawables include every entry once even when unused by a package`() {
        val filter = parse(
            """
            <resources>
                <item package="com.example.one" drawable="shared" />
                <item package="com.example.two" drawable="shared" />
                <item drawable="theme_only" />
                <item package="com.example.broken" />
            </resources>
            """.trimIndent()
        )

        assertEquals(listOf("shared", "theme_only"), filter.drawables)
        assertEquals(2, filter.packages.size)
    }
}
