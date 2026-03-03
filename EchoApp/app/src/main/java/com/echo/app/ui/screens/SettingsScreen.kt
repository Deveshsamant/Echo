package com.echo.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.app.EchoApplication
import com.echo.app.ui.theme.*
import com.echo.app.viewmodel.EchoViewModel
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: EchoViewModel) {
    var pcIp by remember { mutableStateOf(EchoApplication.getPcIp()) }
    var botToken by remember { mutableStateOf(EchoApplication.getBotToken()) }
    var apiKey by remember { mutableStateOf(EchoApplication.getAiApiKey()) }
    var connectionStatus by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .verticalScroll(rememberScrollState())
    ) {

        // Auto-update pcIp when a new tunnel URL is discovered
        val discoveredUrl by viewModel.discoveredPcIp.collectAsState()
        LaunchedEffect(discoveredUrl) {
            if (discoveredUrl.isNotBlank()) {
                pcIp = discoveredUrl
            }
        }
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
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("SETTINGS", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ─── AI Model Selection ──────────────────────────
        SectionHeader("AI Model")
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
                // Current status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeProvider.isNotBlank()) "Provider: $activeProvider" else "Provider: nvidia",
                        color = TextPrimary, fontSize = 13.sp
                    )
                }
                if (activeModel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Model: $activeModel", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 26.dp))
                }
                Spacer(modifier = Modifier.height(14.dp))

                // Provider toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val providers = listOf(
                        Triple("nvidia", "NVIDIA", CyanPrimary),
                        Triple("ollama", "Ollama", GreenAccent)
                    )
                    providers.forEach { (key, label, color) ->
                        val isActive = activeProvider == key || (activeProvider.isBlank() && key == "nvidia")
                        OutlinedButton(
                            onClick = { viewModel.switchModel(key) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isActive) color.copy(alpha = 0.15f) else Color.Transparent
                            ),
                            border = BorderStroke(1.dp, if (isActive) color else BgCardBorder)
                        ) {
                            if (isActive) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(label, color = if (isActive) color else TextMuted, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ─── AI API Key ──────────────────────────────────
        SectionHeader("AI API Key")
        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard(
            icon = Icons.Default.Key,
            title = "NVIDIA API Key",
            subtitle = "Enables AI assistant when PC is offline"
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("nvapi-...", color = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = YellowAccent,
                    unfocusedBorderColor = BgCardBorder,
                    cursorColor = YellowAccent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = BgSecondary,
                    unfocusedContainerColor = BgSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Get your free key at build.nvidia.com",
                color = CyanPrimary,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ─── Connection Settings ─────────────────────────
        SectionHeader("Connection")
        Spacer(modifier = Modifier.height(12.dp))

        // PC IP / Tunnel URL
        SettingsCard(
            icon = Icons.Default.Computer,
            title = "PC Address",
            subtitle = "Local IP or tunnel URL"
        ) {
            OutlinedTextField(
                value = pcIp,
                onValueChange = { pcIp = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("IP or tunnel URL", color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Telegram Bot Token
        SettingsCard(
            icon = Icons.Default.Send,
            title = "Telegram Bot Token",
            subtitle = "Auto-connect from anywhere"
        ) {
            OutlinedTextField(
                value = botToken,
                onValueChange = { botToken = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Bot token from @BotFather", color = TextMuted) },
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
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Save & Connect buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    EchoApplication.savePcIp(pcIp.trim())
                    EchoApplication.saveBotToken(botToken.trim())
                    EchoApplication.saveAiApiKey(apiKey.trim())
                    viewModel.startPolling()
                    connectionStatus = "Settings saved!"
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save", fontWeight = FontWeight.Bold, color = BgPrimary)
            }

            Button(
                onClick = {
                    EchoApplication.saveBotToken(botToken.trim())
                    viewModel.discoverUrlFromBot(botToken.trim())
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                enabled = botToken.isNotBlank()
            ) {
                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Test Connection
        OutlinedButton(
            onClick = {
                EchoApplication.savePcIp(pcIp.trim())
                isTesting = true
                connectionStatus = "Testing..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) { viewModel.testConnection() }
                    connectionStatus = if (result) "✅ Connected!" else "❌ Connection failed"
                    isTesting = false
                    if (result) viewModel.startPolling()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CyanDark),
            enabled = !isTesting
        ) {
            if (isTesting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyanPrimary, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Test Connection", color = CyanPrimary, fontWeight = FontWeight.Bold)
        }

        // Discovery status
        val discoveryState = viewModel.discoveryStatus.collectAsState()
        if (discoveryState.value.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = discoveryState.value,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = when {
                    discoveryState.value.contains("✅") -> GreenAccent
                    discoveryState.value.contains("❌") -> RedAccent
                    else -> CyanPrimary
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (connectionStatus.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = connectionStatus,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = when {
                    connectionStatus.contains("✅") -> GreenAccent
                    connectionStatus.contains("❌") -> RedAccent
                    else -> CyanPrimary
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ─── How to Connect ──────────────────────────────
        SectionHeader("How to Connect")
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
                HelpStep("1", "Run python main.py on your PC")
                Spacer(modifier = Modifier.height(10.dp))
                HelpStep("2", "Enter Telegram Bot Token above")
                Spacer(modifier = Modifier.height(10.dp))
                HelpStep("3", "Tap 'Connect' — auto-discovers your PC!")
                Spacer(modifier = Modifier.height(10.dp))
                HelpStep("4", "Works from ANY network — anywhere")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App info
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Echo AI v2.0", color = TextMuted, fontSize = 12.sp)
                Text("JARVIS-like PC Controller + Personal AI", color = TextMuted, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CyanPrimary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(title.uppercase(), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BgCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = TextMuted, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun HelpStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CyanPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
