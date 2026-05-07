package com.travelsouvenirs.main.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.travelsouvenirs.main.di.LocalAppViewModel
import com.travelsouvenirs.main.ui.add.AddItemScreen
import com.travelsouvenirs.main.ui.auth.SignInScreen
import com.travelsouvenirs.main.ui.detail.ItemDetailScreen
import com.travelsouvenirs.main.ui.main.AppViewModel
import com.travelsouvenirs.main.ui.main.MainScreen
import com.travelsouvenirs.main.ui.settings.SettingsScreen
import org.koin.compose.viewmodel.koinViewModel

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object AddItem : Screen("add_item")
    object ItemDetail : Screen("item_detail/{itemId}") {
        fun createRoute(itemId: Long) = "item_detail/$itemId"
    }
    object EditItem : Screen("edit_item/{itemId}") {
        fun createRoute(itemId: Long) = "edit_item/$itemId"
    }
    object Settings : Screen("settings")
    object SignIn : Screen("sign_in")
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    val appViewModel: AppViewModel = koinViewModel()
    CompositionLocalProvider(LocalAppViewModel provides appViewModel) {
        NavHost(navController = navController, startDestination = Screen.Main.route) {
            composable(Screen.Main.route) {
                MainScreen(
                    onAddClick = { navController.navigate(Screen.AddItem.route) },
                    onItemClick = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                )
            }
            composable(Screen.AddItem.route) {
                AddItemScreen(
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
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
                    onBack = { navController.popBackStack() },
                    itemId = itemId
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignInClick = { navController.navigate(Screen.SignIn.route) }
                )
            }
            composable(Screen.SignIn.route) {
                SignInScreen(onSignedIn = { navController.popBackStack() })
            }
        }
    }
}
