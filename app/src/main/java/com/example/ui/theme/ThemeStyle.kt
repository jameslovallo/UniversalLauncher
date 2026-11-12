package com.example.ui.theme

/**
 * How the app derives its Material color scheme.
 *
 * [WALLPAPER] follows the system wallpaper (Material You on Android 12+, or a scheme
 * derived from the wallpaper color on older devices). Every other entry is a predefined
 * Material palette that stays the same regardless of the wallpaper.
 */
enum class ThemeStyle(
    val label: String,
    val description: String,
    /** Seed color used to build the palette, or null for the wallpaper-derived style. */
    val seedColor: Long?
) {
    WALLPAPER("Wallpaper", "Match your wallpaper", null),
    MONOCHROME("Monochrome", "Black, white and grey", 0xFF8E8E93),
    INDIGO("Indigo", "Material default", 0xFF6750A4),
    BLUE("Blue", "Cool coastal blue", 0xFF0B57D0),
    TEAL("Teal", "Balanced teal", 0xFF006A6A),
    GREEN("Green", "Fresh green", 0xFF386A20),
    AMBER("Amber", "Warm amber", 0xFF8B5000),
    ROSE("Rose", "Soft rose", 0xFF984061);

    companion object {
        /**
         * Reads a saved preference. Older builds stored `NEUTRAL`; map it to the
         * monochrome palette instead of silently falling back to the wallpaper.
         */
        fun fromStorage(value: String?): ThemeStyle = when (value) {
            null -> WALLPAPER
            "NEUTRAL" -> MONOCHROME
            else -> runCatching { valueOf(value) }.getOrDefault(WALLPAPER)
        }
    }
}
