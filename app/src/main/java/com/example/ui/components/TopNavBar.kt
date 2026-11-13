package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.service.TemperatureUnit
import com.example.data.service.WeatherInfo
import com.example.data.service.resolveTemperatureUnit
import com.example.data.service.temperatureDisplay
import com.example.ui.viewmodel.MainLibraryTab
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TopNavBar(
    currentTab: MainLibraryTab,
    alignEnd: Boolean = false,
    showDateWeather: Boolean = false,
    weatherEnabled: Boolean = false,
    weather: WeatherInfo? = null,
    temperatureUnit: TemperatureUnit = TemperatureUnit.AUTO,
    onOpenCalendar: () -> Unit = {},
    onOpenWeather: () -> Unit = {},
    onTabSelected: (MainLibraryTab) -> Unit,
    onOpenAddModal: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDateWeather) {
            // Compact landscape hides the favorites clock, so the date and weather move
            // up here: left-aligned while the navigation controls sit on the right.
            DateWeatherHeader(
                weatherEnabled = weatherEnabled,
                weather = weather,
                temperatureUnit = temperatureUnit,
                onOpenCalendar = onOpenCalendar,
                onOpenWeather = onOpenWeather,
                modifier = Modifier.weight(1f)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AddButton(onOpenAddModal)
                NavTabs(currentTab, onTabSelected)
                SettingsButton(onOpenSettings)
            }
        } else if (alignEnd) {
            // Compact landscape without the date/weather header: keep all controls
            // grouped on the trailing edge.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AddButton(onOpenAddModal)
                NavTabs(currentTab, onTabSelected)
                SettingsButton(onOpenSettings)
            }
        } else {
            AddButton(onOpenAddModal)
            NavTabs(currentTab, onTabSelected)
            SettingsButton(onOpenSettings)
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .testTag("topbar_add_button")
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add app, shortcut, or GeForce NOW game",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun NavTabs(
    currentTab: MainLibraryTab,
    onTabSelected: (MainLibraryTab) -> Unit
) {
    // Center: plain text tabs, Apps first, active label bold. No background or counts.
    Row(verticalAlignment = Alignment.CenterVertically) {
        TabLabel(
            label = "Apps",
            isSelected = currentTab == MainLibraryTab.APPS,
            onClick = { onTabSelected(MainLibraryTab.APPS) },
            testTag = "tab_apps"
        )

        Spacer(modifier = Modifier.width(28.dp))

        TabLabel(
            label = "Games",
            isSelected = currentTab == MainLibraryTab.GAMES,
            onClick = { onTabSelected(MainLibraryTab.GAMES) },
            testTag = "tab_games"
        )
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .testTag("topbar_settings_button")
    ) {
        Icon(
            imageVector = PageInfoIcon,
            contentDescription = "Open Settings",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Date and current conditions shown in the top bar on compact landscape favorites, where
 * the large clock/date block is hidden. Tapping the date opens Calendar and tapping the
 * conditions opens the chosen weather app, matching the favorites home behaviour.
 */
@Composable
private fun DateWeatherHeader(
    weatherEnabled: Boolean,
    weather: WeatherInfo?,
    temperatureUnit: TemperatureUnit,
    onOpenCalendar: () -> Unit,
    onOpenWeather: () -> Unit,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    val displayUnit = remember(temperatureUnit) { resolveTemperatureUnit(temperatureUnit) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateFormatter.format(Date(now)),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenCalendar() }
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .testTag("topbar_date")
        )

        if (weatherEnabled && weather != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenWeather() }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("topbar_weather"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = weatherIcon(weather.weatherCode),
                    contentDescription = weatherDescription(weather.weatherCode),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = weather.temperatureDisplay(displayUnit),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TabLabel(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tab_text"
    )

    Text(
        text = label,
        fontSize = 18.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .focusProperties { canFocus = false }
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag(testTag)
    )
}
