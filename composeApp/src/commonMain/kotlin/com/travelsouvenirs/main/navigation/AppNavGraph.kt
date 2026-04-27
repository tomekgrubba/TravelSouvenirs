package com.travelsouvenirs.main.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.travelsouvenirs.main.ui.add.AddItemScreen
import com.travelsouvenirs.main.ui.detail.ItemDetailScreen
import com.travelsouvenirs.main.ui.main.MainScreen

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object AddItem : Screen("add_item")
    object ItemDetail : Screen("item_detail/{itemId}") {
        fun createRoute(itemId: Long) = "item_detail/$itemId"
    }
    object EditItem : Screen("edit_item/{itemId}") {
        fun createRoute(itemId: Long) = "edit_item/$itemId"
    }
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Main.route) {
        composable(Screen.Main.route) {
            MainScreen(
                onAddClick = { navController.navigate(Screen.AddItem.route) },
                onItemClick = { itemId ->
                    navController.navigate(Screen.ItemDetail.createRoute(itemId))
                }
            )
        }
        composable(Screen.AddItem.route) {
            AddItemScreen(onSaved = { navController.popBackStack() })
        }
        composable(
            route = Screen.ItemDetail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.read { getLong("itemId") } ?: 0L
            ItemDetailScreen(
                itemId = itemId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.EditItem.createRoute(itemId)) }
            )
        }
        composable(
            route = Screen.EditItem.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.read { getLong("itemId") } ?: 0L
            AddItemScreen(
                onSaved = { navController.popBackStack() },
                itemId = itemId
            )
        }
    }
}
