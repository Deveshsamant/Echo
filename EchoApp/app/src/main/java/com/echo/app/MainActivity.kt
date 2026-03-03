package com.echo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.echo.app.ui.screens.*
import com.echo.app.ui.theme.*
import com.echo.app.viewmodel.EchoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EchoTheme {
                RequestNotificationPermission()
                EchoMainScreen()
            }
        }
    }
}

@Composable
fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* granted or denied — we handle gracefully */ }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val accentColor: Color = CyanPrimary
) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home, CyanPrimary)
    object Chat : Screen("chat", "Remote", Icons.Filled.Chat, Icons.Outlined.Chat, PurpleAccent)
    object Assistant : Screen("assistant", "AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, YellowAccent)
    object Calendar : Screen("calendar", "Tasks", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, GreenAccent)
    object Control : Screen("control", "Control", Icons.Filled.Tune, Icons.Outlined.Tune, CyanPrimary)
    object Monitor : Screen("monitor", "System", Icons.Filled.Analytics, Icons.Outlined.Analytics, PurpleAccent)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, TextSecondary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoMainScreen() {
    val navController = rememberNavController()
    val viewModel: EchoViewModel = viewModel()

    // Start polling if IP is configured
    LaunchedEffect(Unit) {
        if (EchoApplication.getPcIp().isNotBlank()) {
            viewModel.startPolling()
        }
    }

    val screens = listOf(
        Screen.Home,
        Screen.Chat,
        Screen.Assistant,
        Screen.Calendar,
        Screen.Control,
        Screen.Monitor,
        Screen.Settings
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        containerColor = BgPrimary,
        bottomBar = {
            MagicalBottomBar(
                screens = screens,
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgPrimary)
        ) {
            composable(Screen.Home.route) { HomeScreen(viewModel) }
            composable(Screen.Chat.route) { ChatScreen(viewModel) }
            composable(Screen.Assistant.route) { AssistantScreen(viewModel) }
            composable(Screen.Calendar.route) { CalendarScreen(viewModel) }
            composable(Screen.Control.route) { ControlScreen(viewModel) }
            composable(Screen.Monitor.route) { MonitorScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
        }
    }
}

// ─── Magical Bottom Navigation Bar ─────────────────────────────────────

@Composable
fun MagicalBottomBar(
    screens: List<Screen>,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    // Animated glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "navGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    // Get current accent color for the glow
    val currentScreen = screens.find { it.route == currentRoute } ?: screens[0]
    val currentAccent = currentScreen.accentColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // === Glassmorphism container ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF141B25).copy(alpha = 0.95f),
                            Color(0xFF0D1219).copy(alpha = 0.98f)
                        )
                    )
                )
                .drawBehind {
                    // Top edge glow line
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                currentAccent.copy(alpha = glowAlpha * 0.6f),
                                currentAccent.copy(alpha = glowAlpha),
                                currentAccent.copy(alpha = glowAlpha * 0.6f),
                                Color.Transparent,
                            )
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f
                    )
                }
                .padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                screens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    MagicalNavItem(
                        screen = screen,
                        selected = selected,
                        glowAlpha = glowAlpha,
                        onClick = { onNavigate(screen) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun MagicalNavItem(
    screen: Screen,
    selected: Boolean,
    glowAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "scale"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.5f,
        animationSpec = tween(300),
        label = "iconAlpha"
    )

    val isAI = screen == Screen.Assistant

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon with glow effect
        Box(contentAlignment = Alignment.Center) {
            // Glow circle behind selected icon
            if (selected) {
                Box(
                    modifier = Modifier
                        .size((28 * animatedScale).dp)
                        .background(
                            screen.accentColor.copy(alpha = glowAlpha * 0.25f),
                            CircleShape
                        )
                )
            }

            Icon(
                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                contentDescription = screen.title,
                tint = if (selected) screen.accentColor else TextMuted.copy(alpha = iconAlpha),
                modifier = Modifier.size((if (selected) 22 * animatedScale else 20f).dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Label
        Text(
            text = screen.title,
            fontSize = if (selected) 9.sp else 8.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) screen.accentColor else TextMuted.copy(alpha = iconAlpha),
            maxLines = 1
        )

        // Animated dot indicator
        if (selected) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(screen.accentColor, CircleShape)
            )
        } else {
            Spacer(modifier = Modifier.height(7.dp))
        }
    }
}
