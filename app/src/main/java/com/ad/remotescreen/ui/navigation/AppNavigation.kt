package com.ad.remotescreen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ad.remotescreen.ui.screen.ControllerScreen
import com.ad.remotescreen.ui.screen.OnboardingScreen
import com.ad.remotescreen.ui.screen.PairingScreen
import com.ad.remotescreen.ui.screen.RoleSelectionScreen
import com.ad.remotescreen.ui.screen.SettingsScreen
import com.ad.remotescreen.ui.screen.TargetScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val ROLE_SELECTION = "role_selection"
    const val PAIRING = "pairing/{role}"
    const val CONTROLLER = "controller/{pairingCode}"
    const val TARGET = "target"
    const val SETTINGS = "settings"
    
    fun pairing(role: String) = "pairing/$role"
    fun controller(pairingCode: String) = "controller/$pairingCode"
}

/**
 * Main navigation host for the app.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.ONBOARDING
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.ROLE_SELECTION) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Routes.ROLE_SELECTION) {
            RoleSelectionScreen(
                onControllerSelected = {
                    navController.navigate(Routes.pairing("controller"))
                },
                onTargetSelected = {
                    navController.navigate(Routes.pairing("target"))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
        
        composable(
            route = Routes.PAIRING,
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "controller"
            PairingScreen(
                isController = role == "controller",
                onConnected = { pairingCode ->
                    if (role == "controller") {
                        navController.navigate(Routes.controller(pairingCode)) {
                            popUpTo(Routes.ROLE_SELECTION)
                        }
                    } else {
                        navController.navigate(Routes.TARGET) {
                            popUpTo(Routes.ROLE_SELECTION)
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Routes.CONTROLLER,
            arguments = listOf(navArgument("pairingCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val pairingCode = backStackEntry.arguments?.getString("pairingCode") ?: ""
            ControllerScreen(
                pairingCode = pairingCode,
                onDisconnect = {
                    navController.navigate(Routes.ROLE_SELECTION) {
                        popUpTo(Routes.ROLE_SELECTION) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Routes.TARGET) {
            TargetScreen(
                onSessionEnded = {
                    navController.navigate(Routes.ROLE_SELECTION) {
                        popUpTo(Routes.ROLE_SELECTION) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
