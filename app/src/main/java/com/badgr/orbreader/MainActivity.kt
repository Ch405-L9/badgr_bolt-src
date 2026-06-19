package com.badgr.orbreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.badgr.orbreader.data.preferences.THEME_DARK
import com.badgr.orbreader.data.preferences.THEME_LIGHT
import com.badgr.orbreader.data.preferences.UserPreferencesRepository
import com.badgr.orbreader.ui.account.AccountScreen
import com.badgr.orbreader.ui.components.WalkthroughOverlay
import com.badgr.orbreader.ui.components.WalkthroughStep
import com.badgr.orbreader.ui.library.LibraryScreen
import com.badgr.orbreader.ui.onboarding.OnboardingScreen
import com.badgr.orbreader.ui.reader.ReaderScreen
import com.badgr.orbreader.ui.settings.SettingsScreen
import com.badgr.orbreader.ui.stats.StatsScreen
import com.badgr.orbreader.ui.theme.OrbreaderTheme
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Onboarding : Screen("onboarding", "Onboarding", Icons.Default.Person)
    data object Library    : Screen("library",    "Library",    Icons.Default.CollectionsBookmark)
    data object Stats      : Screen("stats",      "Stats",      Icons.Default.QueryStats)
    data object Settings   : Screen("settings",   "Settings",   Icons.Default.Settings)
    data object Account    : Screen("account",    "Account",    Icons.Default.Person)
}

class MainActivity : ComponentActivity() {

    private lateinit var prefsRepo: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsRepo = UserPreferencesRepository(this)
        enableEdgeToEdge()

        setContent {
            val prefs by prefsRepo.preferences.collectAsStateWithLifecycle(
                initialValue = com.badgr.orbreader.data.preferences.UserPreferences()
            )

            val systemDark = isSystemInDarkTheme()
            val useDark = when (prefs.themeMode) {
                THEME_LIGHT -> false
                THEME_DARK -> true
                else -> systemDark
            }

            OrbreaderTheme(darkTheme = useDark) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val showBottomNav =
                    currentDestination?.route?.startsWith("reader") != true &&
                            currentDestination?.route != Screen.Onboarding.route

                Scaffold(
                    bottomBar = {
                        if (showBottomNav) {
                            NavigationBar {
                                val items = listOf(
                                    Screen.Library,
                                    Screen.Stats,
                                    Screen.Settings,
                                    Screen.Account
                                )

                                items.forEach { screen ->
                                    val isSelected =
                                        currentDestination?.hierarchy?.any { it.route == screen.route } == true

                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, contentDescription = null) },
                                        label = { Text(screen.label) },
                                        selected = isSelected,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val showWalkthrough =
                            remember { mutableStateOf(!prefs.hasSeenHelp && prefs.hasSeenOnboarding) }

                        Box(modifier = Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = if (prefs.hasSeenOnboarding) {
                                    Screen.Library.route
                                } else {
                                    Screen.Onboarding.route
                                }
                            ) {
                                composable(Screen.Onboarding.route) {
                                    OnboardingScreen(onGetStarted = {
                                        lifecycleScope.launch {
                                            prefsRepo.setHasSeenOnboarding(true)
                                            navController.navigate(Screen.Library.route) {
                                                popUpTo(Screen.Onboarding.route) {
                                                    inclusive = true
                                                }
                                            }
                                        }
                                    })
                                }

                                composable(Screen.Library.route) {
                                    LibraryScreen(onOpenBook = { bookId ->
                                        navController.navigate("reader/$bookId")
                                    })
                                }

                                composable("reader/{bookId}") { backStackEntry ->
                                    val bookId = backStackEntry.arguments?.getString("bookId")
                                        ?: return@composable

                                    ReaderScreen(
                                        bookId = bookId,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(Screen.Stats.route) {
                                    StatsScreen(onNavigateToAccount = {
                                        navController.navigate(Screen.Account.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    })
                                }

                                composable(Screen.Settings.route) {
                                    SettingsScreen(onNavigateToAccount = {
                                        navController.navigate(Screen.Account.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    })
                                }

                                composable(Screen.Account.route) {
                                    AccountScreen()
                                }
                            }

                            if (showWalkthrough.value) {
                                WalkthroughOverlay(
                                    steps = listOf(
                                        WalkthroughStep(
                                            "Library",
                                            "This is where all your books live. Tap the + button to import PDF, EPUB, or TXT files."
                                        ),
                                        WalkthroughStep(
                                            "Stats",
                                            "Track your reading speed, words read, and unlock achievements as you improve."
                                        ),
                                        WalkthroughStep(
                                            "Settings",
                                            "Customize your reading experience. Change fonts, colors, and speed to match your preference."
                                        ),
                                        WalkthroughStep(
                                            "Account",
                                            "Sign in to sync your library across devices and unlock Pro features."
                                        ),
                                        WalkthroughStep(
                                            "Welcome Guide",
                                            "We've added a welcome guide in your library. Open it anytime for a detailed manual!"
                                        )
                                    ),
                                    onStepChange = { index ->
                                        val routes = listOf(
                                            Screen.Library.route,
                                            Screen.Stats.route,
                                            Screen.Settings.route,
                                            Screen.Account.route,
                                            Screen.Library.route
                                        )
                                        if (index < routes.size) {
                                            navController.navigate(routes[index]) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    onComplete = {
                                        showWalkthrough.value = false
                                        lifecycleScope.launch {
                                            prefsRepo.setHasSeenHelp(true)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val purchaseManager = (application as OrbReaderApp).purchaseManager
        if (purchaseManager.isConnected.value) {
            lifecycleScope.launch {
                purchaseManager.queryExistingPurchases()
            }
        }
    }
}