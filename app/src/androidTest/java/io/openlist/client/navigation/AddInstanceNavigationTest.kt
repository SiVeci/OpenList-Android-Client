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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddInstanceNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    @Test
    fun saveFromFirstRunOpensNewInstanceLoginAsRoot() {
        setGraph(startDestination = Routes.ADD_INSTANCE)

        composeRule.runOnIdle {
            navController.navigateAfterInstanceSaved("instance-1")
        }

        composeRule.runOnIdle {
            assertEquals(Routes.LOGIN, navController.currentDestination?.route)
            assertEquals(
                "instance-1",
                navController.currentBackStackEntry?.arguments?.getString("instanceId"),
            )
            assertFalse(navController.popBackStack())
        }
    }

    @Test
    fun backFromFirstRunFallsBackToEmptyHomeAsRoot() {
        setGraph(startDestination = Routes.ADD_INSTANCE)

        composeRule.runOnIdle {
            navController.leaveAddInstance()
        }

        composeRule.runOnIdle {
            assertEquals(Routes.INSTANCE_LIST, navController.currentDestination?.route)
            assertFalse(navController.popBackStack())
        }
    }

    @Test
    fun backFromHomeAddFlowPopsToExistingHome() {
        setGraph(startDestination = Routes.INSTANCE_LIST)

        composeRule.runOnIdle {
            navController.navigate(Routes.ADD_INSTANCE)
        }
        composeRule.runOnIdle {
            assertEquals(Routes.ADD_INSTANCE, navController.currentDestination?.route)
            navController.leaveAddInstance()
        }

        composeRule.runOnIdle {
            assertEquals(Routes.INSTANCE_LIST, navController.currentDestination?.route)
            assertFalse(navController.popBackStack())
        }
    }

    @Test
    fun saveFromHomeAddFlowAlsoOpensLoginAsRoot() {
        setGraph(startDestination = Routes.INSTANCE_LIST)

        composeRule.runOnIdle {
            navController.navigate(Routes.ADD_INSTANCE)
        }
        composeRule.runOnIdle {
            navController.navigateAfterInstanceSaved("instance-2")
        }

        composeRule.runOnIdle {
            assertEquals(Routes.LOGIN, navController.currentDestination?.route)
            assertEquals(
                "instance-2",
                navController.currentBackStackEntry?.arguments?.getString("instanceId"),
            )
            assertFalse(navController.popBackStack())
        }
    }

    private fun setGraph(startDestination: String) {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
                composable(Routes.INSTANCE_LIST) { Text("home") }
                composable(Routes.ADD_INSTANCE) { Text("add instance") }
                composable(Routes.LOGIN) { Text("login") }
            }
        }
        composeRule.waitForIdle()
    }
}
