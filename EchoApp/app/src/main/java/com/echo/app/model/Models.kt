package com.echo.app.model

data class SystemStats(
    val status: String = "offline",
    val name: String = "Echo",
    val os: String = "",
    val cpu_percent: Float = 0f,
    val cpu_count: Int = 0,
    val ram_used_gb: Float = 0f,
    val ram_total_gb: Float = 0f,
    val ram_percent: Float = 0f,
    val disk_used_gb: Float = 0f,
    val disk_total_gb: Float = 0f,
    val disk_percent: Float = 0f,
    val battery_percent: Int = -1,
    val battery_plugged: Boolean = false,
    val battery_secs_left: Int = -1,
    val active_window: String = "Unknown",
    val active_window_pid: Int = 0,
    val net_bytes_sent: Long = 0,
    val net_bytes_recv: Long = 0,
    val gpu_percent: Int = -1,
    val gpu_mem_used_mb: Int = 0,
    val gpu_mem_total_mb: Int = 0
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBase64: String? = null
)

data class CommandRequest(val command: String)
data class CommandResponse(val response: String? = null, val error: String? = null, val task_id: Int? = null)

data class PowerRequest(val action: String)
data class PowerResponse(val result: String? = null, val error: String? = null)

data class ScreenshotResponse(val image: String? = null, val error: String? = null)

data class UploadRequest(val filename: String, val data: String, val type: String = "image")
data class UploadResponse(val result: String? = null, val error: String? = null)

// ─── Control Models ──────────────────────────────────────────

data class GenericResponse(val result: String? = null, val error: String? = null, val message: String? = null)

data class ProcessInfo(val pid: Int, val name: String, val cpu: Float, val ram_mb: Float, val disk_mb: Float = 0f)
data class ProcessListResponse(val processes: List<ProcessInfo> = emptyList())

data class KillRequest(val pid: Int)

data class VolumeResponse(val volume: Int = 0)
data class VolumeRequest(val level: Int)

data class LaunchRequest(val name: String)

data class ClipboardResponse(val text: String = "")
data class ClipboardRequest(val text: String)

data class NotifyRequest(val title: String, val message: String)

data class MediaRequest(val action: String)

// ─── Mode & Model ────────────────────────────────────────────

data class ModeResponse(val mode: String = "pc_control", val available: List<String> = emptyList())
data class ModeRequest(val mode: String)

data class ModelProvider(val provider: String, val model: String, val base_url: String = "")
data class ModelsResponse(
    val active_provider: String = "",
    val active_model: String = "",
    val available: List<ModelProvider> = emptyList()
)
data class ModelSwitchRequest(val provider: String)

data class StatusResponse(
    val status: String = "offline",
    val name: String = "Echo",
    val mode: String = "pc_control",
    val model: String = "",
    val provider: String = ""
)

// ─── Assistant Mode ──────────────────────────────────────────

data class AssistantChatRequest(val message: String)

// ─── Calendar ────────────────────────────────────────────────

data class CalendarEvent(
    val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val event_date: String = "",
    val event_time: String = "",
    val remind_at: String? = null,
    val is_done: Int = 0,
    val reminded: Int = 0,
    val created_at: String = ""
)
data class CalendarEventsResponse(val events: List<CalendarEvent> = emptyList())
data class CalendarCreateRequest(
    val title: String,
    val date: String,
    val time: String = "",
    val description: String = "",
    val remind_at: String? = null
)
data class CalendarCreateResponse(val event_id: Int = 0, val message: String? = null, val error: String? = null)

// ─── Task Queue ──────────────────────────────────────────────

data class QueuedTask(
    val id: Int = 0,
    val command: String = "",
    val source: String = "",
    val status: String = "pending",
    val result: String? = null,
    val created_at: String = "",
    val executed_at: String? = null
)
data class TaskQueueResponse(val tasks: List<QueuedTask> = emptyList())
data class TaskQueueRequest(val command: String, val source: String = "app")

// ─── Conversations ───────────────────────────────────────────

data class ConversationSummary(
    val id: String = "",
    val title: String = "",
    val created_at: String = "",
    val updated_at: String = ""
)
data class ConversationsResponse(val conversations: List<ConversationSummary> = emptyList())
