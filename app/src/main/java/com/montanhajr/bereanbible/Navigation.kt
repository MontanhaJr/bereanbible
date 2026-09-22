package com.montanhajr.bereanbible

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    object Bible : Screen("bible")
    object Books : Screen("books")
    object Chapters : Screen("chapters/{bookId}") {
        fun path(bookId: String) = "chapters/$bookId"
    }
    object Search : Screen("search")
    object Listening : Screen("listening")
    object Settings : Screen("settings")
}

@Composable
fun BibliaPregacaoApp(vm: BibleViewModel) {
    val navController = rememberNavController()
    MaterialTheme {
        Scaffold(
            bottomBar = { AppBottomBar(navController) }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Bible.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.Bible.route) { BibleReaderScreen(vm, navController) }
                composable(Screen.Books.route) { BookSelectorScreen(vm, navController) }
                composable(
                    route = Screen.Chapters.route,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId")
                    if (bookId != null) ChapterSelectorScreen(vm, bookId, navController)
                }
                composable(Screen.Search.route) { SearchScreen(vm) }
                composable(Screen.Listening.route) { ListeningScreen(vm) }
                composable(Screen.Settings.route) { SettingsScreen(vm) }
            }
        }
    }
}

@Composable
fun AppBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Screen.Bible.route,
            onClick = { navController.navigate(Screen.Bible.route) { launchSingleTop = true } },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Bíblia") }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Search.route,
            onClick = { navController.navigate(Screen.Search.route) { launchSingleTop = true } },
            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
            label = { Text("Buscar") }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Listening.route,
            onClick = { navController.navigate(Screen.Listening.route) { launchSingleTop = true } },
            icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
            label = { Text("Escutar") }
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Settings.route,
            onClick = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Config.") }
        )
    }
}
