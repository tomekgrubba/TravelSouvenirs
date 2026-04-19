package com.travelsouvenirs.main.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.travelsouvenirs.main.ui.add.AddMagnetScreen
import com.travelsouvenirs.main.ui.detail.MagnetDetailScreen
import com.travelsouvenirs.main.ui.main.MainScreen

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object AddMagnet : Screen("add_magnet")
    object MagnetDetail : Screen("magnet_detail/{magnetId}") {
        fun createRoute(magnetId: Long) = "magnet_detail/$magnetId"
    }
    object EditItem : Screen("edit_item/{magnetId}") {
        fun createRoute(magnetId: Long) = "edit_item/$magnetId"
    }
}

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
            val magnetId = backStackEntry.arguments!!.getLong("magnetId")
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
            val magnetId = backStackEntry.arguments!!.getLong("magnetId")
            AddMagnetScreen(
                onSaved = { navController.popBackStack() },
                magnetId = magnetId
            )
        }
    }
}
