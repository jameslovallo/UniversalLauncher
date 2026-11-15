package com.example.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.GameDao
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.InstalledAppsManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderRepositoryTest {

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
        repository = GameRepository(dao, InstalledAppsManager(app))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleApp(id: String, title: String, isFavorite: Boolean = false) = GameItem(
        id = id,
        title = title,
        type = GameType.INSTALLED_APP,
        isGame = false,
        packageName = "com.example.$id",
        isFavorite = isFavorite
    )

    @Test
    fun `createFolder moveItemToFolder setFolderMembers and deleteFolder preserve member apps`(): Unit = runBlocking {
        val alpha = sampleApp("alpha", "Alpha", isFavorite = true)
        val zeta = sampleApp("zeta", "Zeta")
        val music = sampleApp("music", "Music")
        dao.insertGames(listOf(alpha, zeta, music))

        val folder = repository.createFolder("Utilities", initialMemberId = alpha.id)
        assertEquals(GameType.FOLDER, folder.type)
        assertFalse(folder.isGame)

        val alphaInFolder = dao.getGameById(alpha.id)!!
        assertEquals(folder.id, alphaInFolder.folderId)
        assertFalse("Moving an item into a folder clears home favorite state", alphaInFolder.isFavorite)

        // Batch update members: replace alpha with zeta and music
        repository.setFolderMembers(folder.id, setOf(zeta.id, music.id))
        assertNull(dao.getGameById(alpha.id)!!.folderId)
        assertEquals(folder.id, dao.getGameById(zeta.id)!!.folderId)
        assertEquals(folder.id, dao.getGameById(music.id)!!.folderId)

        // Deleting folder unassigns members without deleting the apps themselves
        repository.deleteFolder(folder.id)
        assertNull(dao.getGameById(folder.id))
        assertNotNull(dao.getGameById(zeta.id))
        assertNull(dao.getGameById(zeta.id)!!.folderId)
        assertNotNull(dao.getGameById(music.id))
        assertNull(dao.getGameById(music.id)!!.folderId)
    }

    @Test
    fun `moving an app inside a folder to Games removes folderId`(): Unit = runBlocking {
        val gameApp = sampleApp("retro", "RetroArch")
        dao.insertGame(gameApp)
        val folder = repository.createFolder("Emulators", initialMemberId = gameApp.id)
        assertEquals(folder.id, dao.getGameById(gameApp.id)!!.folderId)

        repository.setIsGame(gameApp.id, isGame = true)
        val updated = dao.getGameById(gameApp.id)!!
        assertTrue(updated.isGame)
        assertNull(updated.folderId)
    }

    @Test
    fun `folders sort after Z in app list unless moved to favorites`() {
        val aFolder = GameItem(id = "folder_1", title = "Arcade", type = GameType.FOLDER, isGame = false)
        val favFolder = GameItem(
            id = "folder_2",
            title = "Pinned Folder",
            type = GameType.FOLDER,
            isGame = false,
            isFavorite = true
        )
        val zetaApp = sampleApp("zeta", "Zeta")
        val alphaApp = sampleApp("alpha", "Alpha")
        val insideFolderApp = sampleApp("inside", "Beta").copy(folderId = aFolder.id)

        val allItems = listOf(aFolder, favFolder, zetaApp, alphaApp, insideFolderApp)

        val libraryApps = allItems
            .filter { !it.isGame && !it.isFavorite && it.folderId == null }
            .sortedWith(compareBy<GameItem> { it.type == GameType.FOLDER }.thenBy { it.title.lowercase() })

        assertEquals(listOf("Alpha", "Zeta", "Arcade"), libraryApps.map { it.title })
    }
}
