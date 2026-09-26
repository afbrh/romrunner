package com.noryan.romrunner.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noryan.romrunner.data.repository.LibraryRepository
import com.noryan.romrunner.ui.controller.ControllerMappingScreen
import com.noryan.romrunner.ui.controller.PlatformControllerMappingScreen
import com.noryan.romrunner.ui.library.LibraryScreen
import com.noryan.romrunner.ui.platforms.PlatformEditScreen
import com.noryan.romrunner.ui.platforms.PlatformSettingsScreen

object Routes {
    const val LIBRARY = "library"
    const val PLATFORM_EDIT = "platformEdit/{platformId}"
    const val PLATFORM_SETTINGS = "platformSettings/{platformId}"
    const val CONTROLLER_MAPPING = "controllerMapping"
    const val CONTROLLER_MAPPING_FOR_PLATFORM = "controllerMapping/{platformId}"
    fun platformEdit(id: Long) = "platformEdit/$id"
    fun platformSettings(id: Long) = "platformSettings/$id"
    fun controllerMappingForPlatform(id: Long) = "controllerMapping/$id"
}

@Composable
fun RomRunnerNavHost(repository: LibraryRepository) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            // RomRunner can be set as the device's default Home app; if it is, there's nothing
            // "back" of this root screen to return to, so swallow the back press here instead of
            // letting it fall through to the Activity's default finish() — which would exit to
            // whatever launcher sits underneath, defeating the point of being set as Home.
            BackHandler(enabled = true) {}
            LibraryScreen(
                repository = repository,
                onOpenControllerMapping = { navController.navigate(Routes.CONTROLLER_MAPPING) },
                onOpenPlatformSettings = { id -> navController.navigate(Routes.platformSettings(id)) }
            )
        }
        composable(
            route = Routes.PLATFORM_EDIT,
            arguments = listOf(navArgument("platformId") { type = NavType.LongType })
        ) { backStackEntry ->
            val platformId = backStackEntry.arguments?.getLong("platformId") ?: -1L
            PlatformEditScreen(
                repository = repository,
                platformId = platformId,
                onDone = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PLATFORM_SETTINGS,
            arguments = listOf(navArgument("platformId") { type = NavType.LongType })
        ) { backStackEntry ->
            val platformId = backStackEntry.arguments?.getLong("platformId") ?: -1L
            PlatformSettingsScreen(
                repository = repository,
                platformId = platformId,
                onEditDetails = { navController.navigate(Routes.platformEdit(platformId)) },
                onOpenControllerMapping = { navController.navigate(Routes.controllerMappingForPlatform(platformId)) },
                onDone = { navController.popBackStack() }
            )
        }
        composable(Routes.CONTROLLER_MAPPING) {
            ControllerMappingScreen(
                title = "Controller Mapping",
                initialMapping = repository.getControllerMapping(),
                onSave = { mapping -> repository.setControllerMapping(mapping) },
                onDone = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.CONTROLLER_MAPPING_FOR_PLATFORM,
            arguments = listOf(navArgument("platformId") { type = NavType.LongType })
        ) { backStackEntry ->
            val platformId = backStackEntry.arguments?.getLong("platformId") ?: -1L
            PlatformControllerMappingScreen(
                repository = repository,
                platformId = platformId,
                onDone = { navController.popBackStack() }
            )
        }
    }
}
