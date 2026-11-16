package com.example.ui.components

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.transform.CircleCropTransformation
import com.example.data.model.GameItem
import com.example.data.model.GameType

/**
 * `packageName` -> rendered icon file for the selected icon theme.
 *
 * Provided only around the Apps view, so a theme can never change artwork in the Games
 * poster grid even when the same package appears in both.
 */
val LocalThemedAppIcons = compositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * `folderId` -> ordered member apps for rendering geometric folder preview icons.
 */
val LocalFolderPreviewItems = compositionLocalOf<Map<String, List<GameItem>>> { emptyMap() }

/**
 * A single left-aligned app row. Shared by the favorites home and the complete app
 * list so both layouts stay visually identical. The whole row is the touch target;
 * [contentPadding] lets the caller grow that target into the gaps between rows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTextRow(
    app: GameItem,
    isFocused: Boolean,
    showFocusEffects: Boolean,
    testTag: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 9.dp),
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
) {
    val focusSource = if (showFocusEffects) remember { MutableInteractionSource() } else null
    val isFocusedByCompose = if (focusSource != null) focusSource.collectIsFocusedAsState().value else false
    val focused = showFocusEffects && (isFocused || isFocusedByCompose)
    val rowShape = remember { RoundedCornerShape(21.dp) }

    Row(
        modifier = modifier
            .then(
                if (focused) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        rowShape
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (showFocusEffects && onFocus != null) {
                    Modifier.onFocusChanged { if (it.isFocused) onFocus() }
                } else {
                    Modifier
                }
            )
            .tvClickableWithLongPress(
                interactionSource = focusSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                hasIndicationParam = focusSource != null
            )
            .padding(contentPadding)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(app = app, size = 39.dp)
        }

        Spacer(modifier = Modifier.width(18.dp))

        Text(
            text = app.title,
            fontSize = 18.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * A single app icon tile. Shared by the favorites grid and the icon-style app drawer.
 * There is no outline or backdrop behind the icon; focus only scales the tile when a
 * controller is connected. The tile fills whatever size the caller gives it, so the
 * whole grid cell is tappable rather than just the icon and label.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconTile(
    app: GameItem,
    isFocused: Boolean,
    showFocusEffects: Boolean,
    testTag: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(vertical = 6.dp),
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
) {
    val interactionSource: MutableInteractionSource?
    val focused: Boolean
    val scaleModifier: Modifier
    if (showFocusEffects) {
        val focusSource = remember { MutableInteractionSource() }
        val isFocusedByCompose by focusSource.collectIsFocusedAsState()
        focused = isFocused || isFocusedByCompose
        val scale by animateFloatAsState(
            targetValue = if (focused) 1.08f else 1f,
            animationSpec = tween(150),
            label = "app_icon_scale"
        )
        interactionSource = focusSource
        scaleModifier = Modifier
            .graphicsLayer {
                if (scale != 1f) {
                    scaleX = scale
                    scaleY = scale
                }
            }
            .then(
                if (onFocus != null) {
                    Modifier.onFocusChanged { if (it.isFocused) onFocus() }
                } else {
                    Modifier
                }
            )
    } else {
        interactionSource = null
        focused = false
        scaleModifier = Modifier
    }

    Column(
        modifier = modifier
            .then(scaleModifier)
            .tvClickableWithLongPress(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                hasIndicationParam = interactionSource != null
            )
            .padding(contentPadding)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(app = app, size = 58.dp)

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = app.title,
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val TvCardShape = RoundedCornerShape(16.dp)

/**
 * Shared 16:9 Android TV app card used identically across TV favorites, the complete app list,
 * and folder contents. Supports D-pad focus highlights and long-press Select context menus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTvCard(
    app: GameItem,
    isFocused: Boolean,
    showFocusEffects: Boolean,
    testTag: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
) {
    val focusSource = if (showFocusEffects) remember { MutableInteractionSource() } else null
    val isFocusedByCompose = if (focusSource != null) focusSource.collectIsFocusedAsState().value else false
    val focused = showFocusEffects && (isFocused || isFocusedByCompose)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "tv_app_card_scale"
    )

    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
    val topBackdrop = if (focused) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
    }
    val bottomBackdrop = if (focused) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                if (scale != 1f) {
                    scaleX = scale
                    scaleY = scale
                }
            }
            .then(
                if (focused) {
                    Modifier.shadow(elevation = 8.dp, shape = TvCardShape, clip = false)
                } else {
                    Modifier
                }
            )
            .clip(TvCardShape)
            .background(Brush.verticalGradient(listOf(topBackdrop, bottomBackdrop)))
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = borderColor,
                shape = TvCardShape
            )
            .then(
                if (onFocus != null) {
                    Modifier.onFocusChanged { if (it.isFocused) onFocus() }
                } else {
                    Modifier
                }
            )
            .tvClickableWithLongPress(
                interactionSource = focusSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                hasIndicationParam = focusSource != null
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        val iconSize = minOf(44.dp, maxHeight * 0.44f).coerceAtLeast(24.dp)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(app = app, size = iconSize)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = app.title,
                fontSize = 13.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                color = if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * True when [iconUrl] points to an explicit user-picked image or URL rather than the
 * auto-generated `app_icons` / `shortcuts` cache (or a legacy `custom_icons` file).
 */
