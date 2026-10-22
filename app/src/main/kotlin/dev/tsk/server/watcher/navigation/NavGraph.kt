package dev.tsk.server.watcher.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.tsk.server.watcher.AppViewModel
import dev.tsk.server.watcher.feature.auth.AuthScreen
import dev.tsk.server.watcher.feature.domaindetail.DomainDetailScreen
import dev.tsk.server.watcher.feature.home.HomeScreen
import dev.tsk.server.watcher.feature.instancedetail.InstanceDetailScreen
import dev.tsk.server.watcher.feature.onboarding.OnboardingScreen
import dev.tsk.server.watcher.feature.settings.SettingsScreen
import dev.tsk.server.watcher.feature.splash.SplashScreen

private const val TAG = "SW"

private object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DOMAIN_DETAIL = "domain_detail/{domainId}"
    const val INSTANCE_DETAIL = "instance_detail/{instanceId}"

    fun domainDetail(domainId: String) = "domain_detail/$domainId"
    fun instanceDetail(instanceId: String) = "instance_detail/$instanceId"
}

@Composable
fun NavGraph(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        appViewModel.sessionExpired.collect {
            Log.d(TAG, "[Nav] session expired — forcing navigation to auth")
            navController.navigate(Routes.AUTH) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onReady = {
                    val destination = when {
                        !appViewModel.isConfigured() -> Routes.ONBOARDING
                        appViewModel.isLoggedIn()    -> Routes.HOME
                        else                         -> Routes.AUTH
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.AUTH) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onDomainClick = { domainId ->
                    navController.navigate(Routes.domainDetail(domainId))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.DOMAIN_DETAIL,
            arguments = listOf(navArgument("domainId") { type = NavType.StringType }),
        ) {
            DomainDetailScreen(
                onBack = { navController.popBackStack() },
                onInstanceClick = { instanceId ->
                    navController.navigate(Routes.instanceDetail(instanceId))
                },
            )
        }

        composable(
            route = Routes.INSTANCE_DETAIL,
            arguments = listOf(navArgument("instanceId") { type = NavType.StringType }),
        ) {
            InstanceDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
