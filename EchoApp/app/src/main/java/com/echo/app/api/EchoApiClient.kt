package com.echo.app.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object EchoApiClient {

    private var retrofit: Retrofit? = null
    private var api: EchoApi? = null
    private var currentBaseUrl: String = ""

    private val gson = GsonBuilder()
        .setLenient()
        .serializeSpecialFloatingPointValues()
        .create()

    /**
     * Build base URL from user input.
     * Supports:
     *   - "192.168.1.5"         → http://192.168.1.5:5000
     *   - "192.168.1.5:8080"    → http://192.168.1.5:8080
     *   - "https://abc.ngrok-free.app" → https://abc.ngrok-free.app
     *   - "abc.ngrok-free.app"  → https://abc.ngrok-free.app
     */
    private fun buildBaseUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            // Already a full URL
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                trimmed.trimEnd('/')
            }
            // Looks like a domain (contains dots but not just an IP)
            trimmed.contains(".") && !trimmed.matches(Regex("""\d+\.\d+\.\d+\.\d+(:\d+)?""")) -> {
                "https://${trimmed.trimEnd('/')}"
            }
            // IP with custom port
            trimmed.contains(":") -> {
                "http://$trimmed"
            }
            // Plain IP — add default port
            else -> {
                "http://$trimmed:5000"
            }
        }
    }

    fun getApi(pcIpOrUrl: String): EchoApi {
        val baseUrl = buildBaseUrl(pcIpOrUrl)
        if (baseUrl != currentBaseUrl || api == null) {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "EchoApp/1.0")
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl("$baseUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()

            api = retrofit?.create(EchoApi::class.java)
            currentBaseUrl = baseUrl
        }
        return api!!
    }
}
