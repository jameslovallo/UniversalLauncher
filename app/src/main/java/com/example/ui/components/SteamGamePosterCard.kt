package com.example.ui.components

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlin.math.roundToInt

private val PosterCardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SteamGamePosterCard(
    game: GameItem,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showFocusEffects: Boolean = true,
    showFocusOutline: Boolean = true,
    showNsfwPoster: Boolean = false,
    posterWidthPx: Int = 0,
    posterHeightPx: Int = 0,
    onFocus: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    // In touch mode (!showFocusEffects), avoid allocating per-card focus/animation coroutines.
    val interactionSource: MutableInteractionSource?
    val effectivelyFocused: Boolean
    val focusScaleModifier: Modifier
    if (showFocusEffects) {
        val focusSource = remember { MutableInteractionSource() }
        val isFocusedByCompose by focusSource.collectIsFocusedAsState()
        effectivelyFocused = isFocused || isFocusedByCompose
        val scale by animateFloatAsState(
            targetValue = if (effectivelyFocused) 1.05f else 1.0f,
            animationSpec = tween(150),
            label = "poster_scale"
        )
        interactionSource = focusSource
        focusScaleModifier = Modifier
            .graphicsLayer {
                if (scale != 1f) {
                    scaleX = scale
                    scaleY = scale
                }
            }
            .then(
                if (onFocus != null) {
                    Modifier.onFocusChanged { focusState ->
                        if (focusState.isFocused) onFocus()
                    }
                } else {
                    Modifier
                }
            )
    } else {
        interactionSource = null
        effectivelyFocused = false
        focusScaleModifier = Modifier
    }

    val borderWidth = when {
        effectivelyFocused && showFocusOutline -> 2.5.dp
        showFocusOutline -> 1.dp
        else -> 0.dp
    }
    val borderColor = when {
        !showFocusOutline -> Color.Transparent
        effectivelyFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(focusScaleModifier)
            .tvClickableWithLongPress(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                hasIndicationParam = interactionSource != null
            )
            .testTag("game_poster_${game.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Steam Poster Aspect Ratio (2:3). Lightweight Box avoids Material Card's Surface overhead
        // during scroll and only casts a shadow when focused.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(
                    if (effectivelyFocused) {
                        Modifier.shadow(elevation = 8.dp, shape = PosterCardShape, clip = false)
                    } else {
                        Modifier
                    }
                )
                .clip(PosterCardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (borderWidth > 0.dp) {
                        Modifier.border(
                            width = borderWidth,
                            color = borderColor,
                            shape = PosterCardShape
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
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
                val posterRequest = remember(context, imageUrl, posterWidthPx, posterHeightPx) {
                    buildPosterImageRequest(
                        context = context,
                        imageUrl = imageUrl,
                        posterWidthPx = posterWidthPx,
                        posterHeightPx = posterHeightPx
                    )
                }
                AsyncImage(
                    model = posterRequest,
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
                    PosterLogoOverlay(
                        logoUrl = activeLogoUrl,
                        posterWidthPx = posterWidthPx,
                        posterHeightPx = posterHeightPx
                    )
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
                            GameType.AMAZON_LUNA -> Icons.Default.Cloud
                            GameType.INSTALLED_APP -> Icons.Default.SportsEsports
                            GameType.SHORTCUT -> Icons.Default.Link
                            GameType.CUSTOM_CLOUD -> Icons.Default.Cloud
                            GameType.MOONLIGHT -> Icons.Default.SportsEsports
                            GameType.FOLDER -> Icons.Default.SportsEsports
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

internal fun buildPosterImageRequest(
    context: Context,
    imageUrl: String,
    posterWidthPx: Int,
    posterHeightPx: Int
): ImageRequest = ImageRequest.Builder(context)
    .data(imageUrl)
    .apply {
        if (posterWidthPx > 0 && posterHeightPx > 0) {
            size(posterWidthPx, posterHeightPx)
            precision(Precision.EXACT)
        }
    }
    .allowRgb565(true)
    .crossfade(false)
    .build()

internal fun buildPosterLogoImageRequest(
    context: Context,
    logoUrl: String,
    posterWidthPx: Int,
    posterHeightPx: Int
): ImageRequest {
    val logoMaxWidthPx = (posterWidthPx * 0.80f).roundToInt()
    val logoMaxHeightPx = (posterHeightPx * 0.35f).roundToInt()
    return ImageRequest.Builder(context)
        .data(logoUrl)
        .apply {
            if (logoMaxWidthPx > 0 && logoMaxHeightPx > 0) {
                size(logoMaxWidthPx, logoMaxHeightPx)
                precision(Precision.INEXACT)
            }
        }
        .crossfade(false)
        .build()
}

@Composable
fun PosterLogoOverlay(
    logoUrl: String,
    modifier: Modifier = Modifier,
    posterWidthPx: Int = 0,
    posterHeightPx: Int = 0,
) {
    val context = LocalContext.current
    val logoRequest = remember(context, logoUrl, posterWidthPx, posterHeightPx) {
        buildPosterLogoImageRequest(
            context = context,
            logoUrl = logoUrl,
            posterWidthPx = posterWidthPx,
            posterHeightPx = posterHeightPx
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = logoRequest,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val parentWidth = constraints.maxWidth
                    val parentHeight = constraints.maxHeight
                    val maxW = (parentWidth * 0.80f).roundToInt().coerceAtLeast(0)
                    val maxH = (parentHeight * 0.35f).roundToInt().coerceAtLeast(0)
                    val bottomPad = (parentHeight * 0.10f).roundToInt()
                    val childConstraints = Constraints(
                        minWidth = maxW,
                        maxWidth = maxW,
                        minHeight = 0,
                        maxHeight = maxH
                    )
                    val placeable = measurable.measure(childConstraints)
                    val x = ((parentWidth - placeable.width) / 2).coerceAtLeast(0)
                    val y = (parentHeight - bottomPad - placeable.height).coerceAtLeast(0)
                    layout(parentWidth, parentHeight) {
                        placeable.placeRelative(x, y)
                    }
                }
        )
    }
}
