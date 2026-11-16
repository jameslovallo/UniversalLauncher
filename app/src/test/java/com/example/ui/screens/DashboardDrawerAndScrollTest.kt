package com.example.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.MainLibraryTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardDrawerAndScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class HarnessState {
        var currentTab by mutableStateOf(MainLibraryTab.APPS)
        var appsHomeReset by mutableIntStateOf(0)
        lateinit var pagerState: PagerState
        lateinit var appsPagerState: PagerState
        lateinit var gridState: LazyGridState
        lateinit var gamesRowState: LazyListState
        lateinit var appsListState: LazyListState
        lateinit var appsGridState: LazyGridState
        lateinit var scope: CoroutineScope
    }

    private fun setupHarness(): HarnessState {
        val state = HarnessState()
        composeRule.setContent {
            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { 2 }
            )
            val appsPagerState = rememberPagerState(pageCount = { 2 })
            val gridState = rememberLazyGridState()
            val gamesRowState = rememberLazyListState()
            val appsListState = rememberLazyListState()
            val appsGridState = rememberLazyGridState()
            val scope = rememberCoroutineScope()

            state.pagerState = pagerState
            state.appsPagerState = appsPagerState
            state.gridState = gridState
            state.gamesRowState = gamesRowState
            state.appsListState = appsListState
            state.appsGridState = appsGridState
            state.scope = scope

            BindLibraryPagerAndScrollEffects(
                currentTab = state.currentTab,
                appsHomeReset = state.appsHomeReset,
                pagerState = pagerState,
                appsPagerState = appsPagerState,
                gridState = gridState,
                gamesRowState = gamesRowState,
                appsListState = appsListState,
                appsGridState = appsGridState,
                onTabFromPager = { tab ->
                    if (state.currentTab != tab) state.currentTab = tab
                }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == 0) {
                    VerticalPager(
                        state = appsPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { appsPage ->
                        if (appsPage == 0) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text("Favorites")
                            }
                        } else {
                            LazyColumn(
                                state = appsListState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(60) { index ->
                                    Text(
                                        text = "App $index",
                                        modifier = Modifier.height(64.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(60) { index ->
                            Text(
                                text = "Game $index",
                                modifier = Modifier.height(140.dp)
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return state
    }

    @Test
    fun `switching to games closes app drawer and resets games grid to top`() {
        val state = setupHarness()

        // Open app drawer and scroll down
        composeRule.runOnIdle {
            state.scope.launch {
                state.appsPagerState.scrollToPage(1)
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            state.scope.launch {
                state.appsListState.scrollToItem(20)
                state.appsGridState.scrollToItem(18)
                state.gridState.scrollToItem(15)
                state.gamesRowState.scrollToItem(10)
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, state.appsPagerState.currentPage)
        assertEquals(20, state.appsListState.firstVisibleItemIndex)

        // Switch tab to Games
        composeRule.runOnIdle {
            state.currentTab = MainLibraryTab.GAMES
        }
        composeRule.waitForIdle()

        assertEquals(1, state.pagerState.currentPage)
        assertEquals(0, state.appsPagerState.currentPage)
        assertEquals(0, state.gridState.firstVisibleItemIndex)
        assertEquals(0, state.gridState.firstVisibleItemScrollOffset)
        assertEquals(0, state.gamesRowState.firstVisibleItemIndex)
        assertEquals(0, state.gamesRowState.firstVisibleItemScrollOffset)
        assertEquals(0, state.appsListState.firstVisibleItemIndex)
        assertEquals(0, state.appsGridState.firstVisibleItemIndex)
    }

    @Test
    fun `swiping horizontally to games closes app drawer`() {
        val state = setupHarness()

        composeRule.runOnIdle {
            state.scope.launch {
                state.appsPagerState.scrollToPage(1)
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, state.appsPagerState.currentPage)

        // Simulate horizontal pager navigation to Games
        composeRule.runOnIdle {
            state.scope.launch {
                state.pagerState.animateScrollToPage(1)
            }
        }
        composeRule.waitForIdle()

        assertEquals(MainLibraryTab.GAMES, state.currentTab)
        assertEquals(0, state.appsPagerState.currentPage)
    }

    @Test
    fun `reopening app drawer starts scrolled to top`() {
        val state = setupHarness()

        composeRule.runOnIdle {
            state.scope.launch {
                state.appsPagerState.scrollToPage(1)
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            state.scope.launch {
                state.appsListState.scrollToItem(25)
                state.appsGridState.scrollToItem(24)
            }
        }
        composeRule.waitForIdle()
        assertEquals(25, state.appsListState.firstVisibleItemIndex)

        // Close drawer back to favorites
        composeRule.runOnIdle {
            state.scope.launch {
                state.appsPagerState.animateScrollToPage(0)
            }
        }
        composeRule.waitForIdle()

        // Reopen drawer
        composeRule.runOnIdle {
            state.scope.launch {
                state.appsPagerState.animateScrollToPage(1)
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, state.appsPagerState.currentPage)
        assertEquals(0, state.appsListState.firstVisibleItemIndex)
        assertEquals(0, state.appsListState.firstVisibleItemScrollOffset)
        assertEquals(0, state.appsGridState.firstVisibleItemIndex)
        assertEquals(0, state.appsGridState.firstVisibleItemScrollOffset)
    }

    @Test
    fun `reentering games tab resets games grid to top`() {
        val state = setupHarness()

        composeRule.runOnIdle {
            state.currentTab = MainLibraryTab.GAMES
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            state.scope.launch {
                state.gridState.scrollToItem(21)
                state.gamesRowState.scrollToItem(12)
            }
        }
        composeRule.waitForIdle()
        assertEquals(21, state.gridState.firstVisibleItemIndex)

        // Switch to Apps
        composeRule.runOnIdle {
            state.currentTab = MainLibraryTab.APPS
        }
        composeRule.waitForIdle()

        // Switch back to Games
        composeRule.runOnIdle {
            state.currentTab = MainLibraryTab.GAMES
        }
        composeRule.waitForIdle()

        assertEquals(0, state.gridState.firstVisibleItemIndex)
        assertEquals(0, state.gridState.firstVisibleItemScrollOffset)
        assertEquals(0, state.gamesRowState.firstVisibleItemIndex)
        assertEquals(0, state.gamesRowState.firstVisibleItemScrollOffset)
    }

    @Test
    fun `switching tabs and recovering partial pager offsets settle cleanly without getting stuck`() {
        val state = setupHarness()

        // Animate to Games via currentTab, wait until settled -> zero offset fraction, exact page 1.
        composeRule.runOnIdle { state.currentTab = MainLibraryTab.GAMES }
        composeRule.waitForIdle()
        assertEquals(1, state.pagerState.settledPage)
        assertEquals(MainLibraryTab.GAMES, state.currentTab)
        assertTrue(abs(state.pagerState.currentPageOffsetFraction) < 0.005f)

        // Simulate an interrupted mid-scroll leaving horizontal pager stuck partway off-center.
        composeRule.runOnIdle {
            state.scope.launch {
                state.pagerState.scroll { scrollBy(-200f) }
            }
        }
        composeRule.waitForIdle()
        assertTrue(
            "Expected horizontal pager to settle cleanly after interrupt, offset was ${state.pagerState.currentPageOffsetFraction}",
            abs(state.pagerState.currentPageOffsetFraction) < 0.005f
        )

        // Return to Apps and simulate an interrupted vertical drawer offset.
        composeRule.runOnIdle { state.currentTab = MainLibraryTab.APPS }
        composeRule.waitForIdle()
        assertEquals(0, state.pagerState.settledPage)
        assertEquals(MainLibraryTab.APPS, state.currentTab)

        composeRule.runOnIdle {
            state.scope.launch {
                state.appsPagerState.scroll { scrollBy(220f) }
            }
        }
        composeRule.waitForIdle()
        assertTrue(
            "Expected vertical apps pager to settle cleanly after interrupt, offset was ${state.appsPagerState.currentPageOffsetFraction}",
            abs(state.appsPagerState.currentPageOffsetFraction) < 0.005f
        )
    }

    @Test
    fun `pager transitions with focused children complete cleanly without mid-flight focus bounce`() {
        var currentTab by mutableStateOf(MainLibraryTab.APPS)
        lateinit var pagerState: PagerState
        lateinit var appsPagerState: PagerState
        lateinit var scope: CoroutineScope
        val appsRequester = FocusRequester()
        val drawerRequester = FocusRequester()
        val gamesRequester = FocusRequester()

        composeRule.setContent {
            pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
            appsPagerState = rememberPagerState(pageCount = { 2 })
            scope = rememberCoroutineScope()

            BindLibraryPagerAndScrollEffects(
                currentTab = currentTab,
                appsHomeReset = 0,
                pagerState = pagerState,
                appsPagerState = appsPagerState,
                gridState = rememberLazyGridState(),
                gamesRowState = rememberLazyListState(),
                appsListState = rememberLazyListState(),
                appsGridState = rememberLazyGridState(),
                onTabFromPager = { tab -> if (currentTab != tab) currentTab = tab }
            )

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == 0) {
                    val appsCanFocus = if (pagerState.isScrollInProgress) {
                        pagerState.settledPage == 0
                    } else {
                        pagerState.currentPage == 0
                    }
                    VerticalPager(
                        state = appsPagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusProperties { canFocus = appsCanFocus }
                    ) { appsPage ->
                        val pageCanFocus = appsCanFocus && if (appsPagerState.isScrollInProgress) {
                            appsPagerState.settledPage == appsPage
                        } else {
                            appsPagerState.currentPage == appsPage
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .focusProperties { canFocus = pageCanFocus }
                                .focusRequester(if (appsPage == 0) appsRequester else drawerRequester)
                                .focusable()
                        ) {
                            Text(if (appsPage == 0) "Favorites" else "Drawer")
                        }
                    }
                } else {
                    val gamesCanFocus = if (pagerState.isScrollInProgress) {
                        pagerState.settledPage == 1
                    } else {
                        pagerState.currentPage == 1
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusProperties { canFocus = gamesCanFocus }
                            .focusRequester(gamesRequester)
                            .focusable()
                    ) {
                        Text("Games")
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { appsRequester.requestFocus() }
        composeRule.waitForIdle()

        // Transition vertical pager from focused Favorites to App List -> must finish at settledPage 1.
        composeRule.runOnIdle {
            scope.launch { appsPagerState.animateScrollToPage(1) }
        }
        composeRule.waitForIdle()
        assertEquals(1, appsPagerState.settledPage)
        assertTrue(abs(appsPagerState.currentPageOffsetFraction) < 0.005f)

        // Focus drawer then switch horizontal tab to Games -> must settle on Games without bouncing back.
        composeRule.runOnIdle { drawerRequester.requestFocus() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { currentTab = MainLibraryTab.GAMES }
        composeRule.waitForIdle()
        assertEquals(1, pagerState.settledPage)
        assertEquals(MainLibraryTab.GAMES, currentTab)
        assertTrue(abs(pagerState.currentPageOffsetFraction) < 0.005f)
        assertEquals(0, appsPagerState.settledPage)
    }
}
