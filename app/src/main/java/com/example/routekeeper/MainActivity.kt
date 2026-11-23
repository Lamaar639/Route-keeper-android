package com.example.routekeeper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.ui.screens.ActiveRouteScreen
import com.example.routekeeper.ui.screens.DriverProgressScreen
import com.example.routekeeper.ui.screens.HistoryScreen
import com.example.routekeeper.ui.screens.HomeScreen
import com.example.routekeeper.ui.screens.ImportScreen
import com.example.routekeeper.ui.screens.PreRouteReviewScreen
import com.example.routekeeper.ui.screens.SettingsScreen
import com.example.routekeeper.ui.screens.StopDetailScreen
import com.example.routekeeper.ui.screens.VideoExportDialog
import com.example.routekeeper.ui.theme.RouteKeeperTheme
import com.example.routekeeper.ui.theme.RouteTheme
import com.example.routekeeper.ui.viewmodel.RouteViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object ActiveRoute : Screen("route", "Route", Icons.Default.Navigation)
    data object Import : Screen("import", "Import", Icons.Default.UploadFile)
    data object History : Screen("history", "History", Icons.Default.History)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object PreRouteReview : Screen("pre_route_review", "Review")
    data object StopDetail : Screen("stop_detail", "Stop Details")
    data object DriverProgress : Screen("driver_progress", "Driver Progress")
}

