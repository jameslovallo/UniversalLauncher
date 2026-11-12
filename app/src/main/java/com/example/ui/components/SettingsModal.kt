package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
    driveEmail: String?,
    driveSyncing: Boolean,
    driveStatus: String?,
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
    onDriveSignInIntent: () -> android.content.Intent,
    onDriveSignInResult: (android.content.Intent?) -> Unit,
    onDriveSignOut: () -> Unit,
    onDriveSync: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(steamGridDbApiKey ?: "") }
    var zipInput by remember(weatherZip) { mutableStateOf(weatherZip) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // These settings depend on external system state, so re-read them whenever the sheet
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

    val driveSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onDriveSignInResult(result.data)
        }
    }

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
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("settings_close_button")) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Favorites Home Layout
            SectionHeader(
                title = "Favorites Layout",
                description = "Choose how the clock, date, and favorite apps appear on the home view"
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

            // Theme
            SectionHeader(
                title = "Theme",
                description = "Use colors from your wallpaper, or keep the neutral built-in palette"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeStyle.values().forEach { styleOption ->
                    ThemeStyleCard(
                        style = styleOption,
                        isSelected = themeStyle == styleOption,
                        onSelect = { onSelectThemeStyle(styleOption) },
                        modifier = Modifier.weight(1f)
                    )
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
                    title = "Apps Background Opacity",
                    description = "How strongly the wallpaper is dimmed behind the apps screen"
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
                    "Accessibility launcher override is enabled."
                } else {
                    "Some devices refuse to change the default launcher. Enable the " +
                        "accessibility override to return to Nexus whenever another home app opens."
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

            // Google Drive sync
            SectionHeader(
                title = "Google Drive Sync",
                description = "Back up and restore GeForce NOW and Moonlight games using this " +
                    "app's private Drive data"
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (driveEmail == null) {
                Button(
                    onClick = { driveSignInLauncher.launch(onDriveSignInIntent()) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("drive_sign_in_button")
                ) {
                    Icon(imageVector = Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in with Google", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "Signed in as $driveEmail",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("drive_account_label")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDriveSync,
                    enabled = !driveSyncing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("drive_sync_button")
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (driveSyncing) "Syncing…" else "Sync Now",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDriveSignOut,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("drive_sign_out_button")
                ) {
                    Icon(imageVector = Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }

            if (!driveStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = driveStatus,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("drive_status")
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

@Composable
private fun SectionHeader(
    title: String,
    description: String
) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    val title = when (style) {
        ThemeStyle.WALLPAPER -> "Wallpaper"
        ThemeStyle.NEUTRAL -> "Neutral"
    }
    val description = when (style) {
        ThemeStyle.WALLPAPER -> "Match your wallpaper"
        ThemeStyle.NEUTRAL -> "Built-in palette"
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp)
        ) {
            Text(
                text = layout.label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = layout.description,
                fontSize = 11.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
