package com.example.ui.components

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.model.GameItem
import com.example.data.service.TemperatureUnit
import com.example.data.service.WeatherInfo
import com.example.data.service.temperatureDisplay
import com.example.ui.theme.FavoritesLayout
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Full-height favorites home shown on the Apps tab. Favorites are laid out in as many
 * columns as fit, in either the text-list or icon-grid style. Swiping up (handled by the
 * parent pager) reveals the complete app list.
 *
 * The [layout] decides whether favorites use inline icon+name rows (the default) or
 * large icon tiles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesHome(
    favorites: List<GameItem>,
    focusedItemId: String?,
    layout: FavoritesLayout,
    showFocusEffects: Boolean,
    weatherEnabled: Boolean = false,
    weather: WeatherInfo? = null,
    temperatureUnit: TemperatureUnit = TemperatureUnit.defaultForLocale(),
    firstItemFocusRequester: FocusRequester? = null,
    onLaunchFavorite: (GameItem) -> Unit,
    onLongPressFavorite: (GameItem) -> Unit,
    onOpenClock: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenWeather: () -> Unit = {},
    onPullDown: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusFavorite: (GameItem) -> Unit = {},
) {
    val isTextLayout = layout == FavoritesLayout.TEXT
    val contentAlignment = if (isTextLayout) Alignment.Start else Alignment.CenterHorizontally
    val textAlign = if (isTextLayout) TextAlign.Start else TextAlign.Center
    val favoritesSpacing = 10.dp
    // Inline rows add their gutters inside the clickable content, so the clock and date
    // need the same inset to line up with the icons.
    val textRowInset = 6.dp + favoritesSpacing / 2

    // The clock and date are hidden only on small screens in landscape (phones held
    // sideways); landscape tablets keep them because there is room.
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showClockAndDate = !isLandscape || configuration.screenHeightDp >= 480
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val is24Hour = remember { DateFormat.is24HourFormat(context) }
    val timeFormatter = remember(is24Hour) {
        SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", Locale.getDefault())
    }
    val dateFormatter = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    }
    val nowDate = Date(now)

    Column(
        modifier = modifier
            .fillMaxSize()
            // Before the padding, so the whole home screen is a pull-down surface.
            .pointerInput(onPullDown) {
                // A short downward drag on the home screen expands the notification
                // shade. Crucial: this uses Compose's touch-slop axis locking, so the
                // gesture is only claimed once it is clearly a downward drag. Horizontal
                // swipes and upward drags are left to the pagers.
                val triggerDistance = 64.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalPull = 0f

                    awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        val dy = change.position.y - down.position.y
                        val dx = change.position.x - down.position.x
                        // Claim only downward, vertically dominant drags. If anything
                        // else consumed the movement, the detector cancels itself.
                        if (dy > 0f && dy > abs(dx)) {
                            change.consume()
                            totalPull = overSlop
                        }
                    } ?: return@awaitEachGesture

                    var fired = false
                    if (totalPull >= triggerDistance) {
                        fired = true
                        onPullDown()
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        totalPull += change.position.y - change.previousPosition.y
                        change.consume()
                        if (!fired && totalPull >= triggerDistance) {
                            fired = true
                            onPullDown()
                        }
                    }
                }
            }
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = contentAlignment
    ) {
        if (showClockAndDate) {
            Text(
                text = timeFormatter.format(nowDate),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = textAlign,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpenClock() }
                    .padding(vertical = 4.dp)
                    .then(if (isTextLayout) Modifier.padding(start = textRowInset) else Modifier.padding(horizontal = 20.dp))
                    .testTag("favorites_clock")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isTextLayout) Arrangement.Start else Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenCalendar() }
                    .padding(vertical = 4.dp)
                    .then(if (isTextLayout) Modifier.padding(start = textRowInset) else Modifier.padding(horizontal = 14.dp))
                    .testTag("favorites_date_row")
            ) {
                Text(
                    text = dateFormatter.format(nowDate),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = textAlign,
                    modifier = Modifier.testTag("favorites_date")
                )

                if (weatherEnabled && weather != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    // Separate click target from the date row: tapping the conditions
                    // opens the user's weather app, while tapping the date opens Calendar.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenWeather() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("favorites_weather"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = weatherIcon(weather.weatherCode),
                            contentDescription = weatherDescription(weather.weatherCode),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = weather.temperatureDisplay(temperatureUnit, showUnit = false),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(34.dp))
        }

        if (favorites.isEmpty()) {
            // Keep the empty state aligned with the clock and date above it.
            val emptyInset = if (isTextLayout) {
                Modifier.padding(start = textRowInset)
            } else {
                Modifier.padding(horizontal = 14.dp)
            }
            Text(
                text = "No favorite apps yet",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth().then(emptyInset)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Press and hold an app to add it to favorites.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth().then(emptyInset)
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val spacing = favoritesSpacing

                if (isTextLayout) {
                    // Multiple columns of inline icon+name rows. A phone in portrait gets a
                    // single full-width column; wider screens add columns. Rows are packed
                    // edge to edge and the former gaps become part of each row's touch
                    // target, so the whole favorites area is interactive.
                    val minColumnWidth = 170.dp
                    val isCompactPortrait = !isLandscape && maxWidth < 600.dp
                    val columns = if (isCompactPortrait) {
                        1
                    } else {
                        maxOf(
                            2,
                            ((maxWidth + spacing) / (minColumnWidth + spacing)).toInt()
                        )
                    }
                    val rowGap = 2.dp
                    val itemWidth = maxWidth / columns

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("favorites_text_list"),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        maxItemsInEachRow = columns
                    ) {
                        favorites.forEachIndexed { index, app ->
                            AppTextRow(
                                app = app,
                                isFocused = app.id == focusedItemId,
                                showFocusEffects = showFocusEffects,
                                testTag = "favorite_app_${app.id}",
                                onClick = { onLaunchFavorite(app) },
                                onLongClick = { onLongPressFavorite(app) },
                                onFocus = { onFocusFavorite(app) },
                                contentPadding = PaddingValues(
                                    horizontal = 6.dp + spacing / 2,
                                    vertical = 9.dp + rowGap / 2
                                ),
                                modifier = Modifier
                                    .width(itemWidth)
                                    .then(
                                        if (index == 0) {
                                            firstItemFocusRequester?.let { Modifier.focusRequester(it) }
                                                ?: Modifier
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }
                    }
                } else {
                    // Icon tiles in as many columns as the screen width allows. Tiles are
                    // sized to the full column pitch so every pixel of the grid, including
                    // the visual spacing, belongs to a tile's clickable area.
                    val tileWidth = 78.dp
                    val tileGap = spacing
                    val rowGap = 14.dp
                    val tilePitch = tileWidth + tileGap
                    val columns = maxOf(
                        4,
                        ((maxWidth + tileGap) / tilePitch).toInt()
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            0.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        maxItemsInEachRow = columns
                    ) {
                        favorites.forEachIndexed { index, app ->
                            AppIconTile(
                                app = app,
                                isFocused = app.id == focusedItemId,
                                showFocusEffects = showFocusEffects,
                                testTag = "favorite_app_${app.id}",
                                onClick = { onLaunchFavorite(app) },
                                onLongClick = { onLongPressFavorite(app) },
                                onFocus = { onFocusFavorite(app) },
                                contentPadding = PaddingValues(
                                    horizontal = tileGap / 2,
                                    vertical = 6.dp + rowGap / 2
                                ),
                                modifier = Modifier
                                    .width(tilePitch)
                                    .then(
                                        if (index == 0) {
                                            firstItemFocusRequester?.let { Modifier.focusRequester(it) }
                                                ?: Modifier
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Long-press menu for a favorite: open it, edit the favorites order, or remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteActionsSheet(
    item: GameItem,
    onEditFavorites: () -> Unit,
    onRemoveFavorite: () -> Unit,
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
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(app = item, size = 50.dp)
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
                        text = "Favorite app",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(8.dp))

            FavoriteActionRow(
                icon = Icons.Default.Edit,
                title = "Edit favorites",
                onClick = onEditFavorites,
                testTag = "favorite_action_edit"
            )

            FavoriteActionRow(
                icon = Icons.Default.Favorite,
                title = "Remove from favorites",
                onClick = onRemoveFavorite,
                isDestructive = true,
                testTag = "favorite_action_remove"
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Bottom sheet where favorites are dragged into a new order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFavoritesSheet(
    favorites: List<GameItem>,
    onReorder: (List<GameItem>) -> Unit,
    onRemoveFavorite: (GameItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editable = remember {
        mutableStateListOf<GameItem>().apply { addAll(favorites) }
    }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

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
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Edit Favorites",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Press and hold a row, then drag it to reorder.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (editable.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No favorites to edit.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("edit_favorites_list"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(editable, key = { _, item -> item.id }) { index, item ->
                        val isDragging = index == draggingIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .pointerInput(item.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = editable.indexOfFirst { it.id == item.id }
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val from = draggingIndex
                                            if (from !in editable.indices) {
                                                return@detectDragGesturesAfterLongPress
                                            }

                                            dragOffset += dragAmount.y

                                            val currentInfo = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.index == from }
                                                ?: return@detectDragGesturesAfterLongPress
                                            val draggedCenter =
                                                currentInfo.offset + currentInfo.size / 2f + dragOffset

                                            val targetInfo = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { info ->
                                                    info.index != from &&
                                                        draggedCenter >= info.offset &&
                                                        draggedCenter <= info.offset + info.size
                                                }
                                                ?: return@detectDragGesturesAfterLongPress

                                            val to = targetInfo.index
                                            if (to in editable.indices) {
                                                val moved = editable.removeAt(from)
                                                editable.add(to, moved)
                                                draggingIndex = to
                                                dragOffset +=
                                                    (currentInfo.offset - targetInfo.offset).toFloat()
                                            }
                                        },
                                        onDragEnd = {
                                            draggingIndex = -1
                                            dragOffset = 0f
                                            onReorder(editable.toList())
                                        },
                                        onDragCancel = {
                                            draggingIndex = -1
                                            dragOffset = 0f
                                        }
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(app = item, size = 36.dp)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = item.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    editable.removeAll { it.id == item.id }
                                    onRemoveFavorite(item)
                                    onReorder(editable.toList())
                                },
                                modifier = Modifier.testTag("edit_favorites_remove_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Remove ${item.title} from favorites",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onReorder(editable.toList())
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_favorites_done")
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FavoriteActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isDestructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Material icon matching an Open-Meteo WMO weather code. */
internal fun weatherIcon(code: Int) = when (code) {
    0, 1 -> Icons.Default.WbSunny
    2, 3 -> Icons.Default.Cloud
    45, 48 -> Icons.Default.Cloud
    51, 53, 55, 56, 57 -> Icons.Default.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Default.Grain
    71, 73, 75, 77, 85, 86 -> Icons.Default.AcUnit
    95, 96, 99 -> Icons.Default.FlashOn
    else -> Icons.Default.Cloud
}

/** Short spoken description of an Open-Meteo WMO weather code. */
internal fun weatherDescription(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
    71, 73, 75, 77, 85, 86 -> "Snow"
    95, 96, 99 -> "Thunderstorm"
    else -> "Weather"
}
