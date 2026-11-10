package com.example.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

enum class AppIconShape(val label: String) {
    SQUIRCLE("Squircle"),
    CIRCLE("Circle"),
    ROUNDED_SQUARE("Rounded Square"),
    TEARDROP("Teardrop"),
    HEXAGON("Hexagon");

    fun toComposeShape(): Shape = when (this) {
        SQUIRCLE -> RoundedCornerShape(24.dp)
        CIRCLE -> CircleShape
        ROUNDED_SQUARE -> RoundedCornerShape(12.dp)
        TEARDROP -> RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 26.dp, bottomEnd = 4.dp)
        HEXAGON -> RoundedCornerShape(18.dp)
    }
}
