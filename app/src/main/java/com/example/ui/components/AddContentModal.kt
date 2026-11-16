package com.example.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.model.GameItem
import com.example.data.service.AmazonLunaService
import com.example.data.service.AppIconStore
import com.example.data.service.GeForceNowService
import com.example.data.service.InstalledAppsManager
import com.example.data.service.MOONLIGHT_PACKAGES
import com.example.ui.viewmodel.AddModalSubView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
                val primaryFocusRequesters = remember {
                    AddModalSubView.entries.associateWith { FocusRequester() }
                }
                val activePrimaryFocusRequester = primaryFocusRequesters.getValue(subView)

                // Header aligned at top, matching Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val backInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = handleBack,
                        interactionSource = backInteraction,
                        modifier = Modifier
                            .tvFocusBorder(backInteraction, shape = CircleShape)
                            .focusProperties {
                                down = activePrimaryFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                    runCatching { activePrimaryFocusRequester.requestFocus() }
                                    true
                                } else {
                                    false
                                }
                            }
                            .testTag("add_modal_close_button")
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
                    val targetPrimaryFocusRequester = primaryFocusRequesters.getValue(targetView)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusProperties { canFocus = (subView == targetView) }
                            .focusGroup()
                    ) {
                        when (targetView) {
                            AddModalSubView.OPTIONS -> {
                                OptionsSelectView(
                                    onSelectPlayStore = onOpenPlayStore,
                                    onSelectGfn = { onNavigateSubView(AddModalSubView.ADD_GFN) },
                                    onSelectLuna = { onNavigateSubView(AddModalSubView.ADD_LUNA) },
                                    onSelectMoonlight = onOpenMoonlight,
                                    onSelectWebUrl = { onNavigateSubView(AddModalSubView.ADD_WEB_URL) },
                                    firstOptionFocusRequester = targetPrimaryFocusRequester
                                )
                            }
                            AddModalSubView.ADD_WEB_URL -> {
                                AddShortcutFormView(
                                    onAddShortcut = onAddShortcut,
                                    titleFocusRequester = targetPrimaryFocusRequester
                                )
                            }
                            AddModalSubView.ADD_GFN -> {
                                AddGfnSearchView(
                                    onGameSelected = onAddGfnGame,
                                    searchFocusRequester = targetPrimaryFocusRequester
                                )
                            }
                            AddModalSubView.ADD_LUNA -> {
                                AddLunaSearchView(
                                    onGameSelected = onAddLunaGame,
                                    searchFocusRequester = targetPrimaryFocusRequester
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val GEFORCE_NOW_PACKAGE_NAME = "com.nvidia.geforcenow"
private const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"

private data class DiscoverOption(
    val icon: ImageVector,
    val title: String,
    val testTag: String,
    val onClick: () -> Unit
)

internal suspend fun resolveDiscoverOptionIcons(
    context: Context,
    themedIcons: Map<String, String>
): Map<String, String> = withContext(Dispatchers.IO) {
    val installedAppsManager = InstalledAppsManager(context)
    val lunaPackages = buildList {
        add(AmazonLunaService.LUNA_PACKAGE_NAME)
        installedAppsManager.findInstalledLunaPwaPackage()?.let(::add)
    }
    val mappings = listOf(
        "option_add_luna" to lunaPackages,
        "option_add_gfn" to listOf(GEFORCE_NOW_PACKAGE_NAME),
        "option_add_moonlight" to MOONLIGHT_PACKAGES,
        "option_play_store" to listOf(PLAY_STORE_PACKAGE_NAME)
    )
    buildMap {
        for ((tag, candidates) in mappings) {
            val url = resolveFirstInstalledPackageIconUrl(context, candidates, themedIcons)
            if (!url.isNullOrBlank()) {
                put(tag, url)
            }
        }
    }
}

private suspend fun resolveFirstInstalledPackageIconUrl(
    context: Context,
    packages: List<String>,
    themedIcons: Map<String, String>
): String? {
    val pm = context.packageManager
    for (pkg in packages) {
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        } ?: continue

        val themedUrl = themedIcons["app_$pkg"] ?: themedIcons[pkg]
        if (!themedUrl.isNullOrBlank()) return themedUrl

        val iconUrl = AppIconStore.iconUrl(context, pkg, packageInfo.lastUpdateTime)
        if (!iconUrl.isNullOrBlank()) return iconUrl
    }
    return null
}

@Composable
internal fun OptionsSelectView(
    onSelectPlayStore: () -> Unit,
    onSelectGfn: () -> Unit,
    onSelectLuna: () -> Unit,
    onSelectMoonlight: () -> Unit,
    onSelectWebUrl: () -> Unit,
    firstOptionFocusRequester: FocusRequester = remember { FocusRequester() },
    iconResolver: suspend (Context, Map<String, String>) -> Map<String, String> = { ctx, themed ->
        resolveDiscoverOptionIcons(ctx, themed)
    }
) {
    val context = LocalContext.current
    val themedIcons = LocalThemedAppIcons.current
    var resolvedIcons by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(context, themedIcons) {
        resolvedIcons = runCatching { iconResolver(context, themedIcons) }
            .getOrDefault(emptyMap())
    }

    val options = listOf(
        DiscoverOption(
            icon = Icons.Default.VideogameAsset,
            title = "Amazon Luna",
            testTag = "option_add_luna",
            onClick = onSelectLuna
        ),
        DiscoverOption(
            icon = Icons.Default.Cloud,
            title = "GeForce Now",
            testTag = "option_add_gfn",
            onClick = onSelectGfn
        ),
        DiscoverOption(
            icon = Icons.Default.Nightlight,
            title = "Moonlight",
            testTag = "option_add_moonlight",
            onClick = onSelectMoonlight
        ),
        DiscoverOption(
            icon = Icons.Default.Storefront,
            title = "Play Store",
            testTag = "option_play_store",
            onClick = onSelectPlayStore
        ),
        DiscoverOption(
            icon = Icons.Default.Link,
            title = "Web URL",
            testTag = "option_add_web_url",
            onClick = onSelectWebUrl
        )
    )

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstOptionFocusRequester.requestFocus() }
    }

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
                    options.chunked(2).forEachIndexed { rowIndex, rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            rowItems.forEachIndexed { colIndex, option ->
                                OptionCard(
                                    icon = option.icon,
                                    appIconUrl = resolvedIcons[option.testTag],
                                    title = option.title,
                                    onClick = option.onClick,
                                    testTag = option.testTag,
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(
                                            if (rowIndex == 0 && colIndex == 0) {
                                                Modifier.focusRequester(firstOptionFocusRequester)
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    options.forEachIndexed { index, option ->
                        OptionCard(
                            icon = option.icon,
                            appIconUrl = resolvedIcons[option.testTag],
                            title = option.title,
                            onClick = option.onClick,
                            testTag = option.testTag,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(firstOptionFocusRequester) else Modifier)
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
    appIconUrl: String?,
    title: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(density) { with(density) { 54.dp.roundToPx().coerceAtLeast(1) } }
    var imageFailed by remember(appIconUrl) { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
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
            if (!appIconUrl.isNullOrBlank() && !imageFailed) {
                val request = remember(context, appIconUrl, sizePx) {
                    ImageRequest.Builder(context)
                        .data(appIconUrl)
                        .size(sizePx, sizePx)
                        .precision(Precision.INEXACT)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .testTag("${testTag}_app_icon")
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .testTag("${testTag}_symbolic_icon"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
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
    onAddShortcut: (title: String, url: String, posterUrl: String?) -> Unit,
    titleFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var posterUrl by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { titleFocusRequester.requestFocus() }
    }

    val fieldDpadNavigation = Modifier.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.DirectionDown -> {
                    keyboardController?.hide()
                    focusManager.moveFocus(FocusDirection.Down)
                    true
                }
                Key.DirectionUp -> {
                    keyboardController?.hide()
                    focusManager.moveFocus(FocusDirection.Up)
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }

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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .then(fieldDpadNavigation)
                        .testTag("add_shortcut_title")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Web URL or Deep Link") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(fieldDpadNavigation)
                        .testTag("add_shortcut_url")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = posterUrl,
                    onValueChange = { posterUrl = it },
                    label = { Text("Poster Box Art URL (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(fieldDpadNavigation)
                        .testTag("add_shortcut_poster")
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
internal fun AddGfnSearchView(
    onGameSelected: (GameItem) -> Unit,
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    catalogLoader: suspend (String) -> GeForceNowService.GfnBrowseResult = { query ->
        GeForceNowService.browseOrSearchCatalog(query)
    }
) {
    var searchQuery by remember { mutableStateOf("") }
    var gfnTitles by remember { mutableStateOf<List<GameItem>>(emptyList()) }
    var defaultClassification by remember {
        mutableStateOf(GeForceNowService.GfnDefaultClassification.RECENTLY_ADDED)
    }
    var isLoading by remember { mutableStateOf(true) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val firstPosterFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { searchFocusRequester.requestFocus() }
    }

    LaunchedEffect(searchQuery) {
        // Debounce so typing doesn't trigger a burst of catalog/artwork requests.
        isLoading = true
        if (searchQuery.isNotBlank()) delay(300)
        val result = runCatching { catalogLoader(searchQuery) }.getOrNull()
        gfnTitles = result?.games.orEmpty()
        if (result != null) {
            defaultClassification = result.classification
        }
        isLoading = false
    }

    val moveFromSearchDown = {
        keyboardController?.hide()
        if (!isLoading && gfnTitles.isNotEmpty()) {
            val moved = runCatching { firstPosterFocusRequester.requestFocus() }.isSuccess
            if (!moved) focusManager.moveFocus(FocusDirection.Down)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search GeForce NOW titles...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    val clearInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { searchQuery = "" },
                        interactionSource = clearInteraction,
                        modifier = Modifier
                            .tvFocusBorder(clearInteraction, shape = CircleShape)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                    moveFromSearchDown()
                                    true
                                } else {
                                    false
                                }
                            }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { moveFromSearchDown() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocusRequester)
                .focusProperties {
                    down = if (!isLoading && gfnTitles.isNotEmpty()) {
                        firstPosterFocusRequester
                    } else {
                        FocusRequester.Default
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> {
                                moveFromSearchDown()
                                true
                            }
                            Key.DirectionUp -> {
                                keyboardController?.hide()
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .testTag("add_gfn_search")
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (searchQuery.isBlank() && !isLoading && gfnTitles.isNotEmpty()) {
            Text(
                text = when (defaultClassification) {
                    GeForceNowService.GfnDefaultClassification.RECENTLY_ADDED ->
                        "Recently added to GeForce NOW (${gfnTitles.size})"
                    GeForceNowService.GfnDefaultClassification.CATALOG_FALLBACK ->
                        "GeForce NOW Catalog (${gfnTitles.size})"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("gfn_default_header")
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isBlank()) {
                            "Loading recently added GeForce NOW games..."
                        } else {
                            "Searching GeForce NOW titles & box art..."
                        },
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
                itemsIndexed(gfnTitles, key = { _, it -> it.id }) { index, gfnGame ->
                    val itemInteraction = remember { MutableInteractionSource() }
                    val itemFocused by itemInteraction.collectIsFocusedAsState()
                    Column(
                        modifier = Modifier
                            .then(
                                if (index == 0) Modifier.focusRequester(firstPosterFocusRequester)
                                else Modifier
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (itemFocused) {
                                    Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                        .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(interactionSource = itemInteraction, indication = null) {
                                onGameSelected(gfnGame)
                            }
                            .padding(4.dp)
                            .testTag("gfn_game_${gfnGame.id}"),
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
                                val artUrl = gfnGame.posterUrl ?: gfnGame.heroBannerUrl
                                var imageFailed by remember(artUrl) { mutableStateOf(false) }
                                if (!artUrl.isNullOrBlank() && !imageFailed) {
                                    AsyncImage(
                                        model = artUrl,
                                        contentDescription = gfnGame.title,
                                        contentScale = ContentScale.Crop,
                                        onError = { imageFailed = true },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
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
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
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

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val firstPosterFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { searchFocusRequester.requestFocus() }
    }

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

    val moveFromSearchDown = {
        keyboardController?.hide()
        when {
            !isLoading && errorMessage != null -> {
                val moved = runCatching { retryFocusRequester.requestFocus() }.isSuccess
                if (!moved) focusManager.moveFocus(FocusDirection.Down)
            }
            !isLoading && lunaTitles.isNotEmpty() -> {
                val moved = runCatching { firstPosterFocusRequester.requestFocus() }.isSuccess
                if (!moved) focusManager.moveFocus(FocusDirection.Down)
            }
            else -> Unit
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Amazon Luna titles...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    val clearInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { searchQuery = "" },
                        interactionSource = clearInteraction,
                        modifier = Modifier
                            .tvFocusBorder(clearInteraction, shape = CircleShape)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                    moveFromSearchDown()
                                    true
                                } else {
                                    false
                                }
                            }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { moveFromSearchDown() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocusRequester)
                .focusProperties {
                    down = when {
                        !isLoading && errorMessage != null -> retryFocusRequester
                        !isLoading && lunaTitles.isNotEmpty() -> firstPosterFocusRequester
                        else -> FocusRequester.Default
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> {
                                moveFromSearchDown()
                                true
                            }
                            Key.DirectionUp -> {
                                keyboardController?.hide()
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
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
                val retryInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = { reloadTrigger += 1 },
                    interactionSource = retryInteraction,
                    modifier = Modifier
                        .focusRequester(retryFocusRequester)
                        .tvFocusBorder(retryInteraction, shape = CircleShape)
                        .testTag("luna_retry_button")
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
                itemsIndexed(lunaTitles, key = { _, it -> it.id }) { index, lunaGame ->
                    LunaPosterOptionItem(
                        game = lunaGame,
                        posterResolver = posterResolver,
                        onSelect = onGameSelected,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstPosterFocusRequester)
                        } else {
                            Modifier
                        }
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
    onSelect: (GameItem) -> Unit,
    modifier: Modifier = Modifier
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

    val itemInteraction = remember { MutableInteractionSource() }
    val itemFocused by itemInteraction.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (itemFocused) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = itemInteraction, indication = null) {
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
