package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GameItem
import com.example.data.service.AmazonLunaService
import com.example.data.service.GeForceNowService
import com.example.ui.viewmodel.AddModalSubView
import kotlinx.coroutines.delay

@Composable
fun AddContentModal(
    subView: AddModalSubView,
    onNavigateSubView: (AddModalSubView) -> Unit,
    onOpenPlayStore: () -> Unit,
    onOpenMoonlight: () -> Unit,
    onAddShortcut: (title: String, url: String, posterUrl: String?) -> Unit,
    onAddGfnGame: (GameItem) -> Unit,
    onAddLunaGame: (GameItem) -> Unit = {},
    onDismiss: () -> Unit
) {
    val handleBack: () -> Unit = {
        if (subView != AddModalSubView.OPTIONS) {
            onNavigateSubView(AddModalSubView.OPTIONS)
        } else {
            onDismiss()
        }
    }

    BackHandler(onBack = handleBack)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Header aligned at top, matching Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier.testTag("add_modal_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (subView != AddModalSubView.OPTIONS) {
                                "Back to options"
                            } else {
                                "Back"
                            },
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (subView) {
                            AddModalSubView.OPTIONS -> "Discover"
                            AddModalSubView.ADD_GFN -> "Add GeForce Now Game"
                            AddModalSubView.ADD_LUNA -> "Add Amazon Luna Game"
                            AddModalSubView.ADD_WEB_URL -> "Add Web URL"
                        },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content area filling remaining height
                AnimatedContent(
                    targetState = subView,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "modal_view_anim",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { targetView ->
                    when (targetView) {
                        AddModalSubView.OPTIONS -> {
                            OptionsSelectView(
                                onSelectPlayStore = onOpenPlayStore,
                                onSelectGfn = { onNavigateSubView(AddModalSubView.ADD_GFN) },
                                onSelectLuna = { onNavigateSubView(AddModalSubView.ADD_LUNA) },
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
                        AddModalSubView.ADD_LUNA -> {
                            AddLunaSearchView(
                                onGameSelected = onAddLunaGame
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class DiscoverOption(
    val icon: ImageVector,
    val title: String,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
private fun OptionsSelectView(
    onSelectPlayStore: () -> Unit,
    onSelectGfn: () -> Unit,
    onSelectLuna: () -> Unit,
    onSelectMoonlight: () -> Unit,
    onSelectWebUrl: () -> Unit
) {
    val options = listOf(
        DiscoverOption(
            icon = Icons.Default.Storefront,
            title = "Play Store",
            testTag = "option_play_store",
            onClick = onSelectPlayStore
        ),
        DiscoverOption(
            icon = Icons.Default.Cloud,
            title = "GeForce Now",
            testTag = "option_add_gfn",
            onClick = onSelectGfn
        ),
        DiscoverOption(
            icon = Icons.Default.VideogameAsset,
            title = "Amazon Luna",
            testTag = "option_add_luna",
            onClick = onSelectLuna
        ),
        DiscoverOption(
            icon = Icons.Default.Nightlight,
            title = "Moonlight",
            testTag = "option_add_moonlight",
            onClick = onSelectMoonlight
        ),
        DiscoverOption(
            icon = Icons.Default.Link,
            title = "Web URL",
            testTag = "option_add_web_url",
            onClick = onSelectWebUrl
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTwoColumns = maxWidth > maxHeight || maxWidth >= 520.dp
        val viewportHeight = maxHeight

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isTwoColumns) {
                    options.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            rowItems.forEach { option ->
                                OptionCard(
                                    icon = option.icon,
                                    title = option.title,
                                    onClick = option.onClick,
                                    testTag = option.testTag,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    options.forEach { option ->
                        OptionCard(
                            icon = option.icon,
                            title = option.title,
                            onClick = option.onClick,
                            testTag = option.testTag,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        shape = shape,
        modifier = modifier.testTag(testTag)
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight),
                verticalArrangement = Arrangement.Center
            ) {
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

                Spacer(modifier = Modifier.height(20.dp))

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

@Composable
internal fun AddLunaSearchView(
    onGameSelected: (GameItem) -> Unit,
    catalogLoader: suspend (String) -> Result<List<GameItem>> = { query ->
        AmazonLunaService.searchCatalog(query)
    },
    posterResolver: suspend (GameItem) -> String? = { game ->
        AmazonLunaService.resolvePosterForTitle(game.title, allowFallbackSearch = false)
    }
) {
    var searchQuery by remember { mutableStateOf("") }
    var lunaTitles by remember { mutableStateOf<List<GameItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(searchQuery, reloadTrigger) {
        isLoading = true
        errorMessage = null
        if (searchQuery.isNotBlank()) delay(300)
        val result = catalogLoader(searchQuery)
        result
            .onSuccess { items ->
                lunaTitles = items
                errorMessage = null
            }
            .onFailure { err ->
                lunaTitles = emptyList()
                errorMessage = err.message ?: "Unable to load Amazon Luna catalog."
            }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Amazon Luna titles...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_luna_search")
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (searchQuery.isBlank() && !isLoading && errorMessage == null && lunaTitles.isNotEmpty()) {
            Text(
                text = "Free with Amazon Prime (${lunaTitles.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("luna_prime_header")
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isBlank()) {
                            "Loading free Prime games on Amazon Luna..."
                        } else {
                            "Searching Amazon Luna catalog..."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Catalog unavailable",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage ?: "Unable to load Amazon Luna catalog.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { reloadTrigger += 1 },
                    modifier = Modifier.testTag("luna_retry_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        } else if (lunaTitles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideogameAsset,
                    contentDescription = "No results",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (searchQuery.isNotBlank()) {
                        "No Amazon Luna title matching \"$searchQuery\""
                    } else {
                        "No Prime-included Amazon Luna games found"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
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
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("luna_poster_grid")
            ) {
                items(lunaTitles, key = { it.id }) { lunaGame ->
                    LunaPosterOptionItem(
                        game = lunaGame,
                        posterResolver = posterResolver,
                        onSelect = onGameSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun LunaPosterOptionItem(
    game: GameItem,
    posterResolver: suspend (GameItem) -> String?,
    onSelect: (GameItem) -> Unit
) {
    var resolvedPoster by remember(game.id, game.posterUrl) { mutableStateOf(game.posterUrl) }
    var imageFailed by remember(resolvedPoster) { mutableStateOf(false) }

    LaunchedEffect(game.id) {
        if (resolvedPoster.isNullOrBlank()) {
            val fetched = runCatching { posterResolver(game) }.getOrNull()
            if (!fetched.isNullOrBlank()) {
                resolvedPoster = fetched
            }
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                val chosen = if (!resolvedPoster.isNullOrBlank()) {
                    game.copy(posterUrl = resolvedPoster)
                } else {
                    game
                }
                onSelect(chosen)
            }
            .padding(4.dp)
            .testTag("luna_game_${game.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val artUrl = resolvedPoster ?: game.heroBannerUrl
                if (!artUrl.isNullOrBlank() && !imageFailed) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = game.title,
                        contentScale = ContentScale.Crop,
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.VideogameAsset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LUNA",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = game.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
