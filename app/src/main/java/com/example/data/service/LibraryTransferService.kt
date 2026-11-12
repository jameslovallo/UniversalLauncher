package com.example.data.service

import android.content.Context
import android.net.Uri
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * @param itemCount number of items in the exported file, or the library total after an
 *   import.
 * @param imported number of new items added by an import.
 */
data class LibraryTransferSummary(
    val itemCount: Int,
    val imported: Int = 0
)

/**
 * Exports and imports the shareable game library through the Storage Access Framework.
 *
 * The file is plain JSON keyed by the stable [GameItem] id, so imports merge cleanly:
 * items that already exist on this device are left untouched, which preserves local
 * favorites, custom titles and artwork. Only active cloud games, Moonlight games and
 * game shortcuts travel; installed apps are device-specific and stay local, and local
 * artwork paths are dropped so the receiving device can fetch its own.
 */
class LibraryTransferService(
    private val context: Context,
    private val repository: GameRepository
) {

    /** Writes the shareable library to [uri], typically a user-picked document. */
    suspend fun exportLibrary(uri: Uri): LibraryTransferSummary = withContext(Dispatchers.IO) {
        val items = shareableItems(repository.getAllItemsSnapshot())
        if (items.isEmpty()) {
            throw IllegalStateException("There are no games to export yet")
        }

        val payload = JSONArray()
        items.forEach { payload.put(itemToJson(it)) }
        val document = JSONObject()
            .put("format", FORMAT_NAME)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("items", payload)

        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Could not open the selected file for writing")
        stream.use { it.write(document.toString().toByteArray(Charsets.UTF_8)) }

        LibraryTransferSummary(itemCount = items.size)
    }

    /** Merges the library file at [uri] into this device, skipping items that already exist. */
    suspend fun importLibrary(uri: Uri): LibraryTransferSummary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("Could not open the selected file")
        if (text.isBlank()) throw IllegalStateException("That file is empty")

        val items = parseItems(text)
        if (items.isEmpty()) {
            throw IllegalStateException("No compatible games were found in that file")
        }

        val knownIds = repository.getAllItemsSnapshot().mapTo(HashSet()) { it.id }
        var imported = 0
        for (item in items) {
            if (item.isDeleted) continue
            if (item.id in knownIds) continue
            if (repository.getGameById(item.id) != null) continue
            knownIds.add(item.id)
            repository.insertUserGame(item)
            imported++
        }

        LibraryTransferSummary(
            itemCount = shareableItems(repository.getAllItemsSnapshot()).size,
            imported = imported
        )
    }

    private fun parseItems(text: String): List<GameItem> {
        val trimmed = text.trim()
        val array = if (trimmed.startsWith("[")) {
            // Also accept the bare array format written by older builds.
            JSONArray(trimmed)
        } else {
            val document = JSONObject(trimmed)
            val format = document.optString("format")
            if (format.isNotBlank() && format != FORMAT_NAME) {
                throw IllegalStateException("That file is not a Nexus library export")
            }
            document.optJSONArray("items") ?: JSONArray()
        }

        return buildList {
            for (index in 0 until array.length()) {
                jsonToItem(array.optJSONObject(index))?.let(::add)
            }
        }
    }

    private fun shareableItems(items: List<GameItem>): List<GameItem> =
        items.filter { item ->
            !item.isDeleted && when (item.type) {
                GameType.GEFORCE_NOW, GameType.MOONLIGHT -> true
                GameType.SHORTCUT -> item.isGame
                else -> false
            }
        }
    // ---- JSON ----------------------------------------------------------------------

    private fun itemToJson(item: GameItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("type", item.type.name)
        put("isGame", item.isGame)
        item.packageName?.let { put("packageName", it) }
        item.activityName?.let { put("activityName", it) }
        item.launchUrl?.let { put("launchUrl", it) }
        item.gfnCmsId?.let { put("gfnCmsId", it) }
        // Local artwork paths only exist on the exporting device, so leave them out and
        // let the receiving device match or fetch artwork again.
        item.posterUrl?.takeUnless { it.startsWith("file:") }?.let { put("posterUrl", it) }
        item.heroBannerUrl?.takeUnless { it.startsWith("file:") }?.let { put("heroBannerUrl", it) }
        item.iconUrl?.takeUnless { it.startsWith("file:") }?.let { put("iconUrl", it) }
        put("genre", item.genre)
        put("publisher", item.publisher)
        put("description", item.description)
        put("isFavorite", item.isFavorite)
        put("favoriteOrder", item.favoriteOrder)
        put("isHidden", item.isHidden)
        put("isCustomized", item.isCustomized)
        put("isDeleted", item.isDeleted)
    }

    private fun jsonToItem(json: JSONObject?): GameItem? {
        if (json == null) return null
        return runCatching {
            GameItem(
                id = json.getString("id"),
                title = json.getString("title"),
                type = GameType.valueOf(json.getString("type")),
                isGame = json.optBoolean("isGame", true),
                packageName = json.optNullableString("packageName"),
                activityName = json.optNullableString("activityName"),
                launchUrl = json.optNullableString("launchUrl"),
                gfnCmsId = json.optNullableString("gfnCmsId"),
                posterUrl = json.optNullableString("posterUrl"),
                heroBannerUrl = json.optNullableString("heroBannerUrl"),
                iconUrl = json.optNullableString("iconUrl"),
                genre = json.optString("genre", "Game"),
                publisher = json.optString("publisher", ""),
                description = json.optString("description", ""),
                isFavorite = json.optBoolean("isFavorite", false),
                favoriteOrder = json.optInt("favoriteOrder", 0),
                isHidden = json.optBoolean("isHidden", false),
                isCustomized = json.optBoolean("isCustomized", false),
                isDeleted = json.optBoolean("isDeleted", false)
            )
        }.getOrNull()
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val FORMAT_NAME = "nexus_library"
        private const val FORMAT_VERSION = 1
    }
}
