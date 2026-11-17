package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesRowsTest {

    @Test
    fun `more than five favorites split evenly across two rows`() {
        assertEquals(listOf(3, 3), balancedFavoriteRows(6, 5))
        assertEquals(listOf(4, 3), balancedFavoriteRows(7, 5))
        assertEquals(listOf(4, 4), balancedFavoriteRows(8, 5))
        assertEquals(listOf(5, 4), balancedFavoriteRows(9, 5))
        assertEquals(listOf(5, 5), balancedFavoriteRows(10, 5))
    }

    @Test
    fun `rows never exceed the configured column limit`() {
        assertEquals(listOf(3, 2, 2), balancedFavoriteRows(7, 3))
        assertEquals(listOf(4, 4, 3), balancedFavoriteRows(11, 5))
        assertTrue(balancedFavoriteRows(23, 4).all { it <= 4 })
    }

    @Test
    fun `small favorite counts stay on one row`() {
        assertEquals(listOf(1), balancedFavoriteRows(1, 5))
        assertEquals(listOf(5), balancedFavoriteRows(5, 5))
        assertEquals(emptyList<Int>(), balancedFavoriteRows(0, 5))
    }
}
