package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.example.ui.theme.FocusRing
import com.example.ui.theme.NeonGfnGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextSecondary

@Composable
fun GamePosterCard(
    game: GameItem,
    isFocused: Boolean,
    onClick: () -> Unit,
    onCustomizeArtwork: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "card_scale"
    )

    val borderColor = if (isFocused) FocusRing else Color.Transparent
    val elevation = if (isFocused) 16.dp else 4.dp

    Column(
        modifier = modifier
            .width(150.dp)
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("game_poster_${game.id}"),
        horizontalAlignment = Alignment.Start
    ) {
        // Poster Box (Standard 2:3 vertical aspect ratio)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) FocusRing else Color(0xFF334155),
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val imageUrl = game.posterUrl ?: game.heroBannerUrl ?: game.iconUrl

                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${game.title} poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (game.type) {
                                GameType.GEFORCE_NOW -> Icons.Default.Cloud
                                GameType.INSTALLED_APP -> Icons.Default.PhoneAndroid
                                GameType.SHORTCUT -> Icons.Default.Link
                                GameType.CUSTOM_CLOUD -> Icons.Default.Cloud
                            },
                            contentDescription = null,
                            tint = NeonGfnGreen,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Gradient overlay at the bottom for readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.6f to Color.Transparent,
                                1.0f to Color(0xCC000000)
                            )
                        )
                )

                // Top Badges: Type and Favorite
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Type Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (game.type) {
                                    GameType.GEFORCE_NOW -> NeonGfnGreen.copy(alpha = 0.9f)
                                    GameType.INSTALLED_APP -> Color(0xFF3B82F6).copy(alpha = 0.9f)
                                    GameType.SHORTCUT -> Color(0xFF8B5CF6).copy(alpha = 0.9f)
                                    GameType.CUSTOM_CLOUD -> ElectricCyan.copy(alpha = 0.9f)
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (game.type) {
                                GameType.GEFORCE_NOW -> "GFN"
                                GameType.INSTALLED_APP -> "APP"
                                GameType.SHORTCUT -> "LINK"
                                GameType.CUSTOM_CLOUD -> "CLOUD"
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }

                    // Favorite Button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onToggleFavorite() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (game.isFavorite) Color(0xFFEF4444) else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // If focused, show hover play & edit quick actions
                if (isFocused) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(FocusRing)
                                .clickable { onCustomizeArtwork() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Customize Artwork",
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title & Genre
        Text(
            text = game.title,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            color = if (isFocused) FocusRing else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = game.genre,
            fontSize = 10.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
