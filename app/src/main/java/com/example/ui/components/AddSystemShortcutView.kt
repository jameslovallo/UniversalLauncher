package com.example.ui.components

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.data.service.SystemShortcutAppGroup
import com.example.data.service.SystemShortcutCatalog
import com.example.data.service.SystemShortcutEntry
import com.example.data.service.SystemShortcutKind
import com.example.data.service.SystemShortcutService

@Composable
internal fun AddSystemShortcutView(
    selectedAppPackage: String?,
    onSelectAppPackage: (String?) -> Unit,
    onAddLauncherShortcut: (SystemShortcutEntry) -> Unit,
    onCreateShortcutResult: (providerEntry: SystemShortcutEntry, resultIntent: Intent, onInvalid: () -> Unit) -> Unit,
    onOpenHomeSettings: (() -> Unit)? = null,
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    catalogLoader: suspend (Context) -> SystemShortcutCatalog = { ctx ->
        SystemShortcutService.loadCatalog(ctx)
    },
    iconResolver: suspend (Context, SystemShortcutEntry) -> String? = { ctx, entry ->
        SystemShortcutService.resolvePreviewIconUrl(ctx, entry)
    }
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf<SystemShortcutCatalog?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingCreateEntry by remember { mutableStateOf<SystemShortcutEntry?>(null) }

    val firstItemFocusRequester = remember { FocusRequester() }
    val allAppsFocusRequester = remember { FocusRequester() }

    val createShortcutLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val provider = pendingCreateEntry
        pendingCreateEntry = null
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && provider != null && data != null) {
            onCreateShortcutResult(provider, data) {
                errorMessage = "Could not create shortcut from ${provider.appLabel}."
            }
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { searchFocusRequester.requestFocus() }
    }

    LaunchedEffect(context) {
        isLoading = true
        catalog = runCatching { catalogLoader(context) }.getOrNull()
        isLoading = false
    }

    val isSearching = searchQuery.isNotBlank()
    val selectedGroup = remember(catalog, selectedAppPackage) {
        if (selectedAppPackage == null) {
            null
        } else {
            catalog?.appGroups?.firstOrNull { it.packageName == selectedAppPackage }
        }
    }
    val searchResults = remember(catalog, searchQuery) {
        val current = catalog ?: return@remember emptyList()
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            SystemShortcutService.filterShortcuts(current, searchQuery)
        }
    }

    // System Back steps out of an app's shortcut list back to All Apps before leaving this view.
    BackHandler(enabled = !isSearching && selectedGroup != null) {
        onSelectAppPackage(null)
    }

    val handleEntryClick: (SystemShortcutEntry) -> Unit = { entry ->
        errorMessage = null
        when (entry.kind) {
            SystemShortcutKind.LAUNCHER_SHORTCUT -> onAddLauncherShortcut(entry)
            SystemShortcutKind.CREATE_SHORTCUT_ACTIVITY -> {
                val activityName = entry.activityName
                if (activityName.isNullOrBlank()) {
                    errorMessage = "Shortcut activity unavailable for ${entry.title}."
                } else {
                    val launchIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                        component = ComponentName(entry.packageName, activityName)
                    }
                    pendingCreateEntry = entry
                    runCatching {
                        createShortcutLauncher.launch(launchIntent)
                    }.onFailure {
                        pendingCreateEntry = null
                        errorMessage = "Unable to open shortcut picker for ${entry.appLabel}."
                    }
                }
            }
        }
    }

    val moveFromSearchDown = {
        keyboardController?.hide()
        val hasTarget = when {
            isLoading -> false
            isSearching -> searchResults.isNotEmpty()
            selectedGroup != null -> true
            else -> catalog?.appGroups?.isNotEmpty() == true
        }
        if (hasTarget) {
            val target = if (!isSearching && selectedGroup != null) {
                allAppsFocusRequester
            } else {
                firstItemFocusRequester
            }
            val moved = runCatching { target.requestFocus() }.isSuccess
            if (!moved) focusManager.moveFocus(FocusDirection.Down)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search shortcuts or apps...") },
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
                        !isLoading && !isSearching && selectedGroup != null -> allAppsFocusRequester
                        !isLoading && ((isSearching && searchResults.isNotEmpty()) ||
                            (!isSearching && catalog?.appGroups?.isNotEmpty() == true)) -> firstItemFocusRequester
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
                .testTag("add_system_shortcut_search")
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val loadedCatalog = catalog
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Loading system shortcuts...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            isSearching -> {
                if (searchResults.isEmpty()) {
                    SystemShortcutEmptyState(
                        title = "No shortcut matching \"$searchQuery\"",
                        subtitle = "Try another app name or shortcut keyword."
                    )
                } else {
                    Text(
                        text = "${searchResults.size} matching ${if (searchResults.size == 1) "shortcut" else "shortcuts"}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("system_shortcut_results_list")
                    ) {
                        itemsIndexed(searchResults, key = { _, it -> it.id }) { index, entry ->
                            val subtitle = buildString {
                                append(entry.appLabel)
                                entry.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                                    append(" • ")
                                    append(sub)
                                }
                            }
                            SystemShortcutItemRow(
                                entry = entry,
                                subtitleText = subtitle,
                                iconResolver = iconResolver,
                                onClick = { handleEntryClick(entry) },
                                modifier = Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                    .testTag("system_shortcut_result_${entry.id}")
                            )
                        }
                    }
                }
            }

            selectedGroup != null -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val allAppsInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = { onSelectAppPackage(null) },
                        interactionSource = allAppsInteraction,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .focusRequester(allAppsFocusRequester)
                            .tvFocusBorder(allAppsInteraction, shape = RoundedCornerShape(12.dp))
                            .testTag("system_shortcut_all_apps_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to apps",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("All Apps", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    ShortcutThumbnail(
                        imageUrl = selectedGroup.appIconUrl,
                        sizeDp = 32,
                        contentDescription = null
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedGroup.appLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val count = selectedGroup.shortcuts.size
                        Text(
                            text = "$count ${if (count == 1) "shortcut" else "shortcuts"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("system_shortcut_app_items_list")
                ) {
                    itemsIndexed(selectedGroup.shortcuts, key = { _, it -> it.id }) { index, entry ->
                        SystemShortcutItemRow(
                            entry = entry,
                            subtitleText = entry.subtitle,
                            iconResolver = iconResolver,
                            onClick = { handleEntryClick(entry) },
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                .testTag("system_shortcut_item_${entry.id}")
                        )
                    }
                }
            }

            loadedCatalog == null || loadedCatalog.appGroups.isEmpty() -> {
                SystemShortcutEmptyState(
                    title = "No system shortcuts found",
                    subtitle = if (loadedCatalog?.hasShortcutHostPermission == false) {
                        "Set Nexus as your default Home app to browse per-app quick shortcuts."
                    } else {
                        "Installed apps have not published any system shortcuts yet."
                    },
                    actionLabel = if (loadedCatalog?.hasShortcutHostPermission == false && onOpenHomeSettings != null) {
                        "Open Home Settings"
                    } else {
                        null
                    },
                    onAction = onOpenHomeSettings
                )
            }

            else -> {
                if (!loadedCatalog.hasShortcutHostPermission) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("system_shortcut_host_hint")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Set Nexus as default Home app to also browse per-app quick shortcuts.",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (onOpenHomeSettings != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                val homeSettingsInteraction = remember { MutableInteractionSource() }
                                TextButton(
                                    onClick = onOpenHomeSettings,
                                    interactionSource = homeSettingsInteraction,
                                    modifier = Modifier
                                        .tvFocusBorder(homeSettingsInteraction, shape = RoundedCornerShape(10.dp))
                                ) {
                                    Text("Home App", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Browse by App (${loadedCatalog.appGroups.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag("system_shortcut_browse_header")
                )

                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("system_shortcut_apps_list")
                ) {
                    itemsIndexed(loadedCatalog.appGroups, key = { _, it -> it.packageName }) { index, group ->
                        SystemShortcutAppRow(
                            group = group,
                            onClick = { onSelectAppPackage(group.packageName) },
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                .testTag("shortcut_app_${group.packageName}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemShortcutAppRow(
    group: SystemShortcutAppGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val count = group.shortcuts.size

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShortcutThumbnail(
                imageUrl = group.appIconUrl,
                sizeDp = 42,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.appLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count ${if (count == 1) "shortcut" else "shortcuts"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SystemShortcutItemRow(
    entry: SystemShortcutEntry,
    subtitleText: String?,
    iconResolver: suspend (Context, SystemShortcutEntry) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var resolvedIconUrl by remember(entry.id, entry.appIconUrl) { mutableStateOf(entry.appIconUrl) }

    LaunchedEffect(entry.id) {
        val preview = runCatching { iconResolver(context, entry) }.getOrNull()
        if (!preview.isNullOrBlank()) {
            resolvedIconUrl = preview
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShortcutThumbnail(
                imageUrl = resolvedIconUrl,
                sizeDp = 40,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitleText.isNullOrBlank()) {
                    Text(
                        text = subtitleText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (entry.kind == SystemShortcutKind.CREATE_SHORTCUT_ACTIVITY) {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                } else {
                    Icons.Default.Add
                },
                contentDescription = if (entry.kind == SystemShortcutKind.CREATE_SHORTCUT_ACTIVITY) {
                    "Configure ${entry.title}"
                } else {
                    "Add ${entry.title}"
                },
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ShortcutThumbnail(
    imageUrl: String?,
    sizeDp: Int,
    contentDescription: String?
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(density, sizeDp) { with(density) { sizeDp.dp.roundToPx().coerceAtLeast(1) } }
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }

    if (!imageUrl.isNullOrBlank() && !imageFailed) {
        val request = remember(context, imageUrl, sizePx) {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .size(sizePx, sizePx)
                .precision(Precision.INEXACT)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onError = { imageFailed = true },
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size((sizeDp * 0.58f).dp)
            )
        }
    }
}

@Composable
private fun SystemShortcutEmptyState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            val actionInteraction = remember { MutableInteractionSource() }
            OutlinedButton(
                onClick = onAction,
                interactionSource = actionInteraction,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .tvFocusBorder(actionInteraction, shape = CircleShape)
            ) {
                Text(actionLabel)
            }
        }
    }
}
