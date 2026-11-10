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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.GameItem
import com.example.data.service.GeForceNowService
import com.example.ui.theme.AppIconShape
import com.example.ui.viewmodel.AddModalSubView

@Composable
fun AddContentModal(
    subView: AddModalSubView,
    installedApps: List<GameItem>,
    iconShape: AppIconShape,
    useThemedIcons: Boolean,
    onNavigateSubView: (AddModalSubView) -> Unit,
    onAddApp: (GameItem) -> Unit,
    onAddShortcut: (title: String, url: String, posterUrl: String?) -> Unit,
    onAddGfnGame: (GameItem) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                                AddModalSubView.OPTIONS -> "Add Content"
                                AddModalSubView.ADD_APP -> "Add Installed App"
                                AddModalSubView.ADD_SHORTCUT -> "Add Custom Shortcut"
                                AddModalSubView.ADD_GFN -> "Add GeForce NOW Title"
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
                    modifier = Modifier.weight(1f)
                ) { targetView ->
                    when (targetView) {
                        AddModalSubView.OPTIONS -> {
                          OptionsSelectView(
                              onSelectApp = { onNavigateSubView(AddModalSubView.ADD_APP) },
                              onSelectShortcut = { onNavigateSubView(AddModalSubView.ADD_SHORTCUT) },
                              onSelectGfn = { onNavigateSubView(AddModalSubView.ADD_GFN) }
                          )
                        }
                        AddModalSubView.ADD_APP -> {
                          AddAppGridView(
                              apps = installedApps,
                              iconShape = iconShape,
                              useThemedIcons = useThemedIcons,
                              onAppSelected = onAddApp
                          )
                        }
                        AddModalSubView.ADD_SHORTCUT -> {
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
}

@Composable
private fun OptionsSelectView(
    onSelectApp: () -> Unit,
    onSelectShortcut: () -> Unit,
    onSelectGfn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OptionCard(
            icon = Icons.Default.Apps,
            title = "Installed Application",
            description = "Browse all installed apps on your device and add them directly to your Games view",
            badge = "App Grid",
            onClick = onSelectApp,
            testTag = "option_add_app"
        )

        OptionCard(
            icon = Icons.Default.Link,
            title = "Custom Web / Cloud Shortcut",
            description = "Add URL links, remote play services, or custom gaming shortcuts with custom posters",
            badge = "Shortcut",
            onClick = onSelectShortcut,
            testTag = "option_add_shortcut"
        )

        OptionCard(
            icon = Icons.Default.Cloud,
            title = "GeForce NOW Game",
            description = "Search the GeForce NOW library and add games with deep links for full-screen cloud streaming",
            badge = "GeForce NOW",
            onClick = onSelectGfn,
            testTag = "option_add_gfn"
        )
    }
}

@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    badge: String,
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

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddAppGridView(
    apps: List<GameItem>,
    iconShape: AppIconShape,
    useThemedIcons: Boolean,
    onAppSelected: (GameItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = if (searchQuery.isBlank()) apps else apps.filter {
        it.title.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter installed apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("add_app_search")
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 88.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("installed_apps_grid")
        ) {
            items(filteredApps, key = { it.id }) { app ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAppSelected(app) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(iconShape.toComposeShape())
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        val iconUrl = app.iconUrl ?: app.posterUrl
                        if (!iconUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(iconUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = app.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(60.dp)
                            )
                        } else {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = app.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
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

@Composable
private fun AddShortcutFormView(
    onAddShortcut: (title: String, url: String, posterUrl: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var posterUrl by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Popular Cloud & Streaming Presets",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetPill("Xbox Cloud") {
                title = "Xbox Cloud Gaming"
                url = "https://www.xbox.com/play"
                posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co5v43.jpg"
            }
            PresetPill("Amazon Luna") {
                title = "Amazon Luna"
                url = "https://luna.amazon.com"
                posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co2m37.jpg"
            }
            PresetPill("Discord") {
                title = "Discord"
                url = "https://discord.com/app"
                posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co41ea.jpg"
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
private fun PresetPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddGfnSearchView(
    onGameSelected: (GameItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var gfnTitles by remember { mutableStateOf(GeForceNowService.DEFAULT_GFN_LIBRARY) }

    LaunchedEffect(searchQuery) {
        gfnTitles = GeForceNowService.searchOrSyncCatalog(searchQuery)
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
