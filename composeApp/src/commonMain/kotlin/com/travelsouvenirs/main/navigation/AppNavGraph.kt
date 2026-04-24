package com.travelsouvenirs.main.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.travelsouvenirs.main.ui.add.AddMagnetScreen
import com.travelsouvenirs.main.ui.detail.MagnetDetailScreen
import com.travelsouvenirs.main.ui.main.MainScreen

/** Sealed hierarchy of all top-level navigation destinations. */
sealed class Screen(val route: String) {
    /** Two-tab home screen (Map + List). */
    object Main : Screen("main")
    /** Add new item form. */
    object AddMagnet : Screen("add_magnet")
    /** Detail view for an existing item; requires a `magnetId` path argument. */
    object MagnetDetail : Screen("magnet_detail/{magnetId}") {
        /** Builds the concrete route string for the given [magnetId]. */
        fun createRoute(magnetId: Long) = "magnet_detail/$magnetId"
    }
    /** Edit form for an existing item; requires a `magnetId` path argument. */
    object EditItem : Screen("edit_item/{magnetId}") {
        /** Builds the concrete route string for the given [magnetId]. */
        fun createRoute(magnetId: Long) = "edit_item/$magnetId"
    }
}

/** Wires all screens into a [NavHost] starting at [Screen.Main]. */
@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Main.route) {
        composable(Screen.Main.route) {
            MainScreen(
                onAddClick = { navController.navigate(Screen.AddMagnet.route) },
                onItemClick = { magnetId ->
                    navController.navigate(Screen.MagnetDetail.createRoute(magnetId))
                }
            )
        }
        composable(Screen.AddMagnet.route) {
            AddMagnetScreen(onSaved = { navController.popBackStack() })
        }
        composable(
            route = Screen.MagnetDetail.route,
            arguments = listOf(navArgument("magnetId") { type = NavType.LongType })
        ) { backStackEntry ->
            val magnetId = backStackEntry.arguments?.read { getLong("magnetId") } ?: 0L
            MagnetDetailScreen(
                magnetId = magnetId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.EditItem.createRoute(magnetId)) }
            )
        }
        composable(
            route = Screen.EditItem.route,
            arguments = listOf(navArgument("magnetId") { type = NavType.LongType })
        ) { backStackEntry ->
            val magnetId = backStackEntry.arguments?.read { getLong("magnetId") } ?: 0L
            AddMagnetScreen(
                onSaved = { navController.popBackStack() },
                magnetId = magnetId
            )
        }
    }
}
