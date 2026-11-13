package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * Material Symbols `page_info` icon. The upstream vector uses a 960x960 viewport with the
 * artwork in negative Y space (viewBox "0 -960 960 960"), so the path is translated down
 * into Compose's 0-based viewport.
 */
val PageInfoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PageInfo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        group(translationY = 960f) {
            addPath(
                pathData = PathParser().parsePathString(
                    "M710-150q-63 0-106.5-43.5T560-300q0-63 43.5-106.5T710-450q63 0 " +
                        "106.5 43.5T860-300q0 63-43.5 106.5T710-150Zm0-80q29 0 49.5-20.5T780-300q0-29 " +
                        "-20.5-49.5T710-370q-29 0-49.5 20.5T640-300q0 29 20.5 49.5T710-230Zm-550-30v-80h320v80H160Z" +
                        "m90-250q-63 0-106.5-43.5T100-660q0-63 43.5-106.5T250-810q63 0 106.5 43.5T400-660q0 " +
                        "63-43.5 106.5T250-510Zm0-80q29 0 49.5-20.5T320-660q0-29-20.5-49.5T250-730q-29 " +
                        "0-49.5 20.5T180-660q0 29 20.5 49.5T250-590Zm230-30v-80h320v80H480Zm230 320ZM250-660Z"
                ).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }
    }.build()
}
