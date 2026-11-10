package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonGfnGreen
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardHighlight
import com.example.ui.theme.TextSecondary

@Composable
fun LauncherSettingsDialog(
    steamGridDbApiKey: String?,
    onSaveApiKey: (String) -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onResyncGfn: () -> Unit,
    onResyncApps: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(steamGridDbApiKey ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateBackground)
                .border(1.dp, SurfaceCardHighlight, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Launcher & Cloud Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("settings_close_button")) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Launcher Mode Section
            SettingsSectionTitle(title = "Android Home / Launcher Mode")
            Text(
                text = "Nexus can run as a standard standalone app or as your primary Android device home launcher.",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSetDefaultLauncher,
                colors = ButtonDefaults.buttonColors(containerColor = NeonGfnGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("set_default_home_button")
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Set as Default Home Launcher", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SteamGridDB API Key
            SettingsSectionTitle(title = "SteamGridDB Artwork API Key")
            Text(
                text = "Used to fetch community posters, hero banners, and grids directly inside the customizer.",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("SteamGridDB API Key") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = SurfaceCardHighlight
                ),
                modifier = Modifier.fillMaxWidth().testTag("steamgriddb_key_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onSaveApiKey(apiKeyInput.trim()) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("save_key_button")
            ) {
                Icon(Icons.Default.Key, contentDescription = null, tint = ElectricCyan)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save SteamGridDB Key", color = Color.White)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Library Syncing
            SettingsSectionTitle(title = "Library Synchronization")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onResyncGfn,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("sync_gfn_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonGfnGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync GFN", fontSize = 11.sp, color = Color.White)
                }

                OutlinedButton(
                    onClick = onResyncApps,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("sync_apps_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync Apps", fontSize = 11.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Gamepad Support Note
            SettingsSectionTitle(title = "Controller & Gamepad Support")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCard)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = NeonGfnGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Full Xbox, PlayStation DualSense, Nintendo Switch Pro, and standard Bluetooth/USB controller support is enabled throughout the launcher and in the GeForce NOW web player.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = NeonGfnGreen
    )
}
