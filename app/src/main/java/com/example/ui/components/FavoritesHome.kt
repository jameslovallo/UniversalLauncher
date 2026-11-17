package com.example.ui.components

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.example.data.model.GameType
import com.example.data.service.TemperatureUnit
import com.example.data.service.WeatherInfo
import com.example.data.service.temperatureDisplay
import com.example.ui.theme.FavoritesLayout
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal const val TV_FAVORITES_MAX_COLUMNS = 5
internal const val TV_FAVORITES_MAX_ROWS = 3

/**
 * Splits [itemCount] favorites into rows that are as even as possible while never exceeding
 * [columns] per row. Extra items go to the earlier rows, so an odd two-row split puts one more
 * card on the top row and the narrower row sits underneath it.
 *
 * With the TV maximum of five columns, 6–10 favorites become two centered rows (3/3, 4/3,
 * 4/4, 5/4, 5/5) instead of a full row followed by a nearly empty one.
 */
internal fun balancedFavoriteRows(itemCount: Int, columns: Int): List<Int> {
    if (itemCount <= 0 || columns <= 0) return emptyList()
    val rowCount = (itemCount + columns - 1) / columns
    val base = itemCount / rowCount
    val extra = itemCount % rowCount
    return List(rowCount) { row -> base + if (row < extra) 1 else 0 }
}

private fun rowStartsFor(rowSizes: List<Int>): List<Int> {
    var next = 0
    return rowSizes.map { size ->
        val start = next
        next += size
        start
    }
}

