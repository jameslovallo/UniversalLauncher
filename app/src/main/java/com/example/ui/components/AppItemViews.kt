package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.GameItem

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
    modifier: Modifier = Modifier
) {
    val focused = isFocused && showFocusEffects

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(21.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
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
    modifier: Modifier = Modifier
) {
    val focused = isFocused && showFocusEffects
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(150),
        label = "app_icon_scale"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
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

@Composable
fun AppIcon(app: GameItem, size: Dp) {
    val context = LocalContext.current
    val imageUrl = app.iconUrl ?: app.posterUrl
    // Broken URLs (e.g. icons saved before icons were copied to local files) fall back to
    // the initial letter instead of leaving an empty slot.
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }

    if (!imageUrl.isNullOrBlank() && !imageFailed) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { imageFailed = true },
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Text(
            text = app.title.trim().take(1).uppercase().ifEmpty { "?" },
            fontSize = (size.value / 2.6f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
