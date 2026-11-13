package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
    onRemoveNsfwPoster: (id: String, newTitle: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var isNsfwSlot by remember(item.id) { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember(item.id) { mutableStateOf(item.title) }
    var customUrlInput by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ArtworkOption>>(emptyList()) }
    var isSearching by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val activeSlotUrl = when {
        !item.isGame -> item.iconUrl
        isNsfwSlot -> item.nsfwPosterUrl
        else -> item.posterUrl
    }
    val hasNsfwPoster = item.isGame && !item.nsfwPosterUrl.isNullOrBlank()

    val commitTitleAndDismiss = {
        onApplyTitle(item.id, title)
        onDismiss()
    }

    BackHandler(onBack = commitTitleAndDismiss)

    // Local photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val slotAtPick = isNsfwSlot
            val slotSuffix = when {
                !item.isGame -> "icon"
                slotAtPick -> "nsfw"
                else -> "default"
            }
            scope.launch {
                // The photo picker only grants transient read access to the picked URI, so
                // copy the bytes into app storage; the stored path then survives restarts.
                val stored = withContext(Dispatchers.IO) {
                    copyPickedImage(context, item.id, slotSuffix, uri)
                }
                val resolvedUrl = stored ?: uri.toString()
                onApplyPoster(item.id, title, slotAtPick, resolvedUrl)
            }
        }
    }

    LaunchedEffect(searchQuery, includeNsfw) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        // Debounce typing so a search is only issued once the title settles.
        delay(350)
        searchResults = SteamGridDbService.searchArtwork(query, steamGridDbApiKey, includeNsfw)
        isSearching = false
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

            if (item.isGame) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !isNsfwSlot,
                        onClick = { isNsfwSlot = false },
                        label = { Text("Default Poster") },
                        modifier = Modifier.testTag("customize_slot_default")
                    )
                    FilterChip(
                        selected = isNsfwSlot,
                        onClick = { isNsfwSlot = true },
                        label = {
                            Text(
                                if (hasNsfwPoster) "NSFW Poster (Set)" else "NSFW Poster"
                            )
                        },
                        modifier = Modifier.testTag("customize_slot_nsfw")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search SteamGridDB...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(onClick = {
                                    scope.launch {
                                        isSearching = true
                                        searchResults = SteamGridDbService.searchArtwork(
                                            searchQuery,
                                            steamGridDbApiKey,
                                            includeNsfw
                                        )
                                        isSearching = false
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (isSearching && !hasNsfwPoster) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else if (!isSearching && searchResults.isEmpty() && !hasNsfwPoster) {
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
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 104.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize().testTag("customize_poster_grid")
                                ) {
                                    if (hasNsfwPoster) {
                                        item(key = "remove_nsfw_poster") {
                                            RemoveNsfwPosterButton(
                                                onClick = { onRemoveNsfwPoster(item.id, title) }
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
                                    } else if (searchResults.isEmpty()) {
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
                                        items(searchResults, key = { it.id }) { option ->
                                            val isSelected = activeSlotUrl == option.fullUrl
                                            Box(
                                                modifier = Modifier
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
                                                    .clickable {
                                                        onApplyPoster(
                                                            item.id,
                                                            title,
                                                            isNsfwSlot,
                                                            option.fullUrl
                                                        )
                                                    }
                                            ) {
                                                AsyncImage(
                                                    model = option.thumbnailUrl,
                                                    contentDescription = option.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(2f / 3f)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )

                                                if (option.isNsfw) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopStart)
                                                            .padding(6.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.errorContainer)
                                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "NSFW",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
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
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // Local Photo
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
                                text = "Select any picture from device storage to apply immediately",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse Gallery")
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
private fun RemoveNsfwPosterButton(
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .testTag("remove_nsfw_poster_button")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Remove NSFW Poster",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
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
