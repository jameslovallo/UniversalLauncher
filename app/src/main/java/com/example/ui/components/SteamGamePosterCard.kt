package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.GameItem
import com.example.data.model.GameType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SteamGamePosterCard(
    game: GameItem,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showFocusOutline: Boolean = true,
    showNsfwPoster: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocusedByCompose by interactionSource.collectIsFocusedAsState()
    val effectivelyFocused = isFocused || isFocusedByCompose

    val scale by animateFloatAsState(
        targetValue = if (effectivelyFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "poster_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocus?.invoke()
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("game_poster_${game.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Steam Poster Aspect Ratio (2:3)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .border(
                    width = when {
                        effectivelyFocused && showFocusOutline -> 2.5.dp
                        showFocusOutline -> 1.dp
                        else -> 0.dp
                    },
                    color = when {
                        !showFocusOutline -> androidx.compose.ui.graphics.Color.Transparent
                        effectivelyFocused -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    },
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (effectivelyFocused) 8.dp else 2.dp
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val showingNsfwPoster = showNsfwPoster && !game.nsfwPosterUrl.isNullOrBlank()
                val activePoster = if (showingNsfwPoster) {
                    game.nsfwPosterUrl
                } else {
                    game.posterUrl
                }
                // The safe and adult posters carry independent logo overlays.
                val activeLogoUrl = if (showingNsfwPoster) game.nsfwLogoUrl else game.logoUrl
                val activeLogoOverlay = if (showingNsfwPoster) {
                    game.overlayNsfwLogo
                } else {
                    game.overlayLogo
                }
                val imageUrl = activePoster ?: game.heroBannerUrl ?: game.iconUrl
                // A broken or unreachable URL should fall back to the type icon rather
                // than leaving an empty card.
                var imageFailed by remember(imageUrl) { mutableStateOf(false) }

                if (!imageUrl.isNullOrBlank() && !imageFailed) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${game.title} poster",
                        contentScale = ContentScale.Crop,
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                    // Only composite the logo over real poster art, not the banner or
                    // icon fallbacks.
                    if (
                        activeLogoOverlay &&
                        !activeLogoUrl.isNullOrBlank() &&
                        !activePoster.isNullOrBlank()
                    ) {
                        PosterLogoOverlay(logoUrl = activeLogoUrl)
                    }
                } else {
                    // Fallback artistic card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (game.type) {
                                GameType.GEFORCE_NOW -> Icons.Default.Cloud
                                GameType.INSTALLED_APP -> Icons.Default.SportsEsports
                                GameType.SHORTCUT -> Icons.Default.Link
                                GameType.CUSTOM_CLOUD -> Icons.Default.Cloud
                                GameType.MOONLIGHT -> Icons.Default.SportsEsports
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PosterLogoOverlay(
    logoUrl: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = maxWidth * 0.10f
        val bottomPadding = maxHeight * 0.10f
        val maxLogoHeight = maxHeight * 0.35f

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(logoUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = bottomPadding
                )
                .fillMaxWidth()
                .heightIn(max = maxLogoHeight)
        )
    }
}
