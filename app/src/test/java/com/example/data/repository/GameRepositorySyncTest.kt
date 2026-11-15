package com.example.data.repository

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.GameDao
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.InstalledAppsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameRepositorySyncTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var dao: GameDao
    private lateinit var repository: GameRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.gameDao()

        val shadowPm = shadowOf(app.packageManager)
        val pkg = "com.example.demoapp"
        val packageInfo = PackageInfo().apply {
            packageName = pkg
            lastUpdateTime = 1000L
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
            }
        }
        shadowPm.installPackage(packageInfo)
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.MainActivity"
                applicationInfo = packageInfo.applicationInfo
            }
            nonLocalizedLabel = "Demo App"
        }
        @Suppress("DEPRECATION")
        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            resolveInfo
        )

        repository = GameRepository(dao, InstalledAppsManager(app))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `syncInstalledApps emits no Room invalidation when installed apps are unchanged`(): Unit = runBlocking {
        repository.syncInstalledApps()
        val initial = repository.allGames.first()
        assertEquals(1, initial.size)

        var extraEmission: List<GameItem>? = null
        val collectorJob = launch(Dispatchers.IO) {
            extraEmission = withTimeoutOrNull(300L) {
                repository.allGames.drop(1).first()
            }
        }

        repository.syncInstalledApps()
        collectorJob.join()

        assertNull("Unchanged installed apps should not trigger Room emissions", extraEmission)
    }

    @Test
    fun `syncInstalledApps batches inserts and stale tombstone deletions`(): Unit = runBlocking {
        val staleTombstone = GameItem(
            id = "app_com.removed.app",
            title = "Removed",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.removed.app",
            isDeleted = true
        )
        dao.insertGame(staleTombstone)

        repository.syncInstalledApps()

        val all = dao.getAllItemsNow()
        assertEquals(1, all.size)
        assertEquals("app_com.example.demoapp", all.first().id)
    }
}
