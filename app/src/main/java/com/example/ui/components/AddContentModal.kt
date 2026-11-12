package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.GameItem
import com.example.data.service.GeForceNowService
import com.example.ui.viewmodel.AddModalSubView
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContentModal(
    subView: AddModalSubView,
    onNavigateSubView: (AddModalSubView) -> Unit,
    onOpenPlayStore: () -> Unit,
    onOpenMoonlight: () -> Unit,
    onAddShortcut: (title: String, url: String, posterUrl: String?) -> Unit,
    onAddGfnGame: (GameItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The root menu is short, so let the sheet wrap it. Sub-views (app grid, search
    // results) need the taller sheet so their lists have room to scroll.
    val isOptionsView = subView == AddModalSubView.OPTIONS
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
                .then(if (isOptionsView) Modifier else Modifier.fillMaxHeight(0.88f))
                .padding(20.dp)
        ) {
                // Header with dynamic title and navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (subView != AddModalSubView.OPTIONS) {
                            IconButton(onClick = { onNavigateSubView(AddModalSubView.OPTIONS) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to options"
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Text(
                            text = when (subView) {
                                AddModalSubView.OPTIONS -> "Add Apps & Games"
                                AddModalSubView.ADD_GFN -> "Add GeForce Now Game"
                                AddModalSubView.ADD_WEB_URL -> "Add Web URL"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("add_modal_close_button")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close modal")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content View switcher inside the same modal
                AnimatedContent(
                    targetState = subView,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "modal_view_anim",
                    modifier = if (isOptionsView) Modifier.fillMaxWidth() else Modifier.weight(1f)
                ) { targetView ->
                    when (targetView) {
                        AddModalSubView.OPTIONS -> {
                          OptionsSelectView(
                              onSelectPlayStore = onOpenPlayStore,
                              onSelectGfn = { onNavigateSubView(AddModalSubView.ADD_GFN) },
                              onSelectMoonlight = onOpenMoonlight,
                              onSelectWebUrl = { onNavigateSubView(AddModalSubView.ADD_WEB_URL) }
                          )
                        }
                        AddModalSubView.ADD_WEB_URL -> {
                          AddShortcutFormView(
                              onAddShortcut = onAddShortcut
                          )
                        }
                        AddModalSubView.ADD_GFN -> {
                          AddGfnSearchView(
                              onGameSelected = onAddGfnGame
                          )
                        }
                    }
                }
            }
    }
}

@Composable
private fun OptionsSelectView(
    onSelectPlayStore: () -> Unit,
    onSelectGfn: () -> Unit,
    onSelectMoonlight: () -> Unit,
    onSelectWebUrl: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OptionCard(
            icon = Icons.Default.Storefront,
            title = "Play Store",
            onClick = onSelectPlayStore,
            testTag = "option_play_store"
        )

        OptionCard(
            icon = Icons.Default.Cloud,
            title = "GeForce Now",
            onClick = onSelectGfn,
            testTag = "option_add_gfn"
        )

        OptionCard(
            icon = Icons.Default.Nightlight,
            title = "Moonlight",
            onClick = onSelectMoonlight,
            testTag = "option_add_moonlight"
        )

        OptionCard(
            icon = Icons.Default.Link,
            title = "Web URL",
            onClick = onSelectWebUrl,
            testTag = "option_add_web_url"
        )
    }
}

@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AddShortcutFormView(
    onAddShortcut: (title: String, url: String, posterUrl: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var posterUrl by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Shortcut Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("add_shortcut_title")
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Web URL or Deep Link") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("add_shortcut_url")
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = posterUrl,
            onValueChange = { posterUrl = it },
            label = { Text("Poster Box Art URL (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("add_shortcut_poster")
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (title.isNotBlank() && url.isNotBlank()) {
                    onAddShortcut(title.trim(), url.trim(), posterUrl.trim().ifBlank { null })
                }
            },
            enabled = title.isNotBlank() && url.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_shortcut_submit")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Shortcut to Games", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AddGfnSearchView(
    onGameSelected: (GameItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var gfnTitles by remember { mutableStateOf<List<GameItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery) {
        // Debounce so typing doesn't trigger a burst of catalog/artwork requests.
        isLoading = true
        if (searchQuery.isNotBlank()) delay(300)
        gfnTitles = GeForceNowService.searchOrSyncCatalog(searchQuery)
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search GeForce NOW titles...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("add_gfn_search")
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Loading GeForce NOW titles & box art...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (searchQuery.isNotBlank() && gfnTitles.isEmpty()) {
            // Honest empty state: the title was not found in the GeForce NOW catalog.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "No results",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No GeForce NOW title matching \"$searchQuery\"",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Try a different game title, or add a custom shortcut instead.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().testTag("gfn_poster_grid")
            ) {
                items(gfnTitles, key = { it.id }) { gfnGame ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onGameSelected(gfnGame) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = gfnGame.posterUrl ?: gfnGame.heroBannerUrl,
                                    contentDescription = gfnGame.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "GFN",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = gfnGame.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
