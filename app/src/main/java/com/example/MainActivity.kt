package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.i18n.AppStrings
import com.example.data.i18n.ArabicStrings
import com.example.data.i18n.EnglishStrings
import com.example.data.prefs.PreferencesManager
import com.example.data.queue.TaskQueueManager
import com.example.data.queue.DynamicIslandState
import com.example.ui.components.DynamicIslandHeader
import com.example.ui.components.DynamicIslandNavBar
import com.example.ui.components.OceanBackground
import com.example.ui.screens.AudioScreen
import com.example.ui.screens.ConversionScreen
import com.example.ui.screens.DocumentScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoScreen
import com.example.ui.theme.VodaTheme

import com.example.data.util.SharedImportManager

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.theme.CyanPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        SharedImportManager.handleIntent(this, intent)
        enableEdgeToEdge()
        setContent {
            VodaAppMain()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SharedImportManager.handleIntent(this, intent)
    }
}

@Composable
fun VodaAppMain() {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager.getInstance(context) }
    val queueManager = remember { TaskQueueManager.getInstance(context) }

    var showNotificationDialog by remember { mutableStateOf(false) }
    var showStoragePermissionDialog by remember { mutableStateOf(false) }

    // Request notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    // Request storage permission on Android 7-9 (API 24-28)
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                showNotificationDialog = true
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                showStoragePermissionDialog = true
            }
        }
    }

    val langCode by prefsManager.languageCode.collectAsState(initial = "ar")
    val isDarkTheme by prefsManager.darkTheme.collectAsState(initial = true)
    val islandState by queueManager.islandState.collectAsState()

    val strings: AppStrings = if (langCode == "ar") ArabicStrings else EnglishStrings
    val layoutDir = if (langCode == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    LaunchedEffect(Unit) {
        val route = SharedImportManager.targetRoute
        if (!route.isNullOrBlank()) {
            SharedImportManager.targetRoute = null
            if (route == SharedImportManager.ROUTE_UNSUPPORTED) {
                // Do not open any processing screen for unsupported shared files.
                SharedImportManager.consumeUris()
                android.widget.Toast.makeText(
                    context,
                    "${strings.unsupportedFileTypeTitle}\n${strings.unsupportedFileTypeMessage}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                navController.navigate(route)
            }
        }
    }


    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        VodaTheme(darkTheme = isDarkTheme) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Animated Canvas Ocean Background
                OceanBackground(
                    darkTheme = isDarkTheme,
                    isProcessing = islandState is DynamicIslandState.Processing
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    bottomBar = {
                        DynamicIslandNavBar(
                            currentRoute = currentRoute,
                            strings = strings,
                            onNavigate = { route ->
                                if (route == "home") {
                                    if (currentRoute != "home") {
                                        val popped = navController.popBackStack("home", false)
                                        if (!popped) {
                                            navController.navigate("home") {
                                                popUpTo("home") { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                } else if (currentRoute != route) {
                                    navController.navigate(route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            enterTransition = { fadeIn(animationSpec = tween(250)) + scaleIn(initialScale = 0.96f, animationSpec = tween(250)) },
                            exitTransition = { fadeOut(animationSpec = tween(200)) },
                            popEnterTransition = { fadeIn(animationSpec = tween(250)) + scaleIn(initialScale = 0.96f, animationSpec = tween(250)) },
                            popExitTransition = { fadeOut(animationSpec = tween(200)) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable("home") {
                                HomeScreen(
                                    strings = strings,
                                    onNavigateToCategory = { route -> navController.navigate(route) }
                                )
                            }
                            composable("video") {
                                VideoScreen(
                                    strings = strings,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("audio") {
                                AudioScreen(
                                    strings = strings,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("image") {
                                ImageScreen(
                                    strings = strings,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("files") {
                                DocumentScreen(
                                    strings = strings,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("convert") {
                                ConversionScreen(
                                    strings = strings,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("queue") {
                                QueueScreen(strings = strings)
                            }
                            composable("history") {
                                HistoryScreen(strings = strings)
                            }
                            composable("settings") {
                                SettingsScreen(strings = strings)
                            }
                        }

                        // Top Floating Capsule Header
                        DynamicIslandHeader(
                            strings = strings,
                            islandState = islandState,
                            onMenuClick = {
                                if (currentRoute != "settings") {
                                    navController.navigate("settings")
                                }
                            },
                            onQueueClick = {
                                if (currentRoute != "queue") {
                                    navController.navigate("queue")
                                }
                            },
                            onHomeClick = {
                                if (currentRoute != "home") {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        )
                    }
                }
            }

            if (showNotificationDialog) {
                AlertDialog(
                    onDismissRequest = { showNotificationDialog = false },
                    title = {
                        Text(
                            text = strings.notificationPermissionTitle,
                            color = CyanPrimary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Text(
                            text = strings.notificationPermissionBody,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showNotificationDialog = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        ) {
                            Text(strings.confirm, color = CyanPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNotificationDialog = false }) {
                            Text(strings.cancel, color = Color(0xFFA0AAB5))
                        }
                    },
                    containerColor = Color(0xFF131822)
                )
            }

            if (showStoragePermissionDialog) {
                AlertDialog(
                    onDismissRequest = { showStoragePermissionDialog = false },
                    title = {
                        Text(
                            text = strings.storagePermissionTitle,
                            color = CyanPrimary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Text(
                            text = strings.storagePermissionBody,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showStoragePermissionDialog = false
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            }
                        ) {
                            Text(strings.confirm, color = CyanPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStoragePermissionDialog = false }) {
                            Text(strings.cancel, color = Color(0xFFA0AAB5))
                        }
                    },
                    containerColor = Color(0xFF131822)
                )
            }
        }
    }
}
