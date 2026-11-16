package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GameItem
import com.example.ui.theme.FavoritesLayout
import kotlinx.coroutines.delay

/**
 * Bottom sheet presenting a folder's apps identically to the favorites and app drawer
 * (`AppTextRow` in TEXT mode, `AppIconTile` grid in ICONS mode, `AppTvCard` 16:9 grid in
 * TV_CARDS mode), without alphabetical section headers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContentsSheet(
    folder: GameItem,
    apps: List<GameItem>,
    layout: FavoritesLayout,
    focusedItemId: String?,
    showFocusEffects: Boolean,
    onAppClick: (GameItem) -> Unit,
    onAppLongClick: (GameItem) -> Unit,
    onCustomizeFolder: () -> Unit,
    onManageFolderApps: () -> Unit,
    onDismiss: () -> Unit,
    onAppFocus: (GameItem) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(folder.id, apps.isEmpty()) {
        delay(80)
        runCatching { initialFocusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = if (layout == FavoritesLayout.TV_CARDS) 920.dp else BottomSheetDefaults.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .testTag("folder_contents_sheet_${folder.id}")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val headerInteraction = remember { MutableInteractionSource() }
                val manageInteraction = remember { MutableInteractionSource() }
                val closeInteraction = remember { MutableInteractionSource() }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .tvFocusBorder(headerInteraction, shape = RoundedCornerShape(16.dp))
                        .clickable(interactionSource = headerInteraction, indication = null, onClick = onCustomizeFolder)
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .testTag("folder_sheet_header"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app = folder, size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${apps.size} app${if (apps.size == 1) "" else "s"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onManageFolderApps,
                    interactionSource = manageInteraction,
                    modifier = Modifier
                        .tvFocusBorder(manageInteraction, shape = CircleShape)
                        .testTag("folder_sheet_manage")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Manage apps in folder",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    interactionSource = closeInteraction,
                    modifier = Modifier
                        .tvFocusBorder(closeInteraction, shape = CircleShape)
                        .testTag("folder_sheet_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close folder"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (apps.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No apps in this folder yet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onManageFolderApps,
                        modifier = Modifier
                            .focusRequester(initialFocusRequester)
                            .testTag("folder_empty_add_apps")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Apps")
                    }
                }
            } else if (layout == FavoritesLayout.TV_CARDS) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    val availableWidth = maxWidth
                    val spacing = 12.dp
                    val minCardWidth = 156.dp
                    val columns = minOf(
                        TV_FAVORITES_MAX_COLUMNS,
                        maxOf(2, ((availableWidth + spacing) / (minCardWidth + spacing)).toInt())
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("folder_contents_grid")
                    ) {
                        gridItemsIndexed(
                            items = apps,
                            key = { _, app -> app.id }
                        ) { index, app ->
                            val handleClick = remember(app, onAppClick) { { onAppClick(app) } }
                            val handleLongClick = remember(app, onAppLongClick) { { onAppLongClick(app) } }
                            val handleFocus = remember(app, onAppFocus) { { onAppFocus(app) } }
                            AppTvCard(
                                app = app,
                                isFocused = app.id == focusedItemId,
                                showFocusEffects = showFocusEffects,
                                testTag = "folder_app_row_${app.id}",
                                onClick = handleClick,
                                onLongClick = handleLongClick,
                                onFocus = handleFocus,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            )
                        }
                    }
                }
            } else if (layout == FavoritesLayout.ICONS) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    val availableWidth = maxWidth
                    val tileMinSize = 78.dp
                    val columns = maxOf(3, (availableWidth / tileMinSize).toInt())

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("folder_contents_grid")
                    ) {
                        gridItemsIndexed(
                            items = apps,
                            key = { _, app -> app.id }
                        ) { index, app ->
                            val handleClick = remember(app, onAppClick) { { onAppClick(app) } }
                            val handleLongClick = remember(app, onAppLongClick) { { onAppLongClick(app) } }
                            val handleFocus = remember(app, onAppFocus) { { onAppFocus(app) } }
                            AppIconTile(
                                app = app,
                                isFocused = app.id == focusedItemId,
                                showFocusEffects = showFocusEffects,
                                testTag = "folder_app_row_${app.id}",
                                onClick = handleClick,
                                onLongClick = handleLongClick,
                                onFocus = handleFocus,
                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .testTag("folder_contents_list")
                ) {
                    itemsIndexed(
                        items = apps,
                        key = { _, app -> app.id }
                    ) { index, app ->
                        val handleClick = remember(app, onAppClick) { { onAppClick(app) } }
                        val handleLongClick = remember(app, onAppLongClick) { { onAppLongClick(app) } }
                        val handleFocus = remember(app, onAppFocus) { { onAppFocus(app) } }
                        AppTextRow(
                            app = app,
                            isFocused = app.id == focusedItemId,
                            showFocusEffects = showFocusEffects,
                            testTag = "folder_app_row_${app.id}",
                            onClick = handleClick,
                            onLongClick = handleLongClick,
                            onFocus = handleFocus,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet to move an app into an existing folder or create a new folder for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    targetItem: GameItem,
    folders: List<GameItem>,
    folderMembersMap: Map<String, List<GameItem>>,
    onSelectFolder: (folderId: String) -> Unit,
    onCreateFolder: (title: String) -> Unit,
    onRemoveFromFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isTv = isTelevisionMode()
    var newFolderName by remember { mutableStateOf("") }
    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(targetItem.id) {
        delay(80)
        runCatching { initialFocusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = if (isTv) 760.dp else BottomSheetDefaults.SheetMaxWidth,
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
            Text(
                text = "Move to Folder",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = targetItem.title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("New folder name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newFolderName.isNotBlank()) {
                                onCreateFolder(newFolderName.trim())
                            }
                        }
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = null)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .then(if (folders.isEmpty()) Modifier.focusRequester(initialFocusRequester) else Modifier)
                        .testTag("folder_create_name_input")
                )
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName.trim())
                        }
                    },
                    enabled = newFolderName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("folder_create_submit")
                ) {
                    Text("Create")
                }
            }

            if (targetItem.folderId != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRemoveFromFolder,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("folder_picker_remove")
                ) {
                    Icon(imageVector = Icons.Default.DriveFileMove, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove from folder")
                }
            }

            if (folders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(folders, key = { _, f -> f.id }) { index, folder ->
                        val isCurrent = targetItem.folderId == folder.id
                        val count = folderMembersMap[folder.id]?.size ?: 0
                        val rowInteraction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .tvFocusBorder(rowInteraction, shape = RoundedCornerShape(14.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable(interactionSource = rowInteraction, indication = null) {
                                    onSelectFolder(folder.id)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .then(if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                .testTag("folder_picker_option_${folder.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(app = folder, size = 38.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$count app${if (count == 1) "" else "s"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Current folder",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Multi-select sheet allowing users to batch-add or remove apps inside an existing folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderAppsManageSheet(
    folder: GameItem,
    candidateApps: List<GameItem>,
    currentMemberIds: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isTv = isTelevisionMode()
    var selectedIds by remember(folder.id, currentMemberIds) {
        mutableStateOf(currentMemberIds)
    }
    var filterQuery by remember(folder.id) { mutableStateOf("") }
    val filteredApps = remember(candidateApps, filterQuery) {
        val trimmed = filterQuery.trim()
        if (trimmed.isEmpty()) candidateApps
        else candidateApps.filter { it.title.contains(trimmed, ignoreCase = true) }
    }
    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(folder.id) {
        delay(80)
        runCatching { initialFocusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = if (isTv) 760.dp else BottomSheetDefaults.SheetMaxWidth,
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
            val closeInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Manage ${folder.title}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${selectedIds.size} selected",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    interactionSource = closeInteraction,
                    modifier = Modifier
                        .tvFocusBorder(closeInteraction, shape = CircleShape)
                        .testTag("folder_manage_close")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Filter apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (filterQuery.isNotEmpty()) {
                        IconButton(onClick = { filterQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("folder_manage_search")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(filteredApps, key = { _, app -> app.id }) { index, app ->
                    val checked = app.id in selectedIds
                    val itemInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .tvFocusBorder(itemInteraction, shape = RoundedCornerShape(12.dp))
                            .clickable(interactionSource = itemInteraction, indication = null) {
                                selectedIds = if (checked) {
                                    selectedIds - app.id
                                } else {
                                    selectedIds + app.id
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .then(if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            .testTag("folder_manage_item_${app.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app = app, size = 36.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = app.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                selectedIds = if (isChecked) {
                                    selectedIds + app.id
                                } else {
                                    selectedIds - app.id
                                }
                            },
                            modifier = Modifier.focusProperties { canFocus = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { onSave(selectedIds) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(if (filteredApps.isEmpty()) Modifier.focusRequester(initialFocusRequester) else Modifier)
                    .testTag("folder_manage_save")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
