package com.example.routekeeper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.ui.screens.ActiveRouteScreen
import com.example.routekeeper.ui.screens.HistoryScreen
import com.example.routekeeper.ui.screens.HomeScreen
import com.example.routekeeper.ui.screens.ImportScreen
import com.example.routekeeper.ui.screens.PreRouteReviewScreen
import com.example.routekeeper.ui.screens.SettingsScreen
import com.example.routekeeper.ui.screens.StopDetailScreen
import com.example.routekeeper.ui.theme.MintAccent
import com.example.routekeeper.ui.theme.PinePrimary
import com.example.routekeeper.ui.theme.RouteKeeperTheme
import com.example.routekeeper.ui.theme.TextMuted
import com.example.routekeeper.ui.viewmodel.RouteViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object ActiveRoute : Screen("route", "Route", Icons.Default.Navigation)
    data object Import : Screen("import", "Import", Icons.Default.UploadFile)
    data object History : Screen("history", "History", Icons.Default.History)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object PreRouteReview : Screen("pre_route_review", "Review")
    data object StopDetail : Screen("stop_detail", "Stop Details")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RouteKeeperTheme {
                RouteKeeperApp()
            }
        }
    }
}

@Composable
fun RouteKeeperApp(
    viewModel: RouteViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var selectedStopDetail by remember { mutableStateOf<DeliveryStop?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val navBarItems = listOf(
        Screen.Home,
        Screen.ActiveRoute,
        Screen.Import,
        Screen.History,
        Screen.Settings
    )

    // Back handling for nested screens
    BackHandler(enabled = currentScreen != Screen.Home) {
        when (currentScreen) {
            Screen.StopDetail -> currentScreen = Screen.ActiveRoute
            Screen.PreRouteReview -> currentScreen = Screen.Import
            else -> currentScreen = Screen.Home
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentScreen != Screen.StopDetail && currentScreen != Screen.PreRouteReview) {
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.testTag("main_bottom_nav_bar")
                ) {
                    navBarItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen.route == screen.route,
                            onClick = { currentScreen = screen },
                            icon = {
                                screen.icon?.let {
                                    Icon(imageVector = it, contentDescription = screen.title)
                                }
                            },
                            label = { Text(screen.title, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PinePrimary,
                                selectedTextColor = PinePrimary,
                                indicatorColor = MintAccent,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.Home -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToRoute = { currentScreen = Screen.ActiveRoute },
                    onNavigateToImport = { currentScreen = Screen.Import },
                    onNavigateToHistory = { currentScreen = Screen.History }
                )
                Screen.ActiveRoute -> ActiveRouteScreen(
                    viewModel = viewModel,
                    onNavigateToStopDetail = { stop ->
                        selectedStopDetail = stop
                        currentScreen = Screen.StopDetail
                    },
                    onSaveRoute = {
                        viewModel.saveDraftToHistory()
                        scope.launch {
                            snackbarHostState.showSnackbar("Route saved to delivery history!")
                        }
                    }
                )
                Screen.Import -> ImportScreen(
                    viewModel = viewModel,
                    onNavigateToPreRouteReview = { currentScreen = Screen.PreRouteReview }
                )
                Screen.PreRouteReview -> PreRouteReviewScreen(
                    viewModel = viewModel,
                    onNavigateToActiveRoute = { currentScreen = Screen.ActiveRoute }
                )
                Screen.StopDetail -> selectedStopDetail?.let { stop ->
                    StopDetailScreen(
                        stop = stop,
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = Screen.ActiveRoute }
                    )
                }
                Screen.History -> HistoryScreen(
                    viewModel = viewModel,
                    onSelectRoute = {
                        currentScreen = Screen.ActiveRoute
                    }
                )
                Screen.Settings -> SettingsScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
