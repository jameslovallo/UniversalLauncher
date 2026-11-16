package com.example.ui.theme

/** How the favorites home lays out the clock, date, and favorite apps. */
enum class FavoritesLayout(val label: String, val description: String) {
    TEXT(
        label = "Text list",
        description = "Small icon inline with each app name, left aligned"
    ),
    ICONS(
        label = "Icon grid",
        description = "Large centered icons with labels"
    ),
    TV_CARDS(
        label = "TV 16:9 cards",
        description = "16:9 widescreen cards sized for television remotes"
    );

    companion object {
        fun mobileOptions(): List<FavoritesLayout> = listOf(TEXT, ICONS)
    }
}

/**
 * TV devices always use [FavoritesLayout.TV_CARDS] exclusively; phones and tablets only use
 * [FavoritesLayout.TEXT] or [FavoritesLayout.ICONS].
 */
fun resolveEffectiveFavoritesLayout(
    storedLayout: FavoritesLayout,
    isTelevision: Boolean
): FavoritesLayout = when {
    isTelevision -> FavoritesLayout.TV_CARDS
    storedLayout == FavoritesLayout.TV_CARDS -> FavoritesLayout.TEXT
    else -> storedLayout
}

