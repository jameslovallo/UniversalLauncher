package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.service.TemperatureUnit
import com.example.data.service.WeatherInfo
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
    temperatureUnit: TemperatureUnit = TemperatureUnit.defaultForLocale(),
    showFocusEffects: Boolean = false,
    onOpenCalendar: () -> Unit = {},
    onOpenWeather: () -> Unit = {},
    onTabSelected: (MainLibraryTab) -> Unit,
    onOpenAddModal: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateDown: (() -> Unit)? = null,
    firstItemFocusRequester: FocusRequester? = null,
    onNavFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            // True while any control in the bar holds focus. The dashboard uses this to
            // stop the library views from drawing their "last focused" highlight once
            // focus moves up to the navigation.
            .onFocusChanged { onNavFocusChanged(it.hasFocus) }
            .focusGroup()
            .onPreviewKeyEvent { keyEvent ->
                if (onNavigateDown != null &&
                    keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionDown
                ) {
                    onNavigateDown()
                    true
                } else {
                    false
                }
            }
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
                showFocusEffects = showFocusEffects,
                onOpenCalendar = onOpenCalendar,
                onOpenWeather = onOpenWeather,
                modifier = Modifier.weight(1f)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AddButton(
                    onClick = onOpenAddModal,
                    showFocusEffects = showFocusEffects,
                    focusRequester = firstItemFocusRequester
                )
                NavTabs(currentTab, showFocusEffects, onTabSelected)
                SettingsButton(onOpenSettings, showFocusEffects)
            }
        } else if (alignEnd) {
            // Compact landscape without the date/weather header: keep all controls
            // grouped on the trailing edge.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AddButton(
                    onClick = onOpenAddModal,
                    showFocusEffects = showFocusEffects,
                    focusRequester = firstItemFocusRequester
                )
                NavTabs(currentTab, showFocusEffects, onTabSelected)
                SettingsButton(onOpenSettings, showFocusEffects)
            }
        } else {
            AddButton(
                onClick = onOpenAddModal,
                showFocusEffects = showFocusEffects,
                focusRequester = firstItemFocusRequester
            )
            NavTabs(currentTab, showFocusEffects, onTabSelected)
            SettingsButton(onOpenSettings, showFocusEffects)
        }
    }
}

@Composable
private fun AddButton(
    onClick: () -> Unit,
    showFocusEffects: Boolean,
    focusRequester: FocusRequester? = null
) {
    // Material3's IconButton does not reliably emit focus interactions for its supplied
    // interaction source, so the focus ring is driven by the focus state directly.
    var isFocused by remember { mutableStateOf(false) }
    val focused = isFocused && showFocusEffects
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (focused) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .testTag("topbar_add_button")
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add app, shortcut, or GeForce NOW game",
            tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun NavTabs(
    currentTab: MainLibraryTab,
    showFocusEffects: Boolean,
    onTabSelected: (MainLibraryTab) -> Unit
) {
    // Center: plain text tabs, Apps first, active label bold. No background or counts.
    Row(verticalAlignment = Alignment.CenterVertically) {
        TabLabel(
            label = "Apps",
            isSelected = currentTab == MainLibraryTab.APPS,
            showFocusEffects = showFocusEffects,
            onClick = { onTabSelected(MainLibraryTab.APPS) },
            testTag = "tab_apps"
        )

        Spacer(modifier = Modifier.width(24.dp))

        TabLabel(
            label = "Games",
            isSelected = currentTab == MainLibraryTab.GAMES,
            showFocusEffects = showFocusEffects,
            onClick = { onTabSelected(MainLibraryTab.GAMES) },
            testTag = "tab_games"
        )
    }
}

@Composable
private fun SettingsButton(
    onClick: () -> Unit,
    showFocusEffects: Boolean
) {
    var isFocused by remember { mutableStateOf(false) }
    val focused = isFocused && showFocusEffects
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (focused) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .testTag("topbar_settings_button")
    ) {
        Icon(
            imageVector = PageInfoIcon,
            contentDescription = "Open Settings",
            tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
    showFocusEffects: Boolean,
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
    val dateInteraction = remember { MutableInteractionSource() }
    val dateFocused by dateInteraction.collectIsFocusedAsState()
    val weatherInteraction = remember { MutableInteractionSource() }
    val weatherFocused by weatherInteraction.collectIsFocusedAsState()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val highlightDate = dateFocused && showFocusEffects
        Text(
            text = dateFormatter.format(Date(now)),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = if (highlightDate) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (highlightDate) {
                        Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    interactionSource = dateInteraction,
                    indication = null,
                    onClick = onOpenCalendar
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("topbar_date")
        )

        if (weatherEnabled && weather != null) {
            Spacer(modifier = Modifier.width(8.dp))
            val highlightWeather = weatherFocused && showFocusEffects
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (highlightWeather) {
                            Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = weatherInteraction,
                        indication = null,
                        onClick = onOpenWeather
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("topbar_weather"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = weatherIcon(weather.weatherCode),
                    contentDescription = weatherDescription(weather.weatherCode),
                    tint = if (highlightWeather) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = weather.temperatureDisplay(temperatureUnit, showUnit = false),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (highlightWeather) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
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
    showFocusEffects: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Focus visuals come from the focus state directly: the clickable interaction source is
    // not a reliable focus signal when focus is moved programmatically onto the bar.
    var isFocused by remember { mutableStateOf(false) }
    val focused = isFocused && showFocusEffects
    val textColor by animateColorAsState(
        targetValue = when {
            focused -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "tab_text"
    )
    val pillShape = RoundedCornerShape(12.dp)

    Text(
        text = label,
        fontSize = 18.sp,
        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
        color = textColor,
        modifier = Modifier
            .clip(pillShape)
            .background(
                if (focused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                else Color.Transparent
            )
            .then(
                if (focused) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, pillShape)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag)
    )
}
