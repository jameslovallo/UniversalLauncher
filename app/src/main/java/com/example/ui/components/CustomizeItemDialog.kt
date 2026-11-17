package com.example.ui.components

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GameItem
import com.example.data.service.ArtworkOption
import com.example.data.service.IconPackIcon
import com.example.data.service.IconPackInfo
import com.example.data.service.IconPackManager
import com.example.data.service.IconPackOverridesStore
import com.example.data.service.SteamGridDbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class CustomizeArtworkTab(val label: String) {
    STEAMGRIDDB("SteamGridDB"),
    ICON_THEME("Icon Theme"),
    LOCAL_PHOTO("Local Photo"),
    CUSTOM_URL("Custom URL")
}

/**
 * Filters the icon-theme picker by the display name or the raw drawable name. Packs name
 * drawables inconsistently (`com_spotify_music`, `spotify`, ...), so matching both means
 * a search for "spotify" finds the icon either way.
 */
internal fun filterIconPackIcons(
    icons: List<IconPackIcon>,
    query: String
): List<IconPackIcon> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return icons
    return icons.filter { icon ->
        icon.label.contains(trimmed, ignoreCase = true) ||
            icon.drawableName.contains(trimmed, ignoreCase = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeItemDialog(
    item: GameItem,
    steamGridDbApiKey: String?,
    includeNsfw: Boolean = false,
    iconPack: IconPackInfo? = null,
    activeThemeKey: String = IconPackOverridesStore.themeKey(iconPack?.packageName),
    iconPackIcons: List<IconPackIcon> = emptyList(),
    selectedPackDrawable: String? = null,
    canResetIcon: Boolean = false,
    onApplyTitle: (id: String, newTitle: String) -> Unit,
    onApplyPoster: (id: String, newTitle: String, isNsfwSlot: Boolean, artworkUrl: String?) -> Unit,
    onSetOverlayLogo: (id: String, newTitle: String, isNsfwSlot: Boolean, enabled: Boolean) -> Unit,
    onUpdateLogoUrl: (id: String, isNsfwSlot: Boolean, logoUrl: String?) -> Unit,
    onSelectIconPackIcon: (id: String, newTitle: String, drawableName: String) -> Unit,
    onResetIcon: (id: String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val tabs = remember(item.isGame, iconPack) {
        if (item.isGame) {
            listOf(
                CustomizeArtworkTab.STEAMGRIDDB,
                CustomizeArtworkTab.LOCAL_PHOTO,
                CustomizeArtworkTab.CUSTOM_URL
            )
        } else {
            buildList {
                // Apps can borrow a different icon from the active theme.
                if (iconPack != null) add(CustomizeArtworkTab.ICON_THEME)
                add(CustomizeArtworkTab.LOCAL_PHOTO)
                add(CustomizeArtworkTab.CUSTOM_URL)
            }
        }
    }
    var title by remember(item.id) { mutableStateOf(item.title) }
    var selectedTab by remember(item.id, item.isGame) { mutableStateOf(tabs.first()) }
    // The Icon Theme tab disappears if the active pack is removed while the sheet is open.
    LaunchedEffect(tabs) {
        if (selectedTab !in tabs) selectedTab = tabs.first()
    }
    var customUrlInput by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ArtworkOption>>(emptyList()) }
    var isSearching by remember { mutableStateOf(item.isGame) }
    var iconQuery by remember(item.id) { mutableStateOf("") }
    val filteredIconPackIcons = remember(iconPackIcons, iconQuery) {
        filterIconPackIcons(iconPackIcons, iconQuery)
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isNsfwSlot = item.isGame && includeNsfw
    val activeSlotUrl = when {
        !item.isGame -> item.iconUrl
        isNsfwSlot -> item.nsfwPosterUrl
        else -> item.posterUrl
    }
    val hasAssignedPoster = !activeSlotUrl.isNullOrBlank()
    // Each poster slot carries its own logo overlay, so the safe and adult posters can
    // use different artwork.
    val activeLogoUrl = if (isNsfwSlot) item.nsfwLogoUrl else item.logoUrl
    val activeOverlay = if (isNsfwSlot) item.overlayNsfwLogo else item.overlayLogo
    val showLogoOverlay = item.isGame && activeOverlay && !activeLogoUrl.isNullOrBlank()

    val commitTitleAndDismiss = {
        onApplyTitle(item.id, title)
        onDismiss()
    }

    BackHandler(onBack = commitTitleAndDismiss)

    // Local file and photo pickers (both copy into app-private storage so URIs persist).
    val applyPickedUri: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            val safeThemeKey = activeThemeKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val slotSuffix = when {
                !item.isGame -> "icon_$safeThemeKey"
                isNsfwSlot -> "nsfw"
                else -> "default"
            }
            scope.launch {
                val stored = withContext(Dispatchers.IO) {
                    copyPickedImage(context, item.id, slotSuffix, uri)
                }
                val resolvedUrl = stored ?: uri.toString()
                onApplyPoster(item.id, title, isNsfwSlot, resolvedUrl)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = applyPickedUri
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = applyPickedUri
    )

    LaunchedEffect(
        title,
        includeNsfw,
        item.isGame,
        item.overlayLogo,
        item.overlayNsfwLogo
    ) {
        if (!item.isGame) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        val query = title.trim()
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        // Debounce typing so a search is only issued once the name settles.
        delay(350)
        searchResults = SteamGridDbService.searchArtwork(query, steamGridDbApiKey, includeNsfw)
        isSearching = false
        if (activeOverlay) {
            val resolvedLogo = SteamGridDbService.findLogoForGame(query, steamGridDbApiKey)
            if (!resolvedLogo.isNullOrBlank()) {
                onUpdateLogoUrl(item.id, isNsfwSlot, resolvedLogo)
            }
        }
    }

    val isTv = isTelevisionMode()
    val configuration = LocalConfiguration.current
    val isWideSheetScreen = isTv ||
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val closeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(item.id) {
        runCatching { closeFocusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = commitTitleAndDismiss,
        sheetState = sheetState,
        sheetMaxWidth = if (isWideSheetScreen) 900.dp else BottomSheetDefaults.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (isWideSheetScreen) 0.94f else 0.88f)
                .consumeOrphanedSelectKeyEvents()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!item.isGame) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("customize_icon_preview"),
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcon(app = item, size = 44.dp)
                        }
                    }
                    Column {
                        Text(
                            text = "Customize Name & Artwork",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.displayCategory,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                val closeInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = commitTitleAndDismiss,
                    interactionSource = closeInteraction,
                    modifier = Modifier
                        .focusRequester(closeFocusRequester)
                        .tvFocusBorder(closeInteraction, shape = CircleShape)
                        .testTag("customize_close_button")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val renderDisplayNameAndAction: @Composable () -> Unit = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onApplyTitle(item.id, title)
                            keyboardController?.hide()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("customize_name_input")
                )

                if (item.isGame) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val overlayRowInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .tvFocusBorder(overlayRowInteraction, shape = RoundedCornerShape(12.dp))
                            .clickable(interactionSource = overlayRowInteraction, indication = null) {
                                onSetOverlayLogo(item.id, title, isNsfwSlot, !activeOverlay)
                            }
                            .padding(horizontal = 4.dp)
                            .testTag("customize_overlay_logo_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = activeOverlay,
                            onCheckedChange = { checked ->
                                onSetOverlayLogo(item.id, title, isNsfwSlot, checked)
                            },
                            modifier = Modifier
                                .focusProperties { canFocus = false }
                                .testTag("customize_overlay_logo_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Overlay logo",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onResetIcon(item.id) },
                        enabled = canResetIcon,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("customize_reset_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset icon to default",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val renderTabContent: @Composable () -> Unit = {
                when (selectedTab) {
                    CustomizeArtworkTab.STEAMGRIDDB -> { // SteamGridDB (games only)
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (isSearching && !hasAssignedPoster) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else if (!isSearching && searchResults.isEmpty() && !hasAssignedPoster) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No artwork found",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Try a different title, or add a local photo.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                val remainingResults = remember(searchResults, activeSlotUrl) {
                                    if (activeSlotUrl.isNullOrBlank()) {
                                        searchResults
                                    } else {
                                        searchResults.filterNot { it.fullUrl == activeSlotUrl }
                                    }
                                }

                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val spacing = 8.dp
                                    val columns = maxOf(2, ((maxWidth + spacing) / (104.dp + spacing)).toInt())
                                    val gridState = rememberLazyGridState()
                                    val gridScope = rememberCoroutineScope()
                                    val assignedCount = if (hasAssignedPoster) 1 else 0
                                    val resultItemsCount = if (!isSearching && remainingResults.isNotEmpty()) {
                                        remainingResults.size
                                    } else {
                                        0
                                    }
                                    val totalCards = assignedCount + resultItemsCount
                                    val posterRequesters = remember(totalCards) {
                                        List(totalCards) { FocusRequester() }
                                    }

                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(columns),
                                        state = gridState,
                                        verticalArrangement = Arrangement.spacedBy(spacing),
                                        horizontalArrangement = Arrangement.spacedBy(spacing),
                                        modifier = Modifier.fillMaxSize().testTag("customize_poster_grid")
                                    ) {
                                        if (hasAssignedPoster) {
                                            item(key = "assigned_poster") {
                                                PosterOptionCard(
                                                    imageUrl = activeSlotUrl!!,
                                                    contentDescription = "Assigned poster (tap to unassign)",
                                                    isSelected = true,
                                                    logoOverlayUrl = if (showLogoOverlay) activeLogoUrl else null,
                                                    onClick = {
                                                        onApplyPoster(item.id, title, isNsfwSlot, null)
                                                    },
                                                    modifier = Modifier
                                                        .focusRequester(posterRequesters[0])
                                                        .flatGridDownNextRowFallback(
                                                            index = 0,
                                                            columns = columns,
                                                            itemCount = totalCards,
                                                            gridState = gridState,
                                                            requesters = posterRequesters,
                                                            scope = gridScope
                                                        )
                                                        .testTag("assigned_poster_card")
                                                )
                                            }
                                        }

                                        if (isSearching) {
                                            item(
                                                key = "loading_results",
                                                span = { GridItemSpan(maxLineSpan) }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 24.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator()
                                                }
                                            }
                                        } else if (remainingResults.isEmpty() && !hasAssignedPoster) {
                                            item(
                                                key = "empty_results",
                                                span = { GridItemSpan(maxLineSpan) }
                                            ) {
                                                Text(
                                                    text = "No artwork found",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 16.dp)
                                                )
                                            }
                                        } else {
                                            itemsIndexed(remainingResults, key = { _, opt -> opt.id }) { index, option ->
                                                val logicalIdx = assignedCount + index
                                                PosterOptionCard(
                                                    imageUrl = option.thumbnailUrl,
                                                    contentDescription = option.title,
                                                    isSelected = false,
                                                    logoOverlayUrl = if (showLogoOverlay) activeLogoUrl else null,
                                                    onClick = {
                                                        onApplyPoster(
                                                            item.id,
                                                            title,
                                                            isNsfwSlot,
                                                            option.fullUrl
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .focusRequester(posterRequesters[logicalIdx])
                                                        .flatGridDownNextRowFallback(
                                                            index = logicalIdx,
                                                            columns = columns,
                                                            itemCount = totalCards,
                                                            gridState = gridState,
                                                            requesters = posterRequesters,
                                                            scope = gridScope
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    CustomizeArtworkTab.ICON_THEME -> { // Icon from the active icon theme
                        if (iconPack == null || iconPackIcons.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (iconPack == null) {
                                        "Select an icon theme in Settings to choose from it."
                                    } else {
                                        "No icons found in ${iconPack.label}."
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = iconQuery,
                                    onValueChange = { iconQuery = it },
                                    placeholder = {
                                        Text("Search icons...", fontSize = 13.sp)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = {
                                        if (iconQuery.isNotEmpty()) {
                                            IconButton(onClick = { iconQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear icon search"
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("customize_icon_pack_search")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (filteredIconPackIcons.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(30.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No icons match \"${iconQuery.trim()}\".",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        val spacing = 8.dp
                                        val columns = maxOf(3, ((maxWidth + spacing) / (72.dp + spacing)).toInt())
                                        val gridState = rememberLazyGridState()
                                        val gridScope = rememberCoroutineScope()
                                        val iconRequesters = remember(filteredIconPackIcons.size) {
                                            List(filteredIconPackIcons.size) { FocusRequester() }
                                        }

                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(columns),
                                            state = gridState,
                                            verticalArrangement = Arrangement.spacedBy(spacing),
                                            horizontalArrangement = Arrangement.spacedBy(spacing),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("customize_icon_pack_grid")
                                        ) {
                                            itemsIndexed(
                                                filteredIconPackIcons,
                                                key = { _, icon -> icon.drawableName }
                                            ) { index, icon ->
                                                IconPackOptionCard(
                                                    packPackage = iconPack.packageName,
                                                    icon = icon,
                                                    isSelected = icon.drawableName == selectedPackDrawable,
                                                    onClick = {
                                                        onSelectIconPackIcon(
                                                            item.id,
                                                            title,
                                                            icon.drawableName
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .focusRequester(iconRequesters[index])
                                                        .flatGridDownNextRowFallback(
                                                            index = index,
                                                            columns = columns,
                                                            itemCount = filteredIconPackIcons.size,
                                                            gridState = gridState,
                                                            requesters = iconRequesters,
                                                            scope = gridScope
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    CustomizeArtworkTab.LOCAL_PHOTO -> { // Local Photo / File
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = maxHeight)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Pick Local Image",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Choose an image file from device folders, Drive, or your gallery",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        filePickerLauncher.launch(arrayOf("image/*"))
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("customize_pick_file_button")
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Choose Image File")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("customize_pick_gallery_button")
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Browse Photos")
                                }
                            }
                        }
                    }
                    CustomizeArtworkTab.CUSTOM_URL -> { // Custom URL
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            OutlinedTextField(
                                value = customUrlInput,
                                onValueChange = { customUrlInput = it },
                                label = { Text("https://example.com/poster.jpg") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("customize_custom_url_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (customUrlInput.isNotBlank()) {
                                        onApplyPoster(
                                            item.id,
                                            title,
                                            isNsfwSlot,
                                            customUrlInput.trim()
                                        )
                                    }
                                },
                                enabled = customUrlInput.isNotBlank(),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("customize_apply_url_button")
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply Image URL")
                            }
                        }
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val useTwoColumnLayout = maxWidth >= 520.dp
                if (useTwoColumnLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("customize_two_column_layout"),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .testTag("customize_left_column")
                        ) {
                            renderDisplayNameAndAction()

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                tabs.forEach { tab ->
                                    val isSelected = selectedTab == tab
                                    val tabInteraction = remember { MutableInteractionSource() }
                                    val tabFocused by tabInteraction.collectIsFocusedAsState()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                }
                                            )
                                            .border(
                                                width = if (tabFocused || isSelected) 2.dp else 1.dp,
                                                color = when {
                                                    tabFocused -> MaterialTheme.colorScheme.primary
                                                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                },
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable(
                                                interactionSource = tabInteraction,
                                                indication = null
                                            ) { selectedTab = tab }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                            .testTag("customize_tab_${tab.name.lowercase()}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tab.label,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(0.58f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(12.dp)
                                .testTag("customize_right_column")
                        ) {
                            renderTabContent()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("customize_single_column_layout")
                    ) {
                        renderDisplayNameAndAction()

                        Spacer(modifier = Modifier.height(10.dp))

                        val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
                        ScrollableTabRow(
                            selectedTabIndex = selectedIndex,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            edgePadding = 0.dp,
                            divider = {},
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            tabs.forEach { tab ->
                                val isSelected = selectedTab == tab
                                Tab(
                                    selected = isSelected,
                                    onClick = { selectedTab = tab },
                                    modifier = Modifier.testTag("customize_tab_${tab.name.lowercase()}"),
                                    text = {
                                        Text(
                                            text = tab.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(12.dp)
                                .testTag("customize_right_column")
                        ) {
                            renderTabContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPackOptionCard(
    packPackage: String,
    icon: IconPackIcon,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Previews are rendered on demand and cached, so a large theme does not block the
    // sheet while every icon is rasterised up front.
    var previewUrl by remember(packPackage, icon.drawableName) { mutableStateOf<String?>(null) }
    var isLoading by remember(packPackage, icon.drawableName) { mutableStateOf(true) }
    LaunchedEffect(packPackage, icon.drawableName) {
        previewUrl = IconPackManager.previewUrl(context, packPackage, icon.drawableName)
        isLoading = false
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isFocused || isSelected) 2.5.dp else 1.dp,
                color = if (isFocused || isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(10.dp)
            .testTag("icon_pack_icon_${icon.drawableName}"),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
            previewUrl != null -> AsyncImage(
                model = previewUrl,
                contentDescription = icon.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            else -> Icon(
                imageVector = Icons.Default.Image,
                contentDescription = icon.label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun PosterOptionCard(
    imageUrl: String,
    contentDescription: String,
    isSelected: Boolean,
    logoOverlayUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isFocused || isSelected) 2.5.dp else 1.dp,
                color = if (isFocused || isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
        )

        if (!logoOverlayUrl.isNullOrBlank()) {
            PosterLogoOverlay(logoUrl = logoOverlayUrl)
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Copies a photo-picker URI into app-private storage and returns a `file://` URL.
 *
 * Photo-picker grants are transient, so a bare `content://` URI saved in the database
 * would stop resolving after the app is restarted. Older copies for the same item and
 * artwork slot are deleted to avoid unbounded growth without clobbering the other slot.
 */
private fun copyPickedImage(
    context: Context,
    itemId: String,
    slotSuffix: String,
    uri: Uri
): String? =
    runCatching {
        val dir = File(context.filesDir, "custom_art").apply { mkdirs() }
        val safeId = itemId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val prefix = "${safeId}_${slotSuffix}-"
        val maxDimPx = if (slotSuffix.startsWith("icon")) 512 else 1200

        // Inspect dimensions first so huge camera/gallery photos are stored at a display-ready
        // size instead of forcing Coil to decode multi-megapixel files during scroll.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        val target: File
        if (bounds.outWidth > maxDimPx || bounds.outHeight > maxDimPx) {
            var sampleSize = 1
            while (
                (bounds.outWidth / (sampleSize * 2)) >= maxDimPx ||
                (bounds.outHeight / (sampleSize * 2)) >= maxDimPx
            ) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val sampled = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            }
            if (sampled != null) {
                val longest = maxOf(sampled.width, sampled.height)
                val finalBitmap = if (longest > maxDimPx) {
                    val scale = maxDimPx.toFloat() / longest.toFloat()
                    val w = (sampled.width * scale).toInt().coerceAtLeast(1)
                    val h = (sampled.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(sampled, w, h, true).also {
                        if (it != sampled) sampled.recycle()
                    }
                } else {
                    sampled
                }
                val hasAlpha = finalBitmap.hasAlpha()
                val ext = if (hasAlpha) "png" else "jpg"
                target = File(dir, "$prefix${System.currentTimeMillis()}.$ext")
                target.outputStream().use { out ->
                    finalBitmap.compress(
                        if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                        90,
                        out
                    )
                }
                finalBitmap.recycle()
            } else {
                target = File(dir, "$prefix${System.currentTimeMillis()}.img")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return null
            }
        } else {
            target = File(dir, "$prefix${System.currentTimeMillis()}.img")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
        }

        if (target.length() == 0L) {
            target.delete()
            return null
        }
        dir.listFiles { file -> file.name.startsWith(prefix) && file != target }
            ?.forEach { it.delete() }
        Uri.fromFile(target).toString()
    }.getOrNull()
