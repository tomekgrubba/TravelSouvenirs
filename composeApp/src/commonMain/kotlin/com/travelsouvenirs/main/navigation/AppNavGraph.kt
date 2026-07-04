package com.travelsouvenirs.main.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.travelsouvenirs.main.di.LocalAppViewModel
import com.travelsouvenirs.main.network.NetworkMonitor
import com.travelsouvenirs.main.sync.SyncCoordinator
import com.travelsouvenirs.main.ui.add.AddItemScreen
import com.travelsouvenirs.main.ui.auth.SignInScreen
import com.travelsouvenirs.main.ui.detail.ItemDetailScreen
import com.travelsouvenirs.main.ui.main.AppViewModel
import com.travelsouvenirs.main.ui.main.MainScreen
import com.travelsouvenirs.main.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.currentKoinScope
import org.koin.compose.koinInject

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object AddItem : Screen("add_item")
    object Settings : Screen("settings")
    object SignIn : Screen("sign_in")
}

@Serializable data class ItemDetailRoute(val itemId: Long)
@Serializable data class EditItemRoute(val itemId: Long)

@Composable
fun AppNavGraph(navController: NavHostController) {
    val koinScope = currentKoinScope()
    val appViewModel: AppViewModel = viewModel { koinScope.get<AppViewModel>() }
    val networkMonitor: NetworkMonitor = koinInject()
    val syncCoordinator: SyncCoordinator = koinInject()
    LaunchedEffect(networkMonitor, syncCoordinator) {
        networkMonitor.isConnected.collect { connected ->
            if (connected) syncCoordinator.sync()
        }
    }
    CompositionLocalProvider(LocalAppViewModel provides appViewModel) {
        NavHost(navController = navController, startDestination = Screen.Main.route) {
            composable(Screen.Main.route) {
                MainScreen(
                    onAddClick = { navController.navigate(Screen.AddItem.route) },
                    onItemClick = { itemId -> navController.navigate(ItemDetailRoute(itemId)) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onSignInClick = { navController.navigate(Screen.SignIn.route) }
                )
            }
            composable(Screen.AddItem.route) {
                AddItemScreen(
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable<ItemDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ItemDetailRoute>()
                ItemDetailScreen(
                    itemId = route.itemId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(EditItemRoute(route.itemId)) }
                )
            }
            composable<EditItemRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<EditItemRoute>()
                AddItemScreen(
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    itemId = route.itemId
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignInClick = { navController.navigate(Screen.SignIn.route) }
                )
            }
            composable(Screen.SignIn.route) {
                // Pop all the way to Main so the sync loading overlay is visible immediately.
                SignInScreen(
                    onBack = { navController.popBackStack() },
                    onSignedIn = { navController.popBackStack(Screen.Main.route, inclusive = false) },
                )
            }
        }
    }
}
