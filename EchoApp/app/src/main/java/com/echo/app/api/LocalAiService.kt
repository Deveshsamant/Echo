package com.echo.app.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-device AI service that calls NVIDIA NIM API directly from the phone.
 * Used when the PC backend is offline so the assistant still works.
 */
object LocalAiService {

    private const val BASE_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
    private const val MODEL = "meta/llama-3.1-70b-instruct"
    private const val TAG = "LocalAI"

    private val conversationHistory = mutableListOf<Pair<String, String>>() // role, content

    private val SYSTEM_PROMPT = """You are Echo, a smart and friendly personal AI assistant on a mobile phone.

PERSONALITY:
- You are warm, helpful, and concise.
- You give direct, useful answers without unnecessary filler.

CAPABILITIES:
- Answer questions on any topic
- Help with writing, brainstorming, planning, coding, and problem-solving
- Have natural conversations

NOTE: You are running locally on the phone. You do NOT have PC control tools.
If the user asks to control their PC, tell them to connect to their PC first via Echo."""

    fun clearHistory() {
        conversationHistory.clear()
    }

    suspend fun chat(apiKey: String, userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            // Build messages array
            val messages = JSONArray()

            // System prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })

            // Conversation history (last 10 turns to keep it fast)
            val historySlice = if (conversationHistory.size > 20) {
                conversationHistory.takeLast(20)
            } else {
                conversationHistory.toList()
            }
            for ((role, content) in historySlice) {
                messages.put(JSONObject().apply {
                    put("role", role)
                    put("content", content)
                })
            }

            // Current user message
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            // Build request body
            val body = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("temperature", 0.5)
                put("max_tokens", 1024)
            }

            // Make HTTP request
            val conn = URL(BASE_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "No error body"
                conn.disconnect()
                Log.e(TAG, "API error $responseCode: $errorBody")
                return@withContext "Error: API returned $responseCode. Check your API key."
            }

            val responseText = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(responseText)
            val reply = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            // Save to history
            conversationHistory.add("user" to userMessage)
            conversationHistory.add("assistant" to reply)

            reply
        } catch (e: Exception) {
            Log.e(TAG, "Local AI error: ${e.message}", e)
            "Sorry, I couldn't connect to the AI service. Check your API key and internet connection."
        }
    }
}