class MainActivity : ComponentActivity() {
    private val incomingShareIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingShareIntent.value = intent
        setContent {
            RouteKeeperApp(
                incomingShareIntentFlow = incomingShareIntent,
                onConsumeShareIntent = { incomingShareIntent.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingShareIntent.value = intent
    }
}

private fun extractSharedPdfUri(intent: Intent?): Uri? {
    if (intent == null) return null
    return when (intent.action) {
        Intent.ACTION_SEND -> {
            val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                ?: intent.data
            if (streamUri != null) {
                streamUri
            } else {
                val textUri = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                if (textUri != null && (textUri.startsWith("content://") || textUri.startsWith("file://"))) {
                    Uri.parse(textUri)
                } else {
                    null
                }
            }
        }
        Intent.ACTION_VIEW -> {
            intent.data ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        }
        else -> null
    }
}

@Composable
fun RouteKeeperApp(
    viewModel: RouteViewModel = viewModel(),
    incomingShareIntentFlow: MutableStateFlow<Intent?>? = null,
    onConsumeShareIntent: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var driverProgressReturnScreen by remember { mutableStateOf<Screen>(Screen.ActiveRoute) }
    var preRouteReturnScreen by remember { mutableStateOf<Screen>(Screen.Import) }
    var selectedStopDetail by remember { mutableStateOf<DeliveryStop?>(null) }
    val isVideoExportDialogOpen by viewModel.isVideoExportDialogOpen.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val incomingIntent = incomingShareIntentFlow?.collectAsState()?.value
    val isImportingPdf by viewModel.isImportingPdf.collectAsState()
    val pendingPdfNavigation by viewModel.pendingPdfNavigation.collectAsState()
    val pdfImportMessage by viewModel.pdfImportMessage.collectAsState()
    val pdfImportError by viewModel.pdfImportError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle direct PDF shares from Android Share Sheet (ACTION_SEND) or Open-With (ACTION_VIEW)
    LaunchedEffect(incomingIntent) {
        val sharedUri = extractSharedPdfUri(incomingIntent)
        if (sharedUri != null) {
            onConsumeShareIntent()
            viewModel.importSharedPdfUri(sharedUri)
        }
    }

    // Auto-navigate to Pre-Route Review (or Import on fallback) after PDF extraction completes
    LaunchedEffect(pendingPdfNavigation) {
        when (pendingPdfNavigation) {
            "pre_route_review" -> {
                preRouteReturnScreen = Screen.Import
                currentScreen = Screen.PreRouteReview
                viewModel.consumePendingPdfNavigation()
            }
            "import" -> {
                currentScreen = Screen.Import
                viewModel.consumePendingPdfNavigation()
            }
        }
    }

    // Show user-facing feedback when a shared PDF finishes auto-importing or fails
    LaunchedEffect(pdfImportMessage) {
        val msg = pdfImportMessage
        if (!msg.isNullOrBlank() && msg != "Reading shared PDF manifest...") {
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(pdfImportError) {
        val err = pdfImportError
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
        }
    }

    val navBarItems = listOf(
        Screen.Home,
        Screen.ActiveRoute,
        Screen.Import,
        Screen.History,
        Screen.Settings
    )

    // Clean back handling: when viewing an active route, Back returns directly to Home
    // without forcing the user back through the pre-route options checklist
    BackHandler(enabled = currentScreen != Screen.Home) {
        when (currentScreen) {
            Screen.StopDetail -> currentScreen = Screen.ActiveRoute
            Screen.DriverProgress -> currentScreen = driverProgressReturnScreen
            Screen.PreRouteReview -> currentScreen = preRouteReturnScreen
            Screen.ActiveRoute -> currentScreen = Screen.Home
            else -> currentScreen = Screen.Home
        }
    }

    RouteKeeperTheme(themeId = preferences.appTheme) {
        val colors = RouteTheme.colors

        // Video Export modal dialog
        if (isVideoExportDialogOpen) {
            VideoExportDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.closeVideoExportDialog() }
            )
        }

        val showBottomBar = currentScreen != Screen.StopDetail &&
            currentScreen != Screen.PreRouteReview &&
            currentScreen != Screen.DriverProgress

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.canvasBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = colors.cardSurface,
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
                                    selectedIconColor = colors.onAccent,
                                    selectedTextColor = colors.brandPrimary,
                                    indicatorColor = colors.accent,
                                    unselectedIconColor = colors.textMuted,
                                    unselectedTextColor = colors.textMuted
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
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
                        onNavigateBack = { currentScreen = Screen.Home },
                        onNavigateToOptions = {
                            preRouteReturnScreen = Screen.ActiveRoute
                            currentScreen = Screen.PreRouteReview
                        },
                        onNavigateToDriverProgress = {
                            viewModel.setProgressTargetRoute(null)
                            driverProgressReturnScreen = Screen.ActiveRoute
                            currentScreen = Screen.DriverProgress
                        },
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
                    Screen.DriverProgress -> DriverProgressScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = driverProgressReturnScreen }
                    )
                    Screen.Import -> ImportScreen(
                        viewModel = viewModel,
                        onNavigateToPreRouteReview = {
                            preRouteReturnScreen = Screen.Import
                            currentScreen = Screen.PreRouteReview
                        }
                    )
                    Screen.PreRouteReview -> PreRouteReviewScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = preRouteReturnScreen },
                        onNavigateToActiveRoute = { currentScreen = Screen.ActiveRoute }
                    )
                    Screen.StopDetail -> {
                        val stop = selectedStopDetail
                        if (stop != null) {
                            StopDetailScreen(
                                stop = stop,
                                viewModel = viewModel,
                                onNavigateBack = { currentScreen = Screen.ActiveRoute }
                            )
                        } else {
                            LaunchedEffect(Unit) {
                                currentScreen = Screen.ActiveRoute
                            }
                        }
                    }
                    Screen.History -> HistoryScreen(
                        viewModel = viewModel,
                        onSelectRoute = { route ->
                            viewModel.loadSavedRouteAsActive(route)
                            currentScreen = Screen.ActiveRoute
                        },
                        onNavigateToDriverProgress = { route ->
                            viewModel.setProgressTargetRoute(route.id)
                            driverProgressReturnScreen = Screen.History
                            currentScreen = Screen.DriverProgress
                        }
                    )
                    Screen.Settings -> SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }

            if (isImportingPdf) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.heroCardBg)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = colors.accent,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = pdfImportMessage ?: "Auto-importing shared PDF...",
                                color = colors.onHeroPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
