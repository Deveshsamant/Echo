package com.echo.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.echo.app.util.NotificationHelper

class EchoApplication : Application() {

    companion object {
        private const val PREFS_NAME = "echo_prefs"
        const val KEY_PC_IP = "pc_ip"
        const val KEY_BOT_TOKEN = "bot_token"
        const val KEY_AI_API_KEY = "ai_api_key"

        lateinit var prefs: SharedPreferences
            private set

        lateinit var appContext: Context
            private set

        fun getPcIp(): String = prefs.getString(KEY_PC_IP, "") ?: ""
        fun getBotToken(): String = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        fun getAiApiKey(): String = prefs.getString(KEY_AI_API_KEY, "") ?: ""
        fun savePcIp(ip: String) = prefs.edit().putString(KEY_PC_IP, ip).apply()
        fun saveBotToken(token: String) = prefs.edit().putString(KEY_BOT_TOKEN, token).apply()
        fun saveAiApiKey(key: String) = prefs.edit().putString(KEY_AI_API_KEY, key).apply()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appContext = applicationContext
        NotificationHelper.createChannels(this)
    }
}
