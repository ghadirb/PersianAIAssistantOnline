package com.persianai.assistant.config

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.persianai.assistant.utils.PreferencesManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteAIConfigManager(context: Context) {
    private val prefs = PreferencesManager(context)
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class RemoteAIConfig(val messages: Messages? = null)
    data class Messages(val welcome: String? = null, val global_announcement: String? = null, val offline_message: String? = null)

    fun getEffectiveConfigUrl(): String = prefs.getRemoteAIConfigUrl()?.takeIf { it.isNotBlank() } ?: DEFAULT_CONFIG_URL

    suspend fun refreshAndCache(): RemoteAIConfig? = withContext(Dispatchers.IO) {
        val url = getEffectiveConfigUrl()
        try {
            val req = Request.Builder().url(url).addHeader("Accept","application/json").get().build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful || body.isNullOrBlank()) {
                    Log.w(TAG, "remote config http ${resp.code}")
                    return@withContext null
                }
                prefs.saveRemoteAIConfigJson(body)
                gson.fromJson(body, RemoteAIConfig::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "remote config fetch failed: ${e.message}")
            null
        }
    }

    fun loadCached(): RemoteAIConfig? {
        val json = prefs.getRemoteAIConfigJson() ?: return null
        return try { gson.fromJson(json, RemoteAIConfig::class.java) } catch (_: Exception) { null }
    }

    companion object {
        private const val TAG = "RemoteAIConfig"
        private const val DEFAULT_CONFIG_URL = "https://abrehamrahi.ir/o/public/eWygRXtp/"
    }
}
