package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.GameItem
import com.example.data.service.WeatherInfo
import com.example.data.service.WeatherLocationMode
import com.example.ui.theme.FavoritesLayout
import com.example.ui.theme.ThemeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModal(
    favoritesLayout: FavoritesLayout,
    steamGridDbApiKey: String?,
    hiddenItems: List<GameItem>,
    appsBackgroundOpacity: Float,
    themeStyle: ThemeStyle,
    weatherEnabled: Boolean,
    weatherMode: WeatherLocationMode,
    weatherZip: String,
    weatherInfo: WeatherInfo?,
    weatherError: String?,
    librarySyncBusy: Boolean,
    librarySyncStatus: String?,
    librarySyncConfigured: Boolean,
    librarySyncTarget: String?,
    librarySyncLastSyncedAt: Long?,
    isDefaultLauncher: () -> Boolean,
    isHomeOverrideEnabled: () -> Boolean,
    onSelectFavoritesLayout: (FavoritesLayout) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onSetAppsBackgroundOpacity: (Float) -> Unit,
    onSelectThemeStyle: (ThemeStyle) -> Unit,
    onSyncLibrary: () -> Unit,
    onUnhideItem: (GameItem) -> Unit,
    onOpenHiddenItem: (GameItem) -> Unit,
    onWeatherEnabledChange: (Boolean) -> Unit,
    onWeatherModeChange: (WeatherLocationMode) -> Unit,
    onWeatherZipChange: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onChooseSyncFolder: (Uri) -> Unit,
    onSyncLibraryNow: () -> Unit,
    onDisconnectLibrarySync: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(steamGridDbApiKey ?: "") }
    var zipInput by remember(weatherZip) { mutableStateOf(weatherZip) }
    val context = LocalContext.current

    // These settings depend on external system state, so re-read them whenever the screen
    // comes back to the foreground (for example after the accessibility settings screen).
    var isDefault by remember { mutableStateOf(isDefaultLauncher()) }
    var overrideEnabled by remember { mutableStateOf(isHomeOverrideEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefault = isDefaultLauncher()
        overrideEnabled = isHomeOverrideEnabled()
    }

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationGranted = granted
        // Re-fetch right away; setWeatherMode triggers a refresh with the new fix.
        if (granted) onWeatherModeChange(WeatherLocationMode.DEVICE_LOCATION)
    }

    val syncFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onChooseSyncFolder(uri)
    }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("settings_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Settings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

            // Favorites Home Layout
            SectionHeader(
                title = "Favorites Layout"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FavoritesLayout.values().forEach { layoutOption ->
                    FavoritesLayoutCard(
                        layout = layoutOption,
                        isSelected = favoritesLayout == layoutOption,
                        onSelect = { onSelectFavoritesLayout(layoutOption) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(18.dp))

            // Color
            SectionHeader(
                title = "Color"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeStyle.values().toList().chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { styleOption ->
                            ThemeStyleCard(
                                style = styleOption,
                                isSelected = themeStyle == styleOption,
                                onSelect = { onSelectThemeStyle(styleOption) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(18.dp))

            // Apps background opacity
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Opacity,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SectionHeader(
                    title = "Apps Background Opacity"
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = appsBackgroundOpacity,
                onValueChange = onSetAppsBackgroundOpacity,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().testTag("apps_opacity_slider")
            )
            Text(
                text = "${(appsBackgroundOpacity * 100).toInt()}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(18.dp))

            // Default launcher / accessibility override
            SectionHeader(
                title = "Android Launcher Mode",
                description = "Set Nexus as your primary Android home launcher"
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (!isDefault) {
                Button(
                    onClick = onSetDefaultLauncher,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("set_home_button")
                ) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set as Default Launcher", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "Nexus is your default launcher.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("default_launcher_status")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Optional accessibility fallback, modelled on LauncherHijack.
            Text(
                text = if (overrideEnabled) {
                    "Accessibility launcher override is enabled. Home presses are routed back " +
                        "to Nexus, and Nexus is hidden from the Task Switcher."
                } else {
                    "Some devices refuse to change the default launcher. Enable the " +
                        "accessibility override to catch Home presses and launcher opens and " +
                        "return to Nexus. Nexus is hidden from the Task Switcher while this " +
                        "service is active."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("accessibility_settings_button")
            ) {
                Icon(imageVector = Icons.Default.Accessibility, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (overrideEnabled) "Manage Accessibility Override"
                    else "Enable Accessibility Override"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(18.dp))

            // Weather
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader(
                        title = "Weather",
                        description = "Show current conditions beside the date on the favorites view"
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = weatherEnabled,
                    onCheckedChange = onWeatherEnabledChange,
                    modifier = Modifier.testTag("weather_enabled_switch")
                )
            }

            if (weatherEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = weatherMode == WeatherLocationMode.DEVICE_LOCATION,
                        onClick = { onWeatherModeChange(WeatherLocationMode.DEVICE_LOCATION) },
                        label = { Text("My location") },
                        modifier = Modifier.testTag("weather_mode_location")
                    )
                    FilterChip(
                        selected = weatherMode == WeatherLocationMode.ZIP_CODE,
                        onClick = { onWeatherModeChange(WeatherLocationMode.ZIP_CODE) },
                        label = { Text("ZIP code") },
                        modifier = Modifier.testTag("weather_mode_zip")
                    )
                }

                if (weatherMode == WeatherLocationMode.DEVICE_LOCATION && !locationGranted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            locationPermissionLauncher.launch(
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("weather_location_permission")
                    ) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Allow Location Access")
                    }
                }

                if (weatherMode == WeatherLocationMode.ZIP_CODE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = zipInput,
                        onValueChange = { zipInput = it },
                        label = { Text("ZIP or postal code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("weather_zip_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onWeatherZipChange(zipInput.trim()) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("weather_zip_save")
                    ) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update Weather")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val status = when {
                    weatherError != null -> weatherError
                    weatherInfo != null -> buildString {
                        append("${weatherInfo.temperatureC.toInt()}° in ${weatherInfo.locationLabel}")
                    }
                    else -> "Fetching current conditions…"
                }
                Text(
                    text = status,
                    fontSize = 12.sp,
                    color = if (weatherError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("weather_status")
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(18.dp))

            // Library folder sync between devices
            SectionHeader(
                title = "Library Sync",
                description = "Keep your GeForce NOW, Moonlight, and shortcut games in sync " +
                    "with a folder on your device or cloud drive. All assigned game posters " +
                    "and library metadata are exported automatically."
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (librarySyncConfigured) {
                Text(
                    text = "Syncing with ${librarySyncTarget ?: "your selected folder"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("library_sync_target")
                )
                val lastSynced = librarySyncLastSyncedAt
                    ?.takeIf { it > 0L }
                    ?.let {
                        java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.SHORT,
                            java.text.DateFormat.SHORT
                        ).format(java.util.Date(it))
                    }
                if (lastSynced != null) {
                    Text(
                        text = "Last synced $lastSynced",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("library_sync_last_synced")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSyncLibraryNow,
                        enabled = !librarySyncBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("library_sync_now_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (librarySyncBusy) "Working…" else "Sync Now")
                    }
                    TextButton(
                        onClick = onDisconnectLibrarySync,
                        enabled = !librarySyncBusy,
                        modifier = Modifier.testTag("library_sync_disconnect_button")
                    ) {
                        Text("Disconnect")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { syncFolderLauncher.launch(null) },
                    enabled = !librarySyncBusy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("library_sync_pick_folder_button")
                ) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Folder")
                }
            } else {
                Button(
                    onClick = { syncFolderLauncher.launch(null) },
                    enabled = !librarySyncBusy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("library_sync_pick_folder_button")
                ) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose Sync Folder", fontWeight = FontWeight.Bold)
                }
            }

            if (!librarySyncStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = librarySyncStatus,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("library_sync_status")
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(18.dp))

            // SteamGridDB API Key
            SectionHeader(
                title = "SteamGridDB API Key",
                description = "Optional. Improves artwork matching for imported games."
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("steamgriddb_key_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onSaveApiKey(apiKeyInput.trim()) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Key, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save API Key")
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onSyncLibrary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("settings_sync_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Resync Library & Apps")
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(18.dp))

            // Hidden Apps & Games restore list
            SectionHeader(
                title = "Hidden Apps",
                description = "Tap an item to open it, or restore it to the library."
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (hiddenItems.isEmpty()) {
                Text(
                    text = "Nothing hidden yet.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("hidden_items_empty")
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("hidden_items_list"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hiddenItems.forEach { item ->
                        HiddenItemRow(
                            item = item,
                            onOpen = { onOpenHiddenItem(item) },
                            onRestore = { onUnhideItem(item) }
                        )
                    }
                }
            }
        }
    }
}
}



@Composable
private fun SectionHeader(
    title: String,
    description: String? = null
) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HiddenItemRow(
    item: GameItem,
    onOpen: () -> Unit,
    onRestore: () -> Unit
) {
    val context = LocalContext.current
    val imageUrl = item.iconUrl ?: item.posterUrl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .testTag("hidden_item_${item.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        TextButton(
            onClick = onRestore,
            modifier = Modifier.testTag("restore_hidden_${item.id}")
        ) {
            Text("Restore", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ThemeStyleCard(
    style: ThemeStyle,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val seed = style.seedColor
    val swatch = if (seed != null) {
        androidx.compose.ui.graphics.Color(seed.toInt())
    } else {
        MaterialTheme.colorScheme.primary
    }
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("theme_style_${style.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(swatch)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FavoritesLayoutCard(
    layout: FavoritesLayout,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("favorites_layout_${layout.name.lowercase()}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = layout.label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
