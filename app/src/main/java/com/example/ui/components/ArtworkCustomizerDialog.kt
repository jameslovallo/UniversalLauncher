package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.GameItem
import com.example.data.service.ArtworkOption
import com.example.data.service.SteamGridDbService
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonGfnGreen
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardHighlight
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import kotlinx.coroutines.launch

@Composable
fun ArtworkCustomizerDialog(
    game: GameItem,
    steamGridDbApiKey: String?,
    onSaveArtwork: (posterUrl: String, heroUrl: String?, source: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentPosterUrl by remember { mutableStateOf(game.posterUrl ?: "") }
    var currentHeroUrl by remember { mutableStateOf(game.heroBannerUrl ?: "") }
    var searchQuery by remember { mutableStateOf(game.title) }
    var customUrlInput by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ArtworkOption>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Photo picker launcher for picking images from local folders/device gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            currentPosterUrl = uri.toString()
        }
    }

    LaunchedEffect(searchQuery) {
        isSearching = true
        searchResults = SteamGridDbService.searchArtwork(searchQuery, steamGridDbApiKey)
        isSearching = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xDD000000))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.90f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SlateBackground)
                    .border(1.dp, SurfaceCardHighlight, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Customize Artwork",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = game.title,
                            fontSize = 12.sp,
                            color = NeonGfnGreen
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("dialog_close_button")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Source Tabs: SteamGridDB, Local Folders, Custom URL, Presets
                val tabs = listOf("SteamGridDB API", "Local Photo / Storage", "Custom Image URL", "Presets")
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceCard,
                    contentColor = NeonGfnGreen,
                    edgePadding = 0.dp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) NeonGfnGreen else TextSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Content & Live Preview
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Source Picker
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .padding(14.dp)
                    ) {
                        when (selectedTab) {
                            0 -> { // SteamGridDB
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            label = { Text("Game Title on SteamGridDB") },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = ElectricCyan,
                                                unfocusedBorderColor = SurfaceCardHighlight
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = {
                                            scope.launch {
                                                isSearching = true
                                                searchResults = SteamGridDbService.searchArtwork(searchQuery, steamGridDbApiKey)
                                                isSearching = false
                                            }
                                        }) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricCyan)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (steamGridDbApiKey.isNullOrBlank()) {
                                        Text(
                                            text = "Tip: Add your free SteamGridDB API Key in Launcher Settings for live community grids.",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }

                                    if (isSearching) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = ElectricCyan)
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(2),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(searchResults) { option ->
                                                val isSelected = currentPosterUrl == option.fullUrl
                                                Column(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(
                                                            width = if (isSelected) 2.dp else 1.dp,
                                                            color = if (isSelected) NeonGfnGreen else Color.Transparent,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                            currentPosterUrl = option.fullUrl
                                                        }
                                                ) {
                                                    AsyncImage(
                                                        model = option.thumbnailUrl,
                                                        contentDescription = option.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .aspectRatio(2f / 3f)
                                                            .clip(RoundedCornerShape(8.dp))
                                                    )
                                                    Text(
                                                        text = option.title,
                                                        fontSize = 10.sp,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> { // Local Photo / Storage
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = NeonGfnGreen,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Pick Local Poster or Icon",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Select any picture from device storage, downloads, or gallery.",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonGfnGreen),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Browse Local Photos", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            2 -> { // Custom URL
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "Enter Image URL (PNG, JPG, WebP)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = customUrlInput,
                                        onValueChange = { customUrlInput = it },
                                        label = { Text("https://example.com/poster.jpg") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonGfnGreen,
                                            unfocusedBorderColor = SurfaceCardHighlight
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (customUrlInput.isNotBlank()) {
                                                currentPosterUrl = customUrlInput.trim()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Apply Image URL", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            3 -> { // Presets
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(SteamGridDbService.PRESET_ARTWORKS) { preset ->
                                        Column(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    currentPosterUrl = preset.fullUrl
                                                }
                                        ) {
                                            AsyncImage(
                                                model = preset.thumbnailUrl,
                                                contentDescription = preset.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(2f / 3f)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Text(
                                                text = preset.title,
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                modifier = Modifier.padding(2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Right Column: Live Poster & Hero Preview
                    Column(
                        modifier = Modifier
                            .weight(0.7f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Live Poster Preview",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, NeonGfnGreen, RoundedCornerShape(12.dp))
                                .background(VoidBlack)
                        ) {
                            if (currentPosterUrl.isNotBlank()) {
                                AsyncImage(
                                    model = currentPosterUrl,
                                    contentDescription = "Poster preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = TextSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Revert to original GeForce NOW poster
                        OutlinedButton(
                            onClick = {
                                currentPosterUrl = game.posterUrl ?: ""
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset to GFN Default", fontSize = 11.sp, color = TextSecondary)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Save Artwork Button
                        Button(
                            onClick = {
                                val source = when (selectedTab) {
                                    0 -> "STEAMGRIDDB"
                                    1 -> "LOCAL"
                                    2 -> "CUSTOM_URL"
                                    else -> "GFN"
                                }
                                onSaveArtwork(currentPosterUrl, currentHeroUrl.ifBlank { null }, source)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGfnGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("save_artwork_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Artwork", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
