package com.echo.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.app.EchoApplication
import com.echo.app.api.EchoApiClient
import com.echo.app.model.*
import com.echo.app.util.NotificationHelper
import com.echo.app.api.LocalAiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class EchoViewModel : ViewModel() {

    // ─── System Stats ────────────────────────────────────────
    private val _stats = MutableStateFlow(SystemStats())
    val stats: StateFlow<SystemStats> = _stats.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // ─── PC Control Chat ─────────────────────────────────────
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage("System connected. Awaiting command input, Operator.", false))
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ─── Assistant Chat ──────────────────────────────────────
    private val _assistantMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage("Hey! I'm your personal AI assistant. How can I help? 💡", false))
    )
    val assistantMessages: StateFlow<List<ChatMessage>> = _assistantMessages.asStateFlow()

    private val _assistantLoading = MutableStateFlow(false)
    val assistantLoading: StateFlow<Boolean> = _assistantLoading.asStateFlow()

    // ─── Mode ────────────────────────────────────────────────
    private val _currentMode = MutableStateFlow("pc_control")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    // ─── Model Info ──────────────────────────────────────────
    private val _activeModel = MutableStateFlow("")
    val activeModel: StateFlow<String> = _activeModel.asStateFlow()

    private val _activeProvider = MutableStateFlow("")
    val activeProvider: StateFlow<String> = _activeProvider.asStateFlow()

    // ─── Calendar ────────────────────────────────────────────
    private val _calendarEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEvent>> = _calendarEvents.asStateFlow()

    // ─── Discovery ───────────────────────────────────────────
    private val _discoveryStatus = MutableStateFlow("")
    val discoveryStatus: StateFlow<String> = _discoveryStatus.asStateFlow()

    // ─── Task Queue ──────────────────────────────────────────
    private val _activeTasks = MutableStateFlow<List<QueuedTask>>(emptyList())
    val activeTasks: StateFlow<List<QueuedTask>> = _activeTasks.asStateFlow()

    // Reactively expose the discovered URL so Settings UI can update its pcIp field
    private val _discoveredPcIp = MutableStateFlow("")
    val discoveredPcIp: StateFlow<String> = _discoveredPcIp.asStateFlow()

    // ─── Network Speed ───────────────────────────────────────
    private var lastBytesRecv: Long = 0
    private var lastBytesSent: Long = 0
    private var lastNetCheckTime: Long = 0

    private val _netSpeedMbps = MutableStateFlow(0f)
    val netSpeedMbps: StateFlow<Float> = _netSpeedMbps.asStateFlow()

    private var pollingJob: Job? = null
    private var notificationPollingJob: Job? = null

    // ─── Notification State Tracking ─────────────────────────
    private var wasConnected: Boolean? = null  // null = unknown (first check)
    private var batteryAlerted = false
    private var cpuAlerted = false
    private var ramAlerted = false
    private var diskAlerted = false
    private var lastKnownTaskIds = mutableSetOf<Int>()
    private var lastKnownReminderIds = mutableSetOf<Int>()

    init {
        val token = EchoApplication.getBotToken()
        if (token.isNotBlank()) {
            discoverUrlFromBot(token)
        }
        // Periodically re-discover the tunnel URL in case it changes (server restart)
        viewModelScope.launch {
            while (isActive) {
                delay(60_000) // Check every 60 seconds
                val t = EchoApplication.getBotToken()
                if (t.isNotBlank()) {
                    discoverUrlFromBot(t, silent = true)
                }
            }
        }
    }

    // ─── Connection Discovery ────────────────────────────────

    fun discoverUrlFromBot(token: String? = null, silent: Boolean = false) {
        val botToken = token ?: EchoApplication.getBotToken()
        if (botToken.isBlank()) {
            if (!silent) _discoveryStatus.value = "❌ No bot token set"
            return
        }

        if (!silent) _discoveryStatus.value = "🔍 Discovering..."
        viewModelScope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    val conn = URL("https://api.telegram.org/bot$botToken/getMyDescription")
                        .openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    val response = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()

                    val json = JSONObject(response)
                    if (json.getBoolean("ok")) {
                        val desc = json.getJSONObject("result").getString("description")
                        val regex = Regex("https://[a-zA-Z0-9\\-]+\\.trycloudflare\\.com")
                        regex.find(desc)?.value
                    } else null
                }

                if (url != null) {
                    val oldUrl = EchoApplication.getPcIp()
                    EchoApplication.savePcIp(url)
                    _discoveredPcIp.value = url  // Notify UI reactively
                    _discoveryStatus.value = "✅ Connected: ${url.substringAfter("://").take(30)}..."
                    if (oldUrl != url) {
                        Log.i("EchoVM", "Auto-discovered NEW tunnel URL: $url (was: $oldUrl)")
                    }
                    startPolling()
                } else {
                    if (!silent) _discoveryStatus.value = "❌ No tunnel URL found in bot description"
                }
            } catch (e: Exception) {
                if (!silent) _discoveryStatus.value = "❌ Discovery failed: ${e.message}"
                Log.e("EchoVM", "Bot discovery failed: ${e.message}")
            }
        }
    }

    // ─── Stats Polling ───────────────────────────────────────

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchStats()
                delay(2000)
            }
        }
        // Start background notification polling for calendar & tasks
        startNotificationPolling()
    }

    private fun startNotificationPolling() {
        notificationPollingJob?.cancel()
        notificationPollingJob = viewModelScope.launch {
            delay(5000) // Initial delay
            while (isActive) {
                checkCalendarReminders()
                checkTaskUpdates()
                delay(30000) // Check every 30s
            }
        }
    }

    private suspend fun checkCalendarReminders() {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return
        val ctx = EchoApplication.appContext
        try {
            val api = EchoApiClient.getApi(ip)
            val result = withContext(Dispatchers.IO) { api.getCalendarEvents(includeDone = "false") }
            val events = result.events
            _calendarEvents.value = events

            // Notify for events with reminders that we haven't seen yet
            events.filter { it.reminded == 1 && it.id !in lastKnownReminderIds && it.is_done == 0 }
                .forEach { event ->
                    NotificationHelper.notifyCalendarReminder(ctx, event.id, event.title, event.event_time)
                    lastKnownReminderIds.add(event.id)
                }
        } catch (e: Exception) {
            Log.e("EchoVM", "Calendar poll failed: ${e.message}")
        }
    }

    private suspend fun checkTaskUpdates() {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return
        val ctx = EchoApplication.appContext
        try {
            val api = EchoApiClient.getApi(ip)
            val result = withContext(Dispatchers.IO) { api.getTaskQueue() }
            
            // Expose active tasks (running or pending) to the UI
            _activeTasks.value = result.tasks.filter { it.status == "running" || it.status == "pending" }

            val completedTasks = result.tasks.filter {
                (it.status == "done" || it.status == "failed") && it.id !in lastKnownTaskIds
            }
            completedTasks.forEach { task ->
                if (task.status == "done") {
                    NotificationHelper.notifyTaskCompleted(ctx, task.id, task.command, task.result ?: "Done")
                } else {
                    NotificationHelper.notifyTaskFailed(ctx, task.id, task.command, task.result ?: "Unknown error")
                }
                lastKnownTaskIds.add(task.id)
            }
        } catch (e: Exception) {
            Log.e("EchoVM", "Task poll failed: ${e.message}")
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    private suspend fun fetchStats() {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) {
            _isConnected.value = false
            return
        }
        val ctx = EchoApplication.appContext
        try {
            val api = EchoApiClient.getApi(ip)
            val newStats = api.getStats()
            _stats.value = newStats
            _isConnected.value = true

            // ── PC Online notification ──
            if (wasConnected == false) {
                NotificationHelper.notifyPcOnline(ctx, newStats.name.ifBlank { "Echo" })
            }
            wasConnected = true

            // ── Battery low (<15%) ──
            if (newStats.battery_percent in 1..14 && !newStats.battery_plugged && !batteryAlerted) {
                NotificationHelper.notifyBatteryLow(ctx, newStats.battery_percent)
                batteryAlerted = true
            } else if (newStats.battery_percent > 20 || newStats.battery_plugged) {
                batteryAlerted = false  // Reset once charged
            }

            // ── CPU high (>90%) ──
            if (newStats.cpu_percent > 90f && !cpuAlerted) {
                NotificationHelper.notifyCpuHigh(ctx, newStats.cpu_percent.toInt())
                cpuAlerted = true
            } else if (newStats.cpu_percent < 75f) {
                cpuAlerted = false
            }

            // ── RAM high (>90%) ──
            if (newStats.ram_percent > 90f && !ramAlerted) {
                NotificationHelper.notifyRamHigh(ctx, newStats.ram_percent.toInt())
                ramAlerted = true
            } else if (newStats.ram_percent < 75f) {
                ramAlerted = false
            }

            // ── Disk high (>95%) ──
            if (newStats.disk_percent > 95f && !diskAlerted) {
                NotificationHelper.notifyDiskHigh(ctx, newStats.disk_percent.toInt())
                diskAlerted = true
            } else if (newStats.disk_percent < 90f) {
                diskAlerted = false
            }

            // Network speed
            val now = System.currentTimeMillis()
            if (lastNetCheckTime > 0 && now > lastNetCheckTime) {
                val elapsed = (now - lastNetCheckTime) / 1000.0
                val bytesPerSec = ((newStats.net_bytes_recv - lastBytesRecv) + (newStats.net_bytes_sent - lastBytesSent)) / elapsed
                _netSpeedMbps.value = (bytesPerSec / (1024 * 1024)).toFloat()
            }
            lastBytesRecv = newStats.net_bytes_recv
            lastBytesSent = newStats.net_bytes_sent
            lastNetCheckTime = now

        } catch (e: Exception) {
            Log.e("EchoVM", "Stats fetch failed: ${e.javaClass.simpleName}: ${e.message}")

            // ── PC Offline notification ──
            if (wasConnected == true) {
                NotificationHelper.notifyPcOffline(ctx, _stats.value.name.ifBlank { "Echo" })
            }
            wasConnected = false

            try {
                val api = EchoApiClient.getApi(ip)
                val status = api.getStatus()
                _isConnected.value = status.status == "online"
                _currentMode.value = status.mode
                _activeModel.value = status.model
                _activeProvider.value = status.provider
                if (_isConnected.value) wasConnected = true
            } catch (e2: Exception) {
                _isConnected.value = false
            }
        }
    }

    // ─── PC Control Chat ─────────────────────────────────────

    fun sendCommand(text: String) {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank() || text.isBlank()) return

        _chatMessages.value = _chatMessages.value + ChatMessage(text, true)
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val api = EchoApiClient.getApi(ip)
                val result = api.sendCommand(CommandRequest(text))
                val taskId = result.task_id

                if (taskId != null && taskId > 0) {
                    // Task was queued — poll for completion
                    _chatMessages.value = _chatMessages.value + ChatMessage("⏳ Processing...", false)
                    val processingIdx = _chatMessages.value.lastIndex

                    // Poll every 2 seconds for up to 120 seconds
                    var attempts = 0
                    while (attempts < 60) {
                        delay(2000)
                        attempts++
                        try {
                            val tasks = withContext(Dispatchers.IO) { api.getTaskQueue() }
                            val task = tasks.tasks.find { it.id == taskId }
                            if (task != null && (task.status == "done" || task.status == "failed")) {
                                val response = task.result ?: if (task.status == "done") "Done!" else "Task failed"
                                // Replace the "Processing..." message with the actual result
                                val updated = _chatMessages.value.toMutableList()
                                if (processingIdx < updated.size) {
                                    updated[processingIdx] = ChatMessage(response, false)
                                }
                                _chatMessages.value = updated
                                break
                            }
                        } catch (e: Exception) {
                            // Polling failed, try again
                        }
                    }
                } else {
                    // Direct response (fallback)
                    val response = result.response ?: result.error ?: "No response"
                    _chatMessages.value = _chatMessages.value + ChatMessage(response, false)
                }
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    "Connection error: ${e.message}", false
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Assistant Chat ──────────────────────────────────────

    fun sendAssistantMessage(text: String) {
        if (text.isBlank()) return

        _assistantMessages.value = _assistantMessages.value + ChatMessage(text, true)
        _assistantLoading.value = true

        viewModelScope.launch {
            val ip = EchoApplication.getPcIp()
            val isOnline = _isConnected.value && ip.isNotBlank()

            if (isOnline) {
                // Try PC backend first
                try {
                    val api = EchoApiClient.getApi(ip)
                    val result = api.assistantChat(AssistantChatRequest(text))
                    val response = result.response ?: result.error ?: "No response"
                    _assistantMessages.value = _assistantMessages.value + ChatMessage(response, false)
                    _assistantLoading.value = false
                    return@launch
                } catch (e: Exception) {
                    Log.d("EchoVM", "PC assistant failed, trying local AI: ${e.message}")
                }
            }

            // Fallback: local on-device AI
            val apiKey = EchoApplication.getAiApiKey()
            if (apiKey.isNotBlank()) {
                try {
                    val response = LocalAiService.chat(apiKey, text)
                    _assistantMessages.value = _assistantMessages.value + ChatMessage(response, false)
                } catch (e: Exception) {
                    _assistantMessages.value = _assistantMessages.value + ChatMessage(
                        "Local AI error: ${e.message}", false
                    )
                }
            } else {
                _assistantMessages.value = _assistantMessages.value + ChatMessage(
                    "⚠️ PC is offline and no AI API key is set.\n\nGo to Settings → enter your NVIDIA API key to chat with AI even when your PC is off.", false
                )
            }
            _assistantLoading.value = false
        }
    }

    // ─── Mode Switching ──────────────────────────────────────

    fun switchMode(mode: String) {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return
        viewModelScope.launch {
            try {
                val api = EchoApiClient.getApi(ip)
                withContext(Dispatchers.IO) { api.setMode(ModeRequest(mode)) }
                _currentMode.value = mode
            } catch (e: Exception) {
                Log.e("EchoVM", "Mode switch failed: ${e.message}")
            }
        }
    }

    // ─── Model Switching ─────────────────────────────────────

    fun switchModel(provider: String) {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return
        viewModelScope.launch {
            try {
                val api = EchoApiClient.getApi(ip)
                withContext(Dispatchers.IO) { api.switchModel(ModelSwitchRequest(provider)) }
                _activeProvider.value = provider
            } catch (e: Exception) {
                Log.e("EchoVM", "Model switch failed: ${e.message}")
            }
        }
    }

    // ─── Calendar ────────────────────────────────────────────

    fun loadCalendarEvents(date: String? = null) {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return
        viewModelScope.launch {
            try {
                val api = EchoApiClient.getApi(ip)
                val result = withContext(Dispatchers.IO) { api.getCalendarEvents(date, "true") }
                _calendarEvents.value = result.events
            } catch (e: Exception) {
                Log.e("EchoVM", "Calendar load failed: ${e.message}")
            }
        }
    }

    fun addCalendarEvent(title: String, date: String, time: String = "", description: String = "", remindAt: String? = null) {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return
        viewModelScope.launch {
            try {
                val api = EchoApiClient.getApi(ip)
                withContext(Dispatchers.IO) {
                    api.createCalendarEvent(CalendarCreateRequest(title, date, time, description, remindAt))
                }
                loadCalendarEvents()
            } catch (e: Exception) {
                Log.e("EchoVM", "Calendar add failed: ${e.message}")
            }
        }
    }

    fun completeCalendarEvent(id: Int) {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return
        viewModelScope.launch {
            try {
                val api = EchoApiClient.getApi(ip)
                withContext(Dispatchers.IO) { api.completeCalendarEvent(id) }
                loadCalendarEvents()
            } catch (e: Exception) {
                Log.e("EchoVM", "Calendar complete failed: ${e.message}")
            }
        }
    }

    fun deleteCalendarEvent(id: Int) {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return
        viewModelScope.launch {
            try {
                val api = EchoApiClient.getApi(ip)
                withContext(Dispatchers.IO) { api.deleteCalendarEvent(id) }
                loadCalendarEvents()
            } catch (e: Exception) {
                Log.e("EchoVM", "Calendar delete failed: ${e.message}")
            }
        }
    }

    // ─── Power & Utility ─────────────────────────────────────

    fun sendPowerAction(action: String) {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return
        viewModelScope.launch {
            try {
                val api = EchoApiClient.getApi(ip)
                api.sendPowerAction(PowerRequest(action))
            } catch (e: Exception) {
                Log.e("EchoVM", "Power action failed: ${e.message}")
            }
        }
    }

    fun takeScreenshot() {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return

        _chatMessages.value = _chatMessages.value + ChatMessage("📸 Taking screenshot...", true)
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val api = EchoApiClient.getApi(ip)
                val result = api.getScreenshot()
                if (result.image != null) {
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        text = "Here's your PC screenshot:",
                        isUser = false,
                        imageBase64 = result.image
                    )
                } else {
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        "Screenshot failed: ${result.error ?: "Unknown error"}", false
                    )
                }
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    "Screenshot error: ${e.message}", false
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadFile(filename: String, base64Data: String) {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return

        _chatMessages.value = _chatMessages.value + ChatMessage(
            text = "📤 Sending: $filename",
            isUser = true,
            imageBase64 = base64Data
        )
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val api = EchoApiClient.getApi(ip)
                val result = api.uploadFile(UploadRequest(filename, base64Data))
                val response = result.result ?: result.error ?: "Upload complete"
                _chatMessages.value = _chatMessages.value + ChatMessage(response, false)
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    "Upload error: ${e.message}", false
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun quickAction(action: String) {
        sendCommand(action)
    }

    fun testConnection(): Boolean {
        val ip = EchoApplication.getPcIp()
        if (ip.isBlank()) return false

        var connected = false
        runBlocking {
            try {
                val api = EchoApiClient.getApi(ip)
                val status = api.getStatus()
                connected = status.status == "online"
            } catch (_: Exception) {}
        }
        return connected
    }
}
