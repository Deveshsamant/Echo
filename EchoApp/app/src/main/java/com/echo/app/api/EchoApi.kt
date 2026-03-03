package com.echo.app.api

import com.echo.app.model.*
import retrofit2.http.*

interface EchoApi {
    // ─── Existing ────────────────────────────────────────────
    @GET("/api/stats")
    suspend fun getStats(): SystemStats

    @GET("/api/status")
    suspend fun getStatus(): StatusResponse

    @POST("/api/command")
    suspend fun sendCommand(@Body request: CommandRequest): CommandResponse

    @POST("/api/power")
    suspend fun sendPowerAction(@Body request: PowerRequest): PowerResponse

    @GET("/api/screenshot")
    suspend fun getScreenshot(): ScreenshotResponse

    @POST("/api/upload")
    suspend fun uploadFile(@Body request: UploadRequest): UploadResponse

    @GET("/api/processes")
    suspend fun getProcesses(@Query("sort") sort: String = "ram"): ProcessListResponse

    @POST("/api/kill")
    suspend fun killProcess(@Body request: KillRequest): GenericResponse

    @GET("/api/volume")
    suspend fun getVolume(): VolumeResponse

    @POST("/api/volume")
    suspend fun setVolume(@Body request: VolumeRequest): GenericResponse

    @POST("/api/launch")
    suspend fun launchApp(@Body request: LaunchRequest): GenericResponse

    @GET("/api/clipboard")
    suspend fun getClipboard(): ClipboardResponse

    @POST("/api/clipboard")
    suspend fun setClipboard(@Body request: ClipboardRequest): GenericResponse

    @POST("/api/notify")
    suspend fun sendNotification(@Body request: NotifyRequest): GenericResponse

    @POST("/api/media")
    suspend fun mediaControl(@Body request: MediaRequest): GenericResponse

    // ─── Mode & Models ───────────────────────────────────────
    @GET("/api/mode")
    suspend fun getMode(): ModeResponse

    @POST("/api/mode")
    suspend fun setMode(@Body request: ModeRequest): GenericResponse

    @GET("/api/models")
    suspend fun getModels(): ModelsResponse

    @POST("/api/models")
    suspend fun switchModel(@Body request: ModelSwitchRequest): GenericResponse

    // ─── Assistant ───────────────────────────────────────────
    @POST("/api/assistant/chat")
    suspend fun assistantChat(@Body request: AssistantChatRequest): CommandResponse

    // ─── Calendar ────────────────────────────────────────────
    @GET("/api/calendar")
    suspend fun getCalendarEvents(
        @Query("date") date: String? = null,
        @Query("include_done") includeDone: String = "false"
    ): CalendarEventsResponse

    @POST("/api/calendar")
    suspend fun createCalendarEvent(@Body request: CalendarCreateRequest): CalendarCreateResponse

    @POST("/api/calendar/{id}/done")
    suspend fun completeCalendarEvent(@Path("id") id: Int): GenericResponse

    @DELETE("/api/calendar/{id}")
    suspend fun deleteCalendarEvent(@Path("id") id: Int): GenericResponse

    // ─── Task Queue ──────────────────────────────────────────
    @GET("/api/tasks/queue")
    suspend fun getTaskQueue(): TaskQueueResponse

    @POST("/api/tasks/queue")
    suspend fun queueTask(@Body request: TaskQueueRequest): GenericResponse

    // ─── Conversations ───────────────────────────────────────
    @GET("/api/conversations")
    suspend fun getConversations(@Query("limit") limit: Int = 30): ConversationsResponse
}
