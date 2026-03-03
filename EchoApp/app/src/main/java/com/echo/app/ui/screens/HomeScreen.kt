package com.echo.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.app.ui.theme.*
import com.echo.app.viewmodel.EchoViewModel
import java.util.Locale

@Composable
fun HomeScreen(viewModel: EchoViewModel) {
    val isConnected by viewModel.isConnected.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val activeTasks by viewModel.activeTasks.collectAsState()
    val config = LocalConfiguration.current
    val isCompact = config.screenWidthDp < 360

    // Speech recognition result
    var lastSpokenText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                lastSpokenText = spokenText
                // Route based on connection: PC command if online, assistant if offline
                if (isConnected) {
                    viewModel.sendCommand(spokenText)
                } else {
                    viewModel.sendAssistantMessage(spokenText)
                }
            }
        }
    }

    // Pulsating animation for the mic button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 600 else 1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = if (isListening) 0.7f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 500 else 2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    val micSize = if (isCompact) 200 else 260
    val mainBtnSize = if (isCompact) 100 else 130
    val darkRingSize = if (isCompact) 112 else 145

    // Mic button color — green when listening, cyan normally
    val micColor = if (isListening) GreenAccent else CyanPrimary
    val micDarkColor = if (isListening) Color(0xFF1B5E20) else CyanDark

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isCompact) 16.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Status pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isConnected) BgCard else Color(0xFF2A1520),
            border = BorderStroke(1.dp, if (isConnected) CyanDark.copy(alpha = 0.5f) else RedAccent.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(
                        color = if (isConnected) GreenAccent else RedAccent,
                        radius = size.minDimension / 2
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isConnected) "PC Connected: Online" else "PC Offline · AI Ready",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // System snapshot row (only when connected)
        if (isConnected) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniStat("CPU", "${stats.cpu_percent.toInt()}%", CyanPrimary)
                Spacer(modifier = Modifier.width(16.dp))
                MiniStat("RAM", "${stats.ram_percent.toInt()}%", PurpleAccent)
                Spacer(modifier = Modifier.width(16.dp))
                MiniStat("DISK", "${stats.disk_percent.toInt()}%", RedAccent)
                if (stats.battery_percent >= 0) {
                    Spacer(modifier = Modifier.width(16.dp))
                    MiniStat("BAT", "${stats.battery_percent}%", YellowAccent)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isListening) "🎙️ Listening..." else if (isConnected) "Tap mic to speak a PC command" else "Tap mic to ask AI anything",
            color = if (isListening) GreenAccent else TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal
        )

        // Show last spoken text
        if (lastSpokenText.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "\"$lastSpokenText\"",
                color = CyanPrimary.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Show active tasks from queue
        if (activeTasks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = BgCard,
                border = BorderStroke(1.dp, BgCardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Active Tasks", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    activeTasks.take(2).forEach { task ->
                        val isRunning = task.status == "running"
                        val statusColor = if (isRunning) YellowAccent else TextMuted
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    color = YellowAccent,
                                    strokeWidth = 1.5.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "[${task.source.uppercase()}] ${task.command}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = task.status.uppercase(),
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (activeTasks.size > 2) {
                        Text(
                            text = "+ ${activeTasks.size - 2} more queued...",
                            color = TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isCompact) 20.dp else 36.dp))

        // Mic button with rings
        Box(
            modifier = Modifier.size(micSize.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer ring 3
            Canvas(modifier = Modifier.size(micSize.dp)) {
                drawCircle(
                    color = micColor.copy(alpha = ringAlpha * 0.3f),
                    style = Stroke(width = 1.dp.toPx()),
                    radius = size.minDimension / 2
                )
            }
            // Outer ring 2
            Canvas(modifier = Modifier.size((micSize * 0.85).dp)) {
                drawCircle(
                    color = micColor.copy(alpha = ringAlpha * 0.5f),
                    style = Stroke(width = 1.dp.toPx()),
                    radius = size.minDimension / 2
                )
            }
            // Outer ring 1
            Canvas(modifier = Modifier.size((micSize * 0.7).dp)) {
                drawCircle(
                    color = micColor.copy(alpha = ringAlpha * 0.7f),
                    style = Stroke(width = 1.5.dp.toPx()),
                    radius = size.minDimension / 2
                )
            }

            // Main mic button
            Box(
                modifier = Modifier
                    .size((mainBtnSize * pulseScale).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(micColor, micDarkColor),
                            center = Offset(0.5f, 0.3f),
                            radius = 300f
                        )
                    )
                    .clickable {
                        isListening = true
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, if (isConnected) "Speak a PC command..." else "Ask Echo anything...")
                        }
                        speechLauncher.launch(intent)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = "Tap to speak",
                    tint = Color.White,
                    modifier = Modifier.size(if (isCompact) 36.dp else 48.dp)
                )
            }

            // Dark inner ring
            Canvas(modifier = Modifier.size((darkRingSize * pulseScale).dp)) {
                drawCircle(
                    color = Color(0xFF1A2030),
                    style = Stroke(width = 8.dp.toPx()),
                    radius = size.minDimension / 2
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isListening) "Listening..." else "Tap to Speak",
            color = if (isListening) GreenAccent else TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(if (isCompact) 24.dp else 36.dp))

        // Quick Actions header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "View All",
                color = CyanPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick action cards
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
        ) {
            item { QuickActionCard("Take\nScreenshot", Icons.Default.CameraAlt, CyanPrimary, isCompact) { viewModel.takeScreenshot() } }
            item { QuickActionCard("Lock\nPC", Icons.Default.Lock, RedAccent, isCompact) { viewModel.sendPowerAction("lock") } }
            item { QuickActionCard("Mute\nVolume", Icons.Default.VolumeOff, YellowAccent, isCompact) { viewModel.sendCommand("set volume to 0") } }
            item { QuickActionCard("Open\nChrome", Icons.Default.Language, GreenAccent, isCompact) { viewModel.sendCommand("open chrome") } }
            item { QuickActionCard("Show\nDesktop", Icons.Default.Computer, PurpleAccent, isCompact) { viewModel.sendCommand("press win+d") } }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun QuickActionCard(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    isCompact: Boolean = false,
    onClick: () -> Unit
) {
    val cardWidth = if (isCompact) 85.dp else 100.dp
    Surface(
        modifier = Modifier
            .width(cardWidth)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BgCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(if (isCompact) 12.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(if (isCompact) 38.dp else 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
                )
            }
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 10.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = if (isCompact) 11.sp else 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