fun hasCustomAppIconUrl(iconUrl: String?): Boolean =
    !iconUrl.isNullOrBlank() &&
        !iconUrl.contains("/app_icons/") &&
        !iconUrl.contains("/custom_icons/") &&
        !iconUrl.contains("/shortcuts/")

/**
 * Resolves the icon URL to display for a non-game item across every icon surface:
 * favorites, app list, context menu headers, hidden apps, and the customization sheet.
 */
fun resolveAppIconUrl(
    item: GameItem,
    themedIcons: Map<String, String>
): String? {
    val themedUrl = if (!item.isGame && !hasCustomAppIconUrl(item.iconUrl)) {
        themedIcons[item.id] ?: item.packageName?.let { themedIcons[it] }
    } else {
        null
    }
    return themedUrl ?: item.iconUrl ?: item.posterUrl
}

internal fun buildAppIconImageRequest(
    context: Context,
    imageUrl: String,
    sizePx: Int
): ImageRequest = ImageRequest.Builder(context)
    .data(imageUrl)
    .size(sizePx, sizePx)
    .precision(Precision.INEXACT)
    // Bake the circular mask into the cached bitmap once during decode instead of clipping on every draw frame.
    .transformations(CircleCropTransformation())
    .crossfade(false)
    .build()

@Composable
fun AppIcon(app: GameItem, size: Dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(density, size) {
        with(density) { size.roundToPx().coerceAtLeast(1) }
    }
    val themedIcons = LocalThemedAppIcons.current
    val imageUrl = resolveAppIconUrl(app, themedIcons)
    // Broken URLs (e.g. icons saved before icons were copied to local files) fall back to
    // the initial letter instead of leaving an empty slot.
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }

    if (!imageUrl.isNullOrBlank() && !imageFailed) {
        val request = remember(context, imageUrl, sizePx) {
            buildAppIconImageRequest(context, imageUrl, sizePx)
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { imageFailed = true },
            modifier = Modifier.size(size)
        )
    } else if (app.type == GameType.FOLDER) {
        FolderPreviewIcon(folderId = app.id, size = size)
    } else {
        Text(
            text = app.title.trim().take(1).uppercase().ifEmpty { "?" },
            fontSize = (size.value / 2.6f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Renders a circular folder icon of exact [size] containing up to four of the folder's first
 * non-folder child app icons arranged geometrically inside.
 */
@Composable
fun FolderPreviewIcon(
    folderId: String,
    size: Dp
) {
    val members = LocalFolderPreviewItems.current[folderId].orEmpty()
        .filter { it.type != GameType.FOLDER }
        .take(4)

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = CircleShape
            )
            .testTag("folder_preview_$folderId"),
        contentAlignment = Alignment.Center
    ) {
        if (members.isEmpty()) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.48f)
            )
        } else {
            val miniSize = size * 0.30f
            val gap = size * 0.06f
            val topRow = members.take(2)
            val bottomRow = members.drop(2).take(2)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topRow.forEach { child ->
                        AppIcon(app = child, size = miniSize)
                    }
                }
                if (bottomRow.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomRow.forEach { child ->
                            AppIcon(app = child, size = miniSize)
                        }
                    }
                }
            }
        }
    }
}
