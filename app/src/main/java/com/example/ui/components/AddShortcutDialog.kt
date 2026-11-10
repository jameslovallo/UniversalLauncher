package com.example.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.model.GameType
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonGfnGreen
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardHighlight
import com.example.ui.theme.TextSecondary

@Composable
fun AddShortcutDialog(
    onAdd: (title: String, url: String, posterUrl: String?, type: GameType) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var posterUrl by remember { mutableStateOf("") }
    var isCloudGame by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateBackground)
                .border(1.dp, SurfaceCardHighlight, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Shortcut or Game",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Presets
            Text(text = "Quick Presets:", fontSize = 11.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip(label = "Xbox Cloud") {
                    title = "Xbox Cloud Gaming"
                    url = "https://www.xbox.com/play"
                    posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co5v43.jpg"
                    isCloudGame = true
                }
                PresetChip(label = "GFN Custom Deal") {
                    title = "GeForce NOW Deal"
                    url = "https://play.geforcenow.com"
                    posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co41ea.jpg"
                    isCloudGame = true
                }
                PresetChip(label = "Amazon Luna") {
                    title = "Amazon Luna"
                    url = "https://luna.amazon.com"
                    posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co2m37.jpg"
                    isCloudGame = true
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Game / Shortcut Title") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGfnGreen,
                    unfocusedBorderColor = SurfaceCardHighlight
                ),
                modifier = Modifier.fillMaxWidth().testTag("shortcut_title_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Web URL or Deep Link") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGfnGreen,
                    unfocusedBorderColor = SurfaceCardHighlight
                ),
                modifier = Modifier.fillMaxWidth().testTag("shortcut_url_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = posterUrl,
                onValueChange = { posterUrl = it },
                label = { Text("Poster Image URL (optional)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = SurfaceCardHighlight
                ),
                modifier = Modifier.fillMaxWidth().testTag("shortcut_poster_input")
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    if (title.isNotBlank() && url.isNotBlank()) {
                        val type = if (isCloudGame || url.contains("geforcenow.com") || url.contains("xbox.com/play")) {
                            GameType.CUSTOM_CLOUD
                        } else {
                            GameType.SHORTCUT
                        }
                        onAdd(title.trim(), url.trim(), posterUrl.trim().ifBlank { null }, type)
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank() && url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGfnGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("save_shortcut_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add to Dashboard", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardHighlight, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = label, fontSize = 11.sp, color = ElectricCyan, fontWeight = FontWeight.Medium)
    }
}
