package com.example

import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import com.example.data.repository.GameRepository
import com.example.data.service.PinnedShortcutImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles shortcut pin requests from other apps.
 *
 * Declaring the `android.content.pm.action.CONFIRM_PIN_SHORTCUT` intent filter is what
 * makes `ShortcutManager.isRequestPinShortcutSupported()` return true while this app is
 * the default home app. Without it, requesters (such as Moonlight adding a game to the
 * home screen) fail with "Your current launcher does not allow for creating pinned
 * shortcuts".
 *
 * The system starts this activity with a [LauncherApps.PinItemRequest]; accepting it pins
 * the shortcut, after which it is added to the launcher's library.
 */
class PinShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            finish()
            return
        }
        acceptPinRequest()
        // This activity has no UI; dismiss it straight away so the requesting app
        // (Moonlight) regains focus while the library insert finishes in the background.
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun acceptPinRequest() {
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = launcherApps?.getPinItemRequest(intent)
        if (request == null ||
            request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
        ) {
            return
        }

        val shortcut = request.shortcutInfo
        // accept() must be called for the shortcut to actually be pinned.
        if (shortcut == null || !request.accept()) {
            return
        }

        val repository = GameRepository.create(applicationContext)
        val appContext = applicationContext

        // Library work uses a process-wide scope: finishing the activity must not cancel
        // the insert or the SteamGridDB artwork lookup.
        workScope.launch {
            val fresh = PinnedShortcutImporter.fromPinnedShortcut(appContext, shortcut)
            // Re-pinning an existing shortcut should refresh its label/icon without
            // losing the user's favourite, hidden, library-tab or artwork choices.
            val existing = repository.getGameById(fresh.id)
            val item = if (existing == null) {
                fresh
            } else {
                fresh.copy(
                    title = if (existing.isCustomized) existing.title else fresh.title,
                    iconUrl = if (existing.isCustomized) existing.iconUrl else fresh.iconUrl,
                    isGame = existing.isGame,
                    isFavorite = existing.isFavorite,
                    favoriteOrder = existing.favoriteOrder,
                    isHidden = existing.isHidden,
                    isCustomized = existing.isCustomized,
                    isPinned = true,
                    lastPlayedTimestamp = existing.lastPlayedTimestamp,
                    playCount = existing.playCount,
                    posterUrl = existing.posterUrl,
                    heroBannerUrl = existing.heroBannerUrl,
                    customArtworkSource = existing.customArtworkSource
                )
            }
            repository.insertUserGame(item)
            // Games get real box art where SteamGridDB can match the title; the shortcut
            // icon is still used as the fallback.
            if (item.isGame) {
                repository.ensureAutoPoster(item)
            }
        }
    }

    companion object {
        /** Lives for the process, so a finished activity cannot cancel the pin import. */
        private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
