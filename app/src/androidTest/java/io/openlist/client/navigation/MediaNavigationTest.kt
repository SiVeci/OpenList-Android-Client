package io.openlist.client.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    @Test
    fun mediaHandoffRemovesPreviewFromBackStack() {
        setGraph()
        val path = "/媒体/演示 video.mp4"

        composeRule.runOnIdle {
            navController.navigate(Routes.preview("instance-1", path))
        }
        composeRule.runOnIdle {
            navController.navigateFromPreviewToMediaPlayer("instance-1", path)
        }
        composeRule.runOnIdle {
            assertEquals(Routes.MEDIA_PLAYER, navController.currentDestination?.route)
            assertTrue(navController.popBackStack())
            assertEquals("origin", navController.currentDestination?.route)
            assertFalse(navController.popBackStack())
        }
    }

    @Test
    fun duplicateMediaHandoffKeepsSinglePlayerEntry() {
        setGraph()
        val path = "/audio/test.mp3"

        composeRule.runOnIdle {
            navController.navigate(Routes.preview("instance-1", path))
        }
        composeRule.runOnIdle {
            navController.navigateFromPreviewToMediaPlayer("instance-1", path)
        }
        composeRule.runOnIdle {
            navController.navigateFromPreviewToMediaPlayer("instance-1", path)
        }
        composeRule.runOnIdle {
            assertEquals(Routes.MEDIA_PLAYER, navController.currentDestination?.route)
            assertTrue(navController.popBackStack())
            assertEquals("origin", navController.currentDestination?.route)
            assertFalse(navController.popBackStack())
        }
    }

    private fun setGraph() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = "origin") {
                composable("origin") { Text("origin") }
                composable(Routes.PREVIEW) { Text("preview") }
                composable(Routes.MEDIA_PLAYER) { Text("player") }
            }
        }
        composeRule.waitForIdle()
    }
}
