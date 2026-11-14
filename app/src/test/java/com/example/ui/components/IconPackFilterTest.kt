package com.example.ui.components

import com.example.data.service.IconPackIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class IconPackFilterTest {

    private val icons = listOf(
        IconPackIcon("com_spotify_music", "Com Spotify Music"),
        IconPackIcon("youtube", "Youtube"),
        IconPackIcon("netflix", "Netflix"),
        IconPackIcon("google_maps", "Google Maps")
    )

    @Test
    fun `blank query keeps every icon`() {
        assertEquals(icons, filterIconPackIcons(icons, ""))
        assertEquals(icons, filterIconPackIcons(icons, "   "))
    }

    @Test
    fun `matches display name case insensitively`() {
        assertEquals(
            listOf("netflix"),
            filterIconPackIcons(icons, "NETFLIX").map { it.drawableName }
        )
    }

    @Test
    fun `matches raw drawable name`() {
        assertEquals(
            listOf("com_spotify_music"),
            filterIconPackIcons(icons, "spotify").map { it.drawableName }
        )
    }

    @Test
    fun `query is trimmed before matching`() {
        assertEquals(
            listOf("netflix"),
            filterIconPackIcons(icons, "  net  ").map { it.drawableName }
        )
    }

    @Test
    fun `no match returns an empty list`() {
        assertEquals(emptyList<IconPackIcon>(), filterIconPackIcons(icons, "tiktok"))
    }
}
