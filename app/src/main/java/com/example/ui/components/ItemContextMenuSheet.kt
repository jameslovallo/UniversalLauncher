package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemContextMenuSheet(
    item: GameItem,
    showNsfwPoster: Boolean = false,
    canDelete: Boolean = true,
    onCustomize: () -> Unit,
    onMove: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveFromRecents: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            ContextMenuHeaderBar(
                item = item,
                subtitle = item.displayCategory,
                showNsfwPoster = showNsfwPoster,
                onCustomize = onCustomize,
                onOpenAppInfo = onOpenAppInfo
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(8.dp))

            // Action 2: Move to Apps / Move to Games
            if (item.isGame) {
                ContextMenuItemRow(
                    icon = Icons.Default.Apps,
                    title = "Move to Apps",
                    onClick = onMove,
                    testTag = "context_menu_move_to_apps"
                )
            } else {
                ContextMenuItemRow(
                    icon = Icons.Default.SportsEsports,
                    title = "Move to Games",
                    onClick = onMove,
                    testTag = "context_menu_move_to_games"
                )
            }

            // Action 3: Favorite toggle (apps only; games are ordered by recency)
            if (!item.isGame) {
                ContextMenuItemRow(
                    icon = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    title = if (item.isFavorite) "Remove from favorites" else "Add to favorites",
                    onClick = onToggleFavorite,
                    testTag = "context_menu_favorite"
                )
            }

            // Games at the top of the grid are the recently played ones; let the user
            // drop one back into the alphabetical tail without deleting it.
            if (item.isGame && item.lastPlayedTimestamp > 0L) {
                ContextMenuItemRow(
                    icon = Icons.Default.History,
                    title = "Remove from recents",
                    onClick = onRemoveFromRecents,
                    testTag = "context_menu_remove_recent"
                )
            }

            // Action 5: Hide from the library (restorable from Settings)
            ContextMenuItemRow(
                icon = Icons.Default.VisibilityOff,
                title = "Hide from library",
                onClick = onHide,
                testTag = "context_menu_hide"
            )

            if (canDelete) {
                // Explicit destructive action: uninstall a native app, delete a shortcut, or
                // remove a game tile from the library. Hidden for protected/system apps.
                val isNativeApp = item.type == GameType.INSTALLED_APP &&
                    !item.isGame && !item.packageName.isNullOrBlank()
                val deleteLabel = when {
                    isNativeApp -> "Uninstall app"
                    item.isGame -> "Remove from library"
                    else -> "Delete shortcut"
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("context_menu_delete")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = deleteLabel,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Shared top customization bar for context menus: a clickable 54.dp pill preview that opens
 * customization, paired with an App Info icon button for installed apps.
 */
@Composable
internal fun ContextMenuHeaderBar(
    item: GameItem,
    subtitle: String,
    showNsfwPoster: Boolean = false,
    onCustomize: () -> Unit,
    onOpenAppInfo: () -> Unit,
    headerTestTag: String = "context_menu_header",
    appInfoTestTag: String = "context_menu_app_info"
) {
    // The header pill stays transparent until it is pressed or focused with a controller.
    val headerInteractionSource = remember { MutableInteractionSource() }
    val headerPressed by headerInteractionSource.collectIsPressedAsState()
    val headerFocused by headerInteractionSource.collectIsFocusedAsState()
    val headerBackground by animateColorAsState(
        targetValue = when {
            headerPressed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            headerFocused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else -> Color.Transparent
        },
        label = "context_menu_header_background"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(CircleShape)
                .background(headerBackground)
                .clickable(
                    interactionSource = headerInteractionSource,
                    indication = null,
                    onClick = onCustomize
                )
                .padding(end = 16.dp)
                .testTag(headerTestTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val activePoster = if (showNsfwPoster && !item.nsfwPosterUrl.isNullOrBlank()) {
                item.nsfwPosterUrl
            } else {
                item.posterUrl
            }
            val previewImage = activePoster ?: item.heroBannerUrl ?: item.iconUrl
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!previewImage.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(previewImage)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = if (item.isGame) Icons.Default.SportsEsports else Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            if (item.type == GameType.INSTALLED_APP && item.packageName != null) {
                IconButton(
                    onClick = onOpenAppInfo,
                    modifier = Modifier.testTag(appInfoTestTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Open app info in system settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItemRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isDestructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
