package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonGfnGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun ControllerActionBar(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x00080C14),
                        Color(0xEB080C14)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GuidePill(buttonLabel = "LB/RB", actionText = "Switch Category", buttonColor = ElectricCyan)
            GuidePill(buttonLabel = "D-PAD", actionText = "Navigate", buttonColor = Color.White)
            GuidePill(buttonLabel = "A", actionText = "Launch", buttonColor = NeonGfnGreen)
            GuidePill(buttonLabel = "X", actionText = "Customize Poster", buttonColor = ElectricCyan)
            GuidePill(buttonLabel = "Y", actionText = "Favorite", buttonColor = Color(0xFFFBBF24))
            GuidePill(buttonLabel = "B", actionText = "Back", buttonColor = Color(0xFFEF4444))
        }
    }
}

@Composable
private fun GuidePill(
    buttonLabel: String,
    actionText: String,
    buttonColor: Color = Color.White
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ControllerKeyBadge(label = buttonLabel, bgColor = Color(0xFF1E293B), textColor = buttonColor)
        Text(
            text = actionText,
            fontSize = 11.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}
