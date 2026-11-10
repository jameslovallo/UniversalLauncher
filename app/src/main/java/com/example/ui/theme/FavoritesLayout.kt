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
    )
}