/**
 * Full-height favorites home shown on the Apps tab. Favorites are laid out in as many
 * columns as fit, in text-list, icon-grid, or TV 16:9 card-grid style. Swiping up or
 * pressing Down from the bottom row (handled via [onNavigateDownToAppList]) reveals the
 * complete app list.
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
    itemFocusRequester: ((String) -> FocusRequester)? = null,
    focusEnabled: Boolean = true,
    onLaunchFavorite: (GameItem) -> Unit,
    onLongPressFavorite: (GameItem) -> Unit,
    onOpenClock: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenWeather: () -> Unit = {},
    onPullDown: () -> Unit,
    onNavigateDownToAppList: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onFocusFavorite: (GameItem) -> Unit = {},
    configuredColumns: Int = 0
) {
    val isTextLayout = layout == FavoritesLayout.TEXT
    val isTvLayout = layout == FavoritesLayout.TV_CARDS
    val contentAlignment = if (isTextLayout) Alignment.Start else Alignment.CenterHorizontally
    val textAlign = if (isTextLayout) TextAlign.Start else TextAlign.Center
    val favoritesSpacing = 10.dp
    // Inline rows add their gutters inside the clickable content, so the clock and date
    // need the same inset to line up with the icons.
    val textRowInset = 6.dp + favoritesSpacing / 2

    // Stable requesters for row-to-row D-pad movement. The implicit 2D focus search used to
    // escape the favorites page (and scroll the apps pager open), so vertical moves inside the
    // grid are wired explicitly instead.
    val rowFocusRequesters = remember(favorites.size) {
        List(favorites.size) { FocusRequester() }
    }
    // The TV favorites grid is laid out as explicit balanced rows, so it scrolls a list of
    // rows rather than a uniform grid.
    val tvRowsListState = rememberLazyListState()
    // A second, id-keyed requester per card lets the dashboard return focus to a specific
    // favorite after a sheet opened from it closes.
    fun itemRequesterModifier(id: String): Modifier =
        itemFocusRequester?.let { Modifier.focusRequester(it(id)) } ?: Modifier

    // The clock and date are hidden only on small screens in landscape (phones held
    // sideways); landscape tablets and TV devices always keep them.
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showClockAndDate = isTvLayout || !isLandscape || configuration.screenHeightDp >= 480

    BoxWithConstraints(
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
        contentAlignment = Alignment.Center
    ) {
        val availableHomeWidth = maxWidth
        val availableHomeHeight = maxHeight

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = contentAlignment
        ) {
            if (showClockAndDate) {
                val emptyDateMod = if (favorites.isEmpty()) {
                    Modifier
                        .then(firstItemFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                        .then(
                            if (onNavigateDownToAppList != null) {
                                Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                        onNavigateDownToAppList()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                } else {
                    Modifier
                }

                FavoritesClockAndDate(
                    isTextLayout = isTextLayout,
                    isTvLayout = isTvLayout,
                    showFocusEffects = showFocusEffects,
                    textAlign = textAlign,
                    textRowInset = textRowInset,
                    weatherEnabled = weatherEnabled,
                    weather = weather,
                    temperatureUnit = temperatureUnit,
                    focusEnabled = focusEnabled,
                    emptyStateAnchor = favorites.isEmpty(),
                    onOpenClock = onOpenClock,
                    onOpenCalendar = onOpenCalendar,
                    onOpenWeather = onOpenWeather,
                    dateRowModifier = emptyDateMod
                )

                Spacer(modifier = Modifier.height(if (isTvLayout) 22.dp else 34.dp))
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
                    text = if (isTvLayout) {
                        "Long-press Select on an app to add it to favorites, or press Down for all apps."
                    } else {
                        "Press and hold an app to add it to favorites."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth().then(emptyInset)
                )
            } else if (isTvLayout) {
                val hSpacing = 14.dp
                val vSpacing = 12.dp
                val gridPadding = 8.dp
                val maxColumns = TV_FAVORITES_MAX_COLUMNS
                val columnLimit = if (configuredColumns > 0) {
                    configuredColumns.coerceIn(1, maxColumns)
                } else {
                    maxColumns
                }

                // Cards are always sized from the full five-column layout. Choosing fewer
                // columns may add rows, but it must never make the cards themselves smaller.
                val maxUsableWidth = (availableHomeWidth - 32.dp - gridPadding * 2)
                    .coerceAtLeast(200.dp)
                val maxColCardWidth = ((maxUsableWidth - hSpacing * (maxColumns - 1)) / maxColumns)
                    .coerceAtLeast(56.dp)
                val clockReserve = 124.dp
                val maxUsableGridHeight = (availableHomeHeight - clockReserve - gridPadding * 2)
                    .coerceAtLeast(100.dp)
                val baseRows = ((favorites.size + maxColumns - 1) / maxColumns).coerceAtLeast(1)
                val baseVisibleRows = baseRows.coerceAtMost(TV_FAVORITES_MAX_ROWS)
                val maxCardHeightForRows =
                    ((maxUsableGridHeight - vSpacing * (baseVisibleRows - 1)) / baseVisibleRows)
                        .coerceAtLeast(48.dp)
                val maxCardWidthFromHeight = maxCardHeightForRows * (16f / 9f)
                val cardWidth = minOf(maxColCardWidth, maxCardWidthFromHeight)
                val cardHeight = cardWidth * (9f / 16f)

                // More than five favorites split into balanced, horizontally centered rows
                // instead of a full row with a single card underneath.
                val rowSizes = remember(favorites.size, columnLimit) {
                    balancedFavoriteRows(favorites.size, columnLimit)
                }
                val rowStarts = remember(rowSizes) { rowStartsFor(rowSizes) }
                val rowIndexByItem = remember(rowSizes) {
                    IntArray(favorites.size).also { rows ->
                        rowStarts.forEachIndexed { rowIndex, start ->
                            repeat(rowSizes[rowIndex]) { offset ->
                                val index = start + offset
                                if (index < rows.size) rows[index] = rowIndex
                            }
                        }
                    }
                }
                val widestRow = rowSizes.maxOrNull()?.coerceAtLeast(1) ?: 1
                val visibleRows = rowSizes.size
                    .coerceAtMost(TV_FAVORITES_MAX_ROWS)
                    .coerceAtLeast(1)
                val gridWidth =
                    cardWidth * widestRow + hSpacing * (widestRow - 1) + gridPadding * 2
                // Fewer configured columns can force more than the usual row count. Keep the
                // card size fixed and cap the viewport instead, so the extra rows scroll
                // beneath the clock rather than spilling past the available height.
                val maxGridHeight = maxUsableGridHeight + gridPadding * 2
                val gridHeight = (
                    cardHeight * visibleRows + vSpacing * (visibleRows - 1) + gridPadding * 2
                    ).coerceAtMost(maxGridHeight)

                LazyColumn(
                    state = tvRowsListState,
                    contentPadding = PaddingValues(gridPadding),
                    verticalArrangement = Arrangement.spacedBy(vSpacing),
                    modifier = Modifier
                        .width(gridWidth)
                        .height(gridHeight)
                        .align(Alignment.CenterHorizontally)
                        .testTag("favorites_tv_grid")
                ) {
                    itemsIndexed(
                        items = rowSizes,
                        key = { rowIndex, _ -> rowIndex }
                    ) { rowIndex, rowSize ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                hSpacing,
                                Alignment.CenterHorizontally
                            )
                        ) {
                            val rowStart = rowStarts[rowIndex]
                            repeat(rowSize) { offset ->
                                val index = rowStart + offset
                                val app = favorites[index]
                                AppTvCard(
                                    app = app,
                                    isFocused = app.id == focusedItemId,
                                    showFocusEffects = showFocusEffects,
                                    testTag = "favorite_app_${app.id}",
                                    onClick = { onLaunchFavorite(app) },
                                    onLongClick = { onLongPressFavorite(app) },
                                    onFocus = { onFocusFavorite(app) },
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .focusProperties { canFocus = focusEnabled }
                                        .focusRequester(rowFocusRequesters[index])
                                        .then(itemRequesterModifier(app.id))
                                        .favoriteRowDpadNavigation(
                                            index = index,
                                            rowIndex = rowIndexByItem[index],
                                            lastVisibleRow = visibleRows - 1,
                                            rowStarts = rowStarts,
                                            rowSizes = rowSizes,
                                            requesters = rowFocusRequesters,
                                            onNavigateDownToAppList = onNavigateDownToAppList
                                        )
                                        .then(
                                            if (index == 0) {
                                                firstItemFocusRequester?.let {
                                                    Modifier.focusRequester(it)
                                                } ?: Modifier
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
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
                        val columns = when {
                            configuredColumns > 0 -> configuredColumns
                            isCompactPortrait -> 1
                            else -> maxOf(
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
                                        .focusProperties { canFocus = focusEnabled }
                                        .focusRequester(rowFocusRequesters[index])
                                        .then(itemRequesterModifier(app.id))
                                        .favoriteDpadNavigation(
                                            index = index,
                                            columns = columns,
                                            itemCount = favorites.size,
                                            requesters = rowFocusRequesters,
                                            onNavigateDownToAppList = onNavigateDownToAppList
                                        )
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
                        // Icon tiles in as many columns as the screen width allows (or configured).
                        // Tiles are sized to the full column pitch so every pixel of the grid,
                        // including the visual spacing, belongs to a tile's clickable area.
                        val tileWidth = 78.dp
                        val tileGap = spacing
                        val rowGap = 14.dp
                        val defaultPitch = tileWidth + tileGap
                        val columns = if (configuredColumns > 0) {
                            configuredColumns
                        } else {
                            maxOf(
                                4,
                                ((maxWidth + tileGap) / defaultPitch).toInt()
                            )
                        }
                        val itemPitch = if (configuredColumns > 0) {
                            maxWidth / columns
                        } else {
                            defaultPitch
                        }
                        val cellHorizontalPadding = if (itemPitch < 68.dp) 2.dp else tileGap / 2

                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("favorites_icons_grid"),
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
                                        horizontal = cellHorizontalPadding,
                                        vertical = 6.dp + rowGap / 2
                                    ),
                                    modifier = Modifier
                                        .width(itemPitch)
                                        .focusProperties { canFocus = focusEnabled }
                                        .focusRequester(rowFocusRequesters[index])
                                        .then(itemRequesterModifier(app.id))
                                        .favoriteDpadNavigation(
                                            index = index,
                                            columns = columns,
                                            itemCount = favorites.size,
                                            requesters = rowFocusRequesters,
                                            onNavigateDownToAppList = onNavigateDownToAppList
                                        )
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
}

/**
 * D-pad handling for the TV favorites rows, which can have different sizes per row.
 * Down/Up keep the horizontal position where possible and clamp to the shorter row's end.
 */
