package com.example.ui.theme

/**
 * Material You / Monet color palettes matching those offered on Google Pixel devices.
 *
 * [WALLPAPER] dynamically derives colors from the system wallpaper (Android 12+ Monet).
 * The remaining styles represent Pixel's official "Basic colors" tonal palettes.
 */
enum class ThemeStyle(
    val label: String,
    val description: String,
    /** Seed color used to build the palette, or null for the wallpaper-derived style. */
    val seedColor: Long?
) {
    WALLPAPER("Wallpaper", "Dynamic colors from wallpaper", null),
    MONOCHROME("Monochrome", "Pixel neutral greyscale", 0xFF76777A),
    OCEAN("Ocean Blue", "Pixel ocean blue", 0xFF3871E0),
    FOREST("Forest Green", "Pixel fresh forest", 0xFF2E7D52),
    LAVENDER("Lavender", "Pixel soft violet", 0xFF7C5295),
    CORAL("Coral", "Pixel flamingo & terra cotta", 0xFFBA4336),
    SAND("Sand", "Pixel warm sunny amber", 0xFF8C6500),
    AQUA("Aqua", "Pixel cool turquoise", 0xFF006874);

    companion object {
        /**
         * Reads a saved preference. Maps older values to matching Pixel Monet palettes
         * instead of silently falling back to wallpaper.
         */
        fun fromStorage(value: String?): ThemeStyle = when (value) {
            null -> WALLPAPER
            "NEUTRAL", "MONOCHROME" -> MONOCHROME
            "BLUE", "INDIGO", "OCEAN" -> OCEAN
            "GREEN", "FOREST" -> FOREST
            "LAVENDER" -> LAVENDER
            "CORAL", "ROSE" -> CORAL
            "AMBER", "SAND" -> SAND
            "TEAL", "AQUA" -> AQUA
            else -> runCatching { valueOf(value) }.getOrDefault(WALLPAPER)
        }
    }
}
