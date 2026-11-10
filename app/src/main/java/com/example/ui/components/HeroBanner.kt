package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonGfnGreen
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

@Composable
fun HeroBanner(
    game: GameItem?,
    onPlay: () -> Unit,
    onCustomizeArtwork: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(VoidBlack)
    ) {
        if (game != null) {
            val backdropUrl = game.heroBannerUrl ?: game.posterUrl

            if (!backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(backdropUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${game.title} hero artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Cinematic vignettes: Darken top for TopBar, darken left for text, darken bottom for grid blend
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to Color(0xF2080C14),
                            0.45f to Color(0xCC080C14),
                            1.0f to Color(0x33080C14)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0x80080C14),
                            0.7f to Color.Transparent,
                            1.0f to VoidBlack
                        )
                    )
            )

            // Content Info
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Platform Tag & Genre
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (game.type) {
                                    GameType.GEFORCE_NOW -> NeonGfnGreen
                                    GameType.INSTALLED_APP -> Color(0xFF3B82F6)
                                    GameType.SHORTCUT -> Color(0xFF8B5CF6)
                                    GameType.CUSTOM_CLOUD -> ElectricCyan
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = game.displayCategory.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }

                    if (game.publisher.isNotBlank()) {
                        Text(
                            text = "•  ${game.publisher}",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "•  ${game.genre}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Game Title
                Text(
                    text = game.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Description
                if (game.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = game.description,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.75f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons with controller shortcut badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play Button [A]
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGfnGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("hero_play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (game.type == GameType.GEFORCE_NOW) "Stream on GFN" else "Launch",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ControllerKeyBadge(label = "A", bgColor = Color(0x33000000), textColor = Color.Black)
                    }

                    // Customize Artwork [X]
                    OutlinedButton(
                        onClick = onCustomizeArtwork,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = Brush.linearGradient(listOf(Color(0xFF475569), Color(0xFF334155)))),
                        modifier = Modifier.testTag("hero_customize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Customize Artwork",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Artwork",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ControllerKeyBadge(label = "X", bgColor = Color(0xFF1E293B), textColor = ElectricCyan)
                    }

                    // Favorite Button [Y]
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = Brush.linearGradient(listOf(Color(0xFF475569), Color(0xFF334155)))),
                        modifier = Modifier.testTag("hero_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (game.isFavorite) Color(0xFFEF4444) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (game.isFavorite) "Favorited" else "Favorite",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ControllerKeyBadge(label = "Y", bgColor = Color(0xFF1E293B), textColor = Color(0xFFFBBF24))
                    }
                }
            }
        }
    }
}

@Composable
fun ControllerKeyBadge(
    label: String,
    bgColor: Color = Color(0xFF1E293B),
    textColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(0.5.dp, textColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = textColor
        )
    }
}
