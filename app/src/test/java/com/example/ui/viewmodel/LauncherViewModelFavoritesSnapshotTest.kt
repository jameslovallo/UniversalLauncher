package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.GameDao
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.FavoritesSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression coverage for the "favorites disappear after returning from an app" bug on
 * low-memory devices. The launcher process is killed in the background there, so a cold start
 * must be able to draw the favorites from the snapshot before Room opens the database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherViewModelFavoritesSnapshotTest {

    private lateinit var app: Application
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var dao: GameDao

    /** Cancelled in [tearDown]; see LauncherViewModelIconThemeMemoryTest for why. */
    private var createdViewModel: LauncherViewModel? = null
    private var collectorScope: CoroutineScope? = null

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        prefs = app.getSharedPreferences("nexus_launcher_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val db = AppDatabase.getDatabase(app)
        dao = db.gameDao()
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
    }

    @After
    fun tearDown() {
        collectorScope?.cancel()
        collectorScope = null
        createdViewModel?.viewModelScope?.cancel()
        createdViewModel = null
    }

    private fun awaitCondition(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(25L)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Subscribes so [LauncherViewModel.favoriteApps] starts collecting Room. */
    private fun subscribeToFavorites(viewModel: LauncherViewModel) {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        collectorScope = scope
        scope.launch { viewModel.favoriteApps.collect {} }
    }

    private fun favorite(id: String, title: String, order: Int) = GameItem(
        id = id,
        title = title,
        type = GameType.INSTALLED_APP,
        isGame = false,
        packageName = id.removePrefix("app_"),
        iconUrl = "file:///data/user/0/com.example/files/app_icons/$id.png",
        isFavorite = true,
        favoriteOrder = order
    )

    @Test
    fun `cached favorites are available on the first frame, before Room emits`() {
        val cached = listOf(favorite("app_alpha", "Alpha", 0), favorite("app_beta", "Beta", 1))
        FavoritesSnapshotStore.persist(prefs, cached)

        // The database is empty (cleared in setUp). A cold start must still draw the
        // favorites the user saw before the process was killed.
        val viewModel = LauncherViewModel(app).also { createdViewModel = it }

        assertEquals(cached, viewModel.favoriteApps.value)
    }

    @Test
    fun `Room emissions replace the cached snapshot`() = runBlocking {
        val first = favorite("app_alpha", "Alpha", 0)
        val second = favorite("app_beta", "Beta", 1)
        withContext(Dispatchers.IO) { dao.insertGames(listOf(first, second)) }

        val viewModel = LauncherViewModel(app).also { createdViewModel = it }
        subscribeToFavorites(viewModel)

        awaitCondition { FavoritesSnapshotStore.restore(prefs).size == 2 }
        assertEquals(listOf(first, second), FavoritesSnapshotStore.restore(prefs))

        // Un-favoriting must not leave the item behind in the cache.
        withContext(Dispatchers.IO) { dao.updateGame(first.copy(isFavorite = false)) }

        awaitCondition { FavoritesSnapshotStore.restore(prefs) == listOf(second) }
        assertEquals(listOf(second), FavoritesSnapshotStore.restore(prefs))
        assertTrue(viewModel.favoriteApps.value.map { it.id } == listOf(second.id))
    }

    @Test
    fun `package removal event removes deleted app from favorites and snapshot immediately`() = runBlocking {
        val first = favorite("app_com.removed.pkg", "Removed", 0)
        val second = favorite("app_com.kept.pkg", "Kept", 1)
        withContext(Dispatchers.IO) { dao.insertGames(listOf(first, second)) }

        val viewModel = LauncherViewModel(app).also { createdViewModel = it }
        subscribeToFavorites(viewModel)

        awaitCondition { viewModel.favoriteApps.value.size == 2 }

        viewModel.handlePackageRemoved("com.removed.pkg")

        awaitCondition { viewModel.favoriteApps.value.map { it.id } == listOf(second.id) }
        assertEquals(listOf(second), FavoritesSnapshotStore.restore(prefs))
        assertEquals(null, withContext(Dispatchers.IO) { dao.getGameById(first.id) })
    }
}
