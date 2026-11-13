package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GameItem
import com.example.data.service.ArtworkOption
import com.example.data.service.SteamGridDbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeItemDialog(
    item: GameItem,
    steamGridDbApiKey: String?,
    includeNsfw: Boolean = false,
    onApplyTitle: (id: String, newTitle: String) -> Unit,
    onApplyPoster: (id: String, newTitle: String, isNsfwSlot: Boolean, artworkUrl: String?) -> Unit,
    onSetOverlayLogo: (id: String, newTitle: String, enabled: Boolean) -> Unit,
    onUpdateNsfwLogoUrl: (id: String, logoUrl: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var customUrlInput by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ArtworkOption>>(emptyList()) }
    var isSearching by remember { mutableStateOf(true) }

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
    val showLogoOverlay = isNsfwSlot && item.overlayNsfwLogo && !item.nsfwLogoUrl.isNullOrBlank()

    val commitTitleAndDismiss = {
        onApplyTitle(item.id, title)
        onDismiss()
    }

    BackHandler(onBack = commitTitleAndDismiss)

    // Local file and photo pickers (both copy into app-private storage so URIs persist).
    val applyPickedUri: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            val slotSuffix = when {
                !item.isGame -> "icon"
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

    LaunchedEffect(title, includeNsfw) {
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
        if (isNsfwSlot && item.overlayNsfwLogo) {
            val resolvedLogo = SteamGridDbService.findLogoForGame(query, steamGridDbApiKey)
            if (!resolvedLogo.isNullOrBlank()) {
                onUpdateNsfwLogoUrl(item.id, resolvedLogo)
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = commitTitleAndDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                IconButton(
                    onClick = commitTitleAndDismiss,
                    modifier = Modifier.testTag("customize_close_button")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title editing field
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

            if (isNsfwSlot) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSetOverlayLogo(item.id, title, !item.overlayNsfwLogo) }
                        .padding(horizontal = 4.dp)
                        .testTag("customize_overlay_logo_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.overlayNsfwLogo,
                        onCheckedChange = { checked -> onSetOverlayLogo(item.id, title, checked) },
                        modifier = Modifier.testTag("customize_overlay_logo_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Overlay logo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Source Tabs: SteamGridDB, Local Photo, Custom URL
            val tabs = listOf("SteamGridDB", "Local Photo", "Custom URL")
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 0.dp,
                divider = {},
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Full-width picker container (tap an option to apply immediately)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp)
            ) {
                when (selectedTab) {
                    0 -> { // SteamGridDB
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

                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 104.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize().testTag("customize_poster_grid")
                                ) {
                                    if (hasAssignedPoster) {
                                        item(key = "assigned_poster") {
                                            PosterOptionCard(
                                                imageUrl = activeSlotUrl!!,
                                                contentDescription = "Assigned poster (tap to unassign)",
                                                isSelected = true,
                                                logoOverlayUrl = if (showLogoOverlay) item.nsfwLogoUrl else null,
                                                onClick = {
                                                    onApplyPoster(item.id, title, isNsfwSlot, null)
                                                },
                                                modifier = Modifier.testTag("assigned_poster_card")
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
                                        items(remainingResults, key = { it.id }) { option ->
                                            PosterOptionCard(
                                                imageUrl = option.thumbnailUrl,
                                                contentDescription = option.title,
                                                isSelected = false,
                                                logoOverlayUrl = if (showLogoOverlay) item.nsfwLogoUrl else null,
                                                onClick = {
                                                    onApplyPoster(
                                                        item.id,
                                                        title,
                                                        isNsfwSlot,
                                                        option.fullUrl
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // Local Photo / File
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
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
                            Spacer(modifier = Modifier.height(14.dp))
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
                    2 -> { // Custom URL
                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = customUrlInput,
                                onValueChange = { customUrlInput = it },
                                label = { Text("https://example.com/poster.jpg") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
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
                                enabled = customUrlInput.isNotBlank()
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply Image URL")
                            }
                        }
                    }
                }
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
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
        val target = File(dir, "$prefix${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (target.length() == 0L) {
            target.delete()
            return null
        }
        dir.listFiles { file -> file.name.startsWith(prefix) && file != target }
            ?.forEach { it.delete() }
        Uri.fromFile(target).toString()
    }.getOrNull()
