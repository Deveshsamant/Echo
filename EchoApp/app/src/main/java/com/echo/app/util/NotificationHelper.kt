package com.echo.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.echo.app.MainActivity
import com.echo.app.R

object NotificationHelper {

    // Channel IDs
    const val CHANNEL_CONNECTION = "echo_connection"
    const val CHANNEL_CALENDAR = "echo_calendar"
    const val CHANNEL_TASKS = "echo_tasks"
    const val CHANNEL_SYSTEM = "echo_system"

    // Notification IDs
    const val ID_PC_ONLINE = 1001
    const val ID_PC_OFFLINE = 1002
    const val ID_CALENDAR = 1100       // + event id offset
    const val ID_TASK_DONE = 1200      // + task id offset
    const val ID_BATTERY_LOW = 1301
    const val ID_CPU_HIGH = 1302
    const val ID_RAM_HIGH = 1303
    const val ID_DISK_HIGH = 1304

    fun createChannels(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_CONNECTION, "Connection Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "PC online/offline alerts"
            },
            NotificationChannel(
                CHANNEL_CALENDAR, "Calendar Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Calendar event reminders from your PC"
            },
            NotificationChannel(
                CHANNEL_TASKS, "Task Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Queued task completion notifications"
            },
            NotificationChannel(
                CHANNEL_SYSTEM, "System Alerts",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Battery low, high CPU/RAM usage warnings"
            }
        )
        channels.forEach { mgr.createNotificationChannel(it) }
    }

    private fun canNotify(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun launchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ─── PC Online ───────────────────────────────────────────

    fun notifyPcOnline(context: Context, pcName: String = "Echo") {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🟢 $pcName is Online")
            .setContentText("Your PC is now connected and ready for commands")
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_PC_ONLINE, n)
        // Clear any offline notification
        NotificationManagerCompat.from(context).cancel(ID_PC_OFFLINE)
    }

    fun notifyPcOffline(context: Context, pcName: String = "Echo") {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔴 $pcName went Offline")
            .setContentText("Lost connection to your PC")
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_PC_OFFLINE, n)
        NotificationManagerCompat.from(context).cancel(ID_PC_ONLINE)
    }

    // ─── Calendar Reminder ───────────────────────────────────

    fun notifyCalendarReminder(context: Context, eventId: Int, title: String, time: String = "") {
        if (!canNotify(context)) return
        val timeText = if (time.isNotBlank()) " at $time" else ""
        val n = NotificationCompat.Builder(context, CHANNEL_CALENDAR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📅 $title")
            .setContentText("Reminder$timeText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_CALENDAR + eventId, n)
    }

    // ─── Task Completed ──────────────────────────────────────

    fun notifyTaskCompleted(context: Context, taskId: Int, command: String, result: String) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("✅ Task Completed")
            .setContentText(command.take(60))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$command\n\nResult: ${result.take(200)}"))
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_TASK_DONE + taskId, n)
    }

    fun notifyTaskFailed(context: Context, taskId: Int, command: String, error: String) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("❌ Task Failed")
            .setContentText(command.take(60))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$command\n\nError: ${error.take(200)}"))
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_TASK_DONE + taskId, n)
    }

    // ─── System Alerts ───────────────────────────────────────

    fun notifyBatteryLow(context: Context, percent: Int) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔋 PC Battery Low — $percent%")
            .setContentText("Consider plugging in your PC")
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_BATTERY_LOW, n)
    }

    fun notifyCpuHigh(context: Context, percent: Int) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔥 PC CPU High — $percent%")
            .setContentText("Your PC's CPU usage is critically high")
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_CPU_HIGH, n)
    }

    fun notifyRamHigh(context: Context, percent: Int) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠️ PC RAM High — $percent%")
            .setContentText("Your PC is running low on memory")
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_RAM_HIGH, n)
    }

    fun notifyDiskHigh(context: Context, percent: Int) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("💾 PC Disk Almost Full — $percent%")
            .setContentText("Your PC's storage is running low")
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_DISK_HIGH, n)
    }
}
