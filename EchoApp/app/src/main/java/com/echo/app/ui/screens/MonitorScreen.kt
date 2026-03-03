package com.echo.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.app.ui.theme.*
import com.echo.app.viewmodel.EchoViewModel

@Composable
fun MonitorScreen(viewModel: EchoViewModel) {
    val stats by viewModel.stats.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val netSpeed by viewModel.netSpeedMbps.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        // Gradient Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0D1219),
                            Color(0xFF111D2B),
                            Color(0xFF0D1219)
                        )
                    )
                )
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SYSTEM MONITOR", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(8.dp)) {
                        drawCircle(color = if (isConnected) GreenAccent else RedAccent)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isConnected) "CONNECTED" else "DISCONNECTED",
                        color = if (isConnected) GreenAccent else RedAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Gauge row: CPU, RAM, DISK
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CircularGauge(
                label = "CPU",
                value = stats.cpu_percent,
                icon = Icons.Default.Memory,
                color = CyanPrimary,
                displayText = "${stats.cpu_percent.toInt()}%"
            )
            CircularGauge(
                label = "RAM",
                value = stats.ram_percent,
                icon = Icons.Default.Storage,
                color = PurpleAccent,
                displayText = "${stats.ram_percent.toInt()}%"
            )
            CircularGauge(
                label = "DISK",
                value = stats.disk_percent,
                icon = Icons.Default.Folder,
                color = RedAccent,
                displayText = "${stats.disk_percent.toInt()}%"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Active Window card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(1.dp, BgCardBorder)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyanPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Laptop, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("ACTIVE WINDOW", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stats.active_window.take(40),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (stats.active_window_pid > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgSecondary,
                        border = BorderStroke(1.dp, BgCardBorder)
                    ) {
                        Text(
                            "PID: ${stats.active_window_pid}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Battery card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(1.dp, BgCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BATTERY LEVEL", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(
                        text = if (stats.battery_percent >= 0) "${stats.battery_percent}%" else "N/A",
                        color = CyanPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Battery bar
                val batteryPct = if (stats.battery_percent >= 0) stats.battery_percent / 100f else 0f
                val barColor = when {
                    stats.battery_percent > 60 -> CyanPrimary
                    stats.battery_percent > 20 -> YellowAccent
                    else -> RedAccent
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgSecondary)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(batteryPct)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (stats.battery_plugged) "Plugged in" else "On battery",
                        color = TextMuted, fontSize = 12.sp
                    )
                    val timeLeft = if (stats.battery_secs_left > 0) {
                        val h = stats.battery_secs_left / 3600
                        val m = (stats.battery_secs_left % 3600) / 60
                        "${h}h ${m}m remaining"
                    } else ""
                    Text(timeLeft, color = TextMuted, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Network + GPU row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Network card
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                border = BorderStroke(1.dp, BgCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("NETWORK", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.1f".format(netSpeed), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mb/s", color = TextMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // GPU card
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                border = BorderStroke(1.dp, BgCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gamepad, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GPU LOAD", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (stats.gpu_percent >= 0) "${stats.gpu_percent}" else "N/A",
                            color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold
                        )
                        if (stats.gpu_percent >= 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("%", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    if (stats.gpu_percent >= 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(BgSecondary)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(stats.gpu_percent.toFloat() / 100f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(CyanPrimary)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Power Controls
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CyanPrimary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("POWER CONTROLS", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PowerCard(Modifier.weight(1f), "Sleep", Icons.Default.Bedtime, Color(0xFF4A90D9)) { viewModel.sendPowerAction("sleep") }
            PowerCard(Modifier.weight(1f), "Restart", Icons.Default.Refresh, YellowAccent) { viewModel.sendPowerAction("restart") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PowerCard(Modifier.weight(1f), "Shutdown", Icons.Default.PowerSettingsNew, RedAccent) { viewModel.sendPowerAction("shutdown") }
            PowerCard(Modifier.weight(1f), "Lock", Icons.Default.Lock, TextSecondary) { viewModel.sendPowerAction("lock") }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun CircularGauge(
    label: String,
    value: Float,
    icon: ImageVector,
    color: Color,
    displayText: String,
    size: Dp = 100.dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            // Background arc
            Canvas(modifier = Modifier.size(size)) {
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                    size = Size(
                        this.size.width - 16.dp.toPx(),
                        this.size.height - 16.dp.toPx()
                    )
                )
            }
            // Value arc
            Canvas(modifier = Modifier.size(size)) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * (value / 100f).coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                    size = Size(
                        this.size.width - 16.dp.toPx(),
                        this.size.height - 16.dp.toPx()
                    )
                )
            }
            // Icon + value
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(displayText, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PowerCard(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BgCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