private fun Modifier.favoriteRowDpadNavigation(
    index: Int,
    rowIndex: Int,
    lastVisibleRow: Int,
    rowStarts: List<Int>,
    rowSizes: List<Int>,
    requesters: List<FocusRequester>,
    onNavigateDownToAppList: (() -> Unit)?
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) {
        false
    } else {
        when (event.key) {
            Key.DirectionDown -> {
                if (rowIndex < lastVisibleRow) {
                    val targetRow = rowIndex + 1
                    val column = index - rowStarts[rowIndex]
                    val targetIndex = rowStarts[targetRow] +
                        column.coerceAtMost(rowSizes[targetRow] - 1)
                    runCatching { requesters.getOrNull(targetIndex)?.requestFocus() }
                    true
                } else {
                    onNavigateDownToAppList?.invoke()
                    true
                }
            }
            Key.DirectionUp -> {
                if (rowIndex > 0) {
                    val targetRow = rowIndex - 1
                    val column = index - rowStarts[rowIndex]
                    val targetIndex = rowStarts[targetRow] +
                        column.coerceAtMost(rowSizes[targetRow] - 1)
                    runCatching { requesters.getOrNull(targetIndex)?.requestFocus() }
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }
}

/**
 * Explicit vertical D-pad handling for favorites.
 *
 * Compose's implicit 2D search can leave the favorites page when the apps pager is
 * already composed, which both steals focus and scrolls the pager open. Moving between
 * rows is therefore wired to stable [FocusRequester]s. Moving Up from the first row is
 * deliberately left unhandled so focus can continue to the clock/date and the top bar.
 */
private fun Modifier.favoriteDpadNavigation(
    index: Int,
    columns: Int,
    itemCount: Int,
    requesters: List<FocusRequester>,
    onNavigateDownToAppList: (() -> Unit)?
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) {
        false
    } else {
        when (event.key) {
            Key.DirectionDown -> {
                val currentRow = index / columns
                val lastRow = (itemCount - 1) / columns
                if (currentRow < lastRow) {
                    val targetIndex = minOf(index + columns, itemCount - 1)
                    runCatching { requesters.getOrNull(targetIndex)?.requestFocus() }
                    true
                } else {
                    onNavigateDownToAppList?.invoke()
                    true
                }
            }
            Key.DirectionUp -> {
                val above = index - columns
                if (above >= 0) {
                    runCatching { requesters.getOrNull(above)?.requestFocus() }
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }
}

@Composable
private fun FavoritesClockAndDate(
    isTextLayout: Boolean,
    isTvLayout: Boolean,
    showFocusEffects: Boolean,
    textAlign: TextAlign,
    textRowInset: androidx.compose.ui.unit.Dp,
    weatherEnabled: Boolean,
    weather: WeatherInfo?,
    temperatureUnit: TemperatureUnit,
    focusEnabled: Boolean,
    emptyStateAnchor: Boolean = false,
    onOpenClock: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenWeather: () -> Unit,
    clockModifier: Modifier = Modifier,
    dateRowModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    // On TV the clock, date and weather are display-only: D-pad Up from the favorites grid
    // goes straight to the top navigation bar instead of stepping through them. The one
    // exception is an empty favorites page, where the date row is the only target on the
    // page; it keeps focus there so the page still has a focus anchor and Down can open the
    // app list.
    val clockCanFocus = focusEnabled && !isTelevisionMode()
    val dateRowCanFocus = clockCanFocus || (emptyStateAnchor && focusEnabled)
    val timeFormatter = remember(is24Hour) {
        SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", Locale.getDefault())
    }
    val dateFormatter = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    }
    val nowDate = remember(now) { Date(now) }

    val clockInteraction = remember { MutableInteractionSource() }
    val clockFocused = showFocusEffects && clockInteraction.collectIsFocusedAsState().value
    val dateInteraction = remember { MutableInteractionSource() }
    val dateFocused = showFocusEffects && dateInteraction.collectIsFocusedAsState().value
    val weatherInteraction = remember { MutableInteractionSource() }
    val weatherFocused = showFocusEffects && weatherInteraction.collectIsFocusedAsState().value

    Text(
        text = timeFormatter.format(nowDate),
        fontSize = if (isTvLayout) 52.sp else 64.sp,
        fontWeight = FontWeight.Light,
        color = if (clockFocused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
        modifier = clockModifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (clockFocused) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                }
            )
            .focusProperties { canFocus = clockCanFocus }
            .clickable(
                interactionSource = clockInteraction,
                indication = null,
                onClick = onOpenClock
            )
            .padding(vertical = 4.dp)
            .then(if (isTextLayout) Modifier.padding(start = textRowInset) else Modifier.padding(horizontal = 20.dp))
            .testTag("favorites_clock")
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isTextLayout) Arrangement.Start else Arrangement.Center,
        modifier = dateRowModifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (dateFocused) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .focusProperties { canFocus = dateRowCanFocus }
            .clickable(
                interactionSource = dateInteraction,
                indication = null,
                onClick = onOpenCalendar
            )
            .padding(vertical = 4.dp)
            .then(if (isTextLayout) Modifier.padding(start = textRowInset) else Modifier.padding(horizontal = 14.dp))
            .testTag("favorites_date_row")
    ) {
        Text(
            text = dateFormatter.format(nowDate),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (dateFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
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
                    .then(
                        if (weatherFocused) {
                            Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
                    .focusProperties { canFocus = clockCanFocus }
                    .clickable(
                        interactionSource = weatherInteraction,
                        indication = null,
                        onClick = onOpenWeather
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("favorites_weather"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = weatherIcon(weather.weatherCode),
                    contentDescription = weatherDescription(weather.weatherCode),
                    tint = if (weatherFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = weather.temperatureDisplay(temperatureUnit, showUnit = false),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (weatherFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Long-press menu for a favorite: customize, view app info, edit order, or remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteActionsSheet(
    item: GameItem,
    onCustomize: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onEditFavorites: () -> Unit,
    onMoveToFolder: () -> Unit = {},
    onManageFolderApps: () -> Unit = {},
    onRemoveFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isFolder = item.type == GameType.FOLDER
    val firstActionFocusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LaunchedEffect(item.id) {
            withFrameNanos { }
            runCatching { firstActionFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .consumeOrphanedSelectKeyEvents()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            ContextMenuHeaderBar(
                item = item,
                subtitle = if (isFolder) "Favorite folder" else "Favorite app",
                onCustomize = onCustomize,
                onOpenAppInfo = onOpenAppInfo,
                headerTestTag = "favorite_context_menu_header",
                appInfoTestTag = "favorite_context_menu_app_info"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(8.dp))

            if (isFolder) {
                FavoriteActionRow(
                    icon = Icons.Default.Apps,
                    title = "Manage folder apps",
                    onClick = onManageFolderApps,
                    testTag = "favorite_action_manage_folder",
                    modifier = Modifier.focusRequester(firstActionFocusRequester)
                )
            } else {
                FavoriteActionRow(
                    icon = Icons.Default.CreateNewFolder,
                    title = "Move to folder",
                    onClick = onMoveToFolder,
                    testTag = "favorite_action_move_to_folder",
                    modifier = Modifier.focusRequester(firstActionFocusRequester)
                )
            }

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

/** Bottom sheet where favorites are reordered by drag-and-drop or D-pad Move Up/Down buttons. */
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
    val doneFocusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { doneFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .consumeOrphanedSelectKeyEvents()
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
                text = "Drag rows or use the arrow buttons to reorder.",
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
                                    if (index > 0) {
                                        val moved = editable.removeAt(index)
                                        editable.add(index - 1, moved)
                                        onReorder(editable.toList())
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.testTag("edit_favorites_move_up_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move ${item.title} up",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (index < editable.lastIndex) {
                                        val moved = editable.removeAt(index)
                                        editable.add(index + 1, moved)
                                        onReorder(editable.toList())
                                    }
                                },
                                enabled = index < editable.lastIndex,
                                modifier = Modifier.testTag("edit_favorites_move_down_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move ${item.title} down",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

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
                    .focusRequester(doneFocusRequester)
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
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (isFocused) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
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
