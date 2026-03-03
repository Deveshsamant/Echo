package com.echo.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.app.EchoApplication
import com.echo.app.api.EchoApiClient
import com.echo.app.model.*
import com.echo.app.ui.theme.*
import com.echo.app.viewmodel.EchoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(viewModel: EchoViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var volume by remember { mutableFloatStateOf(50f) }
    var clipboard by remember { mutableStateOf("") }
    var processes by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
    var showProcesses by remember { mutableStateOf(false) }

    // Load initial volume
    LaunchedEffect(Unit) {
        try {
            val ip = EchoApplication.getPcIp()
            if (ip.isNotBlank()) {
                val api = EchoApiClient.getApi(ip)
                val vol = withContext(Dispatchers.IO) { api.getVolume() }
                volume = vol.volume.toFloat()
            }
        } catch (_: Exception) {}
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("CONTROL CENTER", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ─── Volume Control ─────────────────────────
        SectionHeader("Volume & Media")
        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(1.dp, BgCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Volume slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (volume <= 0) Icons.Default.VolumeOff
                        else if (volume < 50) Icons.Default.VolumeDown
                        else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        onValueChangeFinished = {
                            scope.launch {
                                try {
                                    val ip = EchoApplication.getPcIp()
                                    if (ip.isNotBlank()) {
                                        val api = EchoApiClient.getApi(ip)
                                        withContext(Dispatchers.IO) {
                                            api.setVolume(VolumeRequest(volume.toInt()))
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = CyanPrimary,
                            activeTrackColor = CyanPrimary,
                            inactiveTrackColor = BgCardBorder
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${volume.toInt()}%",
                        color = CyanPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.width(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Media control buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MediaButton(Icons.Default.SkipPrevious, "Prev") {
                        scope.launch {
                            try {
                                val ip = EchoApplication.getPcIp()
                                val api = EchoApiClient.getApi(ip)
                                withContext(Dispatchers.IO) { api.mediaControl(MediaRequest("prev")) }
                            } catch (_: Exception) {}
                        }
                    }
                    MediaButton(Icons.Default.PlayArrow, "Play/Pause") {
                        scope.launch {
                            try {
                                val ip = EchoApplication.getPcIp()
                                val api = EchoApiClient.getApi(ip)
                                withContext(Dispatchers.IO) { api.mediaControl(MediaRequest("play_pause")) }
                            } catch (_: Exception) {}
                        }
                    }
                    MediaButton(Icons.Default.SkipNext, "Next") {
                        scope.launch {
                            try {
                                val ip = EchoApplication.getPcIp()
                                val api = EchoApiClient.getApi(ip)
                                withContext(Dispatchers.IO) { api.mediaControl(MediaRequest("next")) }
                            } catch (_: Exception) {}
                        }
                    }
                    MediaButton(Icons.Default.VolumeOff, "Mute") {
                        scope.launch {
                            try {
                                val ip = EchoApplication.getPcIp()
                                val api = EchoApiClient.getApi(ip)
                                withContext(Dispatchers.IO) { api.mediaControl(MediaRequest("mute")) }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Quick Launch ────────────────────────────
        SectionHeader("Quick Launch")
        Spacer(modifier = Modifier.height(12.dp))

        val apps = listOf(
            Triple("Chrome", Icons.Default.Language, Color(0xFF4285F4)),
            Triple("VS Code", Icons.Default.Code, Color(0xFF007ACC)),
            Triple("Notepad", Icons.Default.Edit, Color(0xFF6B7280)),
            Triple("Explorer", Icons.Default.Folder, Color(0xFFF59E0B)),
            Triple("Terminal", Icons.Default.Terminal, Color(0xFF10B981)),
            Triple("Spotify", Icons.Default.MusicNote, Color(0xFF1DB954)),
            Triple("Discord", Icons.Default.Chat, Color(0xFF5865F2)),
            Triple("Telegram", Icons.Default.Send, Color(0xFF26A5E4)),
        )

        // 4-column grid
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            apps.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { (name, icon, color) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    scope.launch {
                                        try {
                                            val ip = EchoApplication.getPcIp()
                                            val api = EchoApiClient.getApi(ip)
                                            withContext(Dispatchers.IO) {
                                                api.launchApp(LaunchRequest(name))
                                            }
                                            Toast.makeText(context, "Opening $name", Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {}
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = BgCard,
                            border = BorderStroke(1.dp, BgCardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = name, tint = color, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(name, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                            }
                        }
                    }
                    // Fill remaining slots in last row
                    repeat(4 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Clipboard Sync ─────────────────────────
        SectionHeader("Clipboard")
        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(1.dp, BgCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = clipboard,
                    onValueChange = { clipboard = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Clipboard text...", color = TextMuted) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = BgCardBorder,
                        cursorColor = CyanPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BgSecondary,
                        unfocusedContainerColor = BgSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val ip = EchoApplication.getPcIp()
                                    val api = EchoApiClient.getApi(ip)
                                    val result = withContext(Dispatchers.IO) { api.getClipboard() }
                                    clipboard = result.text
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyanDark)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Read PC", color = CyanPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val ip = EchoApplication.getPcIp()
                                    val api = EchoApiClient.getApi(ip)
                                    withContext(Dispatchers.IO) { api.setClipboard(ClipboardRequest(clipboard)) }
                                    Toast.makeText(context, "Clipboard sent to PC", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send to PC", color = BgPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Process Manager ─────────────────────────
        SectionHeader("Processes")
        Spacer(modifier = Modifier.height(12.dp))

        var sortBy by remember { mutableStateOf("ram") }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(1.dp, BgCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = {
                        showProcesses = !showProcesses
                        if (showProcesses) {
                            scope.launch {
                                try {
                                    val ip = EchoApplication.getPcIp()
                                    val api = EchoApiClient.getApi(ip)
                                    val result = withContext(Dispatchers.IO) { api.getProcesses(sortBy) }
                                    processes = result.processes
                                } catch (_: Exception) {}
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showProcesses) RedAccent.copy(alpha = 0.2f) else CyanPrimary.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        if (showProcesses) Icons.Default.Close else Icons.Default.List,
                        contentDescription = null,
                        tint = if (showProcesses) RedAccent else CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (showProcesses) "Hide Processes" else "Show Running Processes",
                        color = if (showProcesses) RedAccent else CyanPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showProcesses) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Sort tabs row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgSecondary)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("name" to "Name", "cpu" to "CPU", "ram" to "RAM", "disk" to "Disk").forEach { (key, label) ->
                            val isActive = sortBy == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isActive) CyanPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        width = if (isActive) 1.dp else 0.dp,
                                        color = if (isActive) CyanPrimary.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        sortBy = key
                                        scope.launch {
                                            try {
                                                val ip = EchoApplication.getPcIp()
                                                val api = EchoApiClient.getApi(ip)
                                                val result = withContext(Dispatchers.IO) { api.getProcesses(key) }
                                                processes = result.processes
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isActive) "▼ $label" else label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) CyanPrimary else TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Column headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Process", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("CPU", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                        Text("RAM", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                        Text("Disk", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                        Spacer(modifier = Modifier.width(32.dp))
                    }
                    HorizontalDivider(color = BgCardBorder, thickness = 1.dp)

                    if (processes.isNotEmpty()) {
                        processes.forEach { proc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Process name
                                Text(
                                    proc.name,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                // CPU
                                Text(
                                    "${proc.cpu}%",
                                    color = if (proc.cpu > 10) Color(0xFFEF4444) else if (proc.cpu > 3) Color(0xFFF59E0B) else TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(48.dp)
                                )
                                // RAM
                                Text(
                                    if (proc.ram_mb >= 1024) "${"%.1f".format(proc.ram_mb / 1024)}G"
                                    else "${proc.ram_mb.toInt()}M",
                                    color = if (proc.ram_mb > 500) Color(0xFFF59E0B) else TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(52.dp)
                                )
                                // Disk
                                Text(
                                    if (proc.disk_mb >= 1024) "${"%.1f".format(proc.disk_mb / 1024)}G"
                                    else "${proc.disk_mb.toInt()}M",
                                    color = if (proc.disk_mb > 100) Color(0xFFF59E0B) else TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(52.dp)
                                )
                                // Kill button
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val ip = EchoApplication.getPcIp()
                                                val api = EchoApiClient.getApi(ip)
                                                withContext(Dispatchers.IO) { api.killProcess(KillRequest(proc.pid)) }
                                                processes = processes.filter { it.pid != proc.pid }
                                                Toast.makeText(context, "Killed ${proc.name}", Toast.LENGTH_SHORT).show()
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Kill", tint = RedAccent, modifier = Modifier.size(14.dp))
                                }
                            }
                            HorizontalDivider(color = BgCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Send Notification ──────────────────────
        SectionHeader("Send Notification")
        Spacer(modifier = Modifier.height(12.dp))

        var notifyTitle by remember { mutableStateOf("") }
        var notifyMsg by remember { mutableStateOf("") }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(1.dp, BgCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = notifyTitle,
                    onValueChange = { notifyTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Title", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = BgCardBorder,
                        cursorColor = CyanPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BgSecondary,
                        unfocusedContainerColor = BgSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notifyMsg,
                    onValueChange = { notifyMsg = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Message", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = BgCardBorder,
                        cursorColor = CyanPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BgSecondary,
                        unfocusedContainerColor = BgSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val ip = EchoApplication.getPcIp()
                                val api = EchoApiClient.getApi(ip)
                                withContext(Dispatchers.IO) {
                                    api.sendNotification(NotifyRequest(notifyTitle, notifyMsg))
                                }
                                Toast.makeText(context, "Notification sent!", Toast.LENGTH_SHORT).show()
                                notifyTitle = ""
                                notifyMsg = ""
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    enabled = notifyMsg.isNotBlank()
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send to PC", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun MediaButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(CyanPrimary.copy(alpha = 0.12f))
                .border(1.dp, CyanPrimary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = CyanPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}
