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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MainLibraryTab

@Composable
fun TopNavBar(
    currentTab: MainLibraryTab,
    alignEnd: Boolean = false,
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
        horizontalArrangement = if (alignEnd) {
            Arrangement.spacedBy(8.dp, Alignment.End)
        } else {
            Arrangement.SpaceBetween
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: + Icon button (no background, same size as settings button)
        IconButton(
            onClick = onOpenAddModal,
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

        // Center: plain text tabs, Apps first, active label bold. No background or counts.
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        // Right: Settings Tune Icon button (balanced minimalist stroke aesthetic)
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(44.dp)
                .testTag("topbar_settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Open Settings",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
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
