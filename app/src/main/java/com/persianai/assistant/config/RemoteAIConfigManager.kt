package com.persianai.assistant.config

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.models.AIModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteAIConfigManager private constructor(private val context: Context) {
    private val prefs = PreferencesManager(context)
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "RemoteAIConfig"
        private const val DEFAULT_CONFIG_URL = "https://abrehamrahi.ir/o/public/eWygRXtp/"
        @Volatile private var INSTANCE: RemoteAIConfigManager? = null
        
        fun getInstance(context: Context?): RemoteAIConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteAIConfigManager(context!!.applicationContext).also { INSTANCE = it }
            }
        }

        // Fallback hardcoded priorities (existing behavior)
        private val FALLBACK_MODEL_PRIORITY = listOf(
            AIModel.IVIRA_GPT5_NANO,
            AIModel.GPT_4O_MINI,
            AIModel.LIARA_GPT_4O_MINI,
            AIModel.IVIRA_GPT5_MINI,
            AIModel.GAPGPT_DEEPSEEK_V3,
            AIModel.QWEN_2_5_1B5,
            AIModel.LLAMA_3_2_1B,
            AIModel.LLAMA_3_2_3B,
            AIModel.MIXTRAL_8X7B,
            AIModel.LLAMA_3_3_70B,
            AIModel.DEEPSEEK_R1T2,
            AIModel.LLAMA_2_70B,
            AIModel.GPT_4O,
            AIModel.CLAUDE_HAIKU,
            AIModel.CLAUDE_SONNET,
            AIModel.AIML_GPT_35
        )
        private val FALLBACK_STT_PRIORITY = listOf("gapgpt", "liara", "openai")
        private val FALLBACK_TTS_PRIORITY = listOf("haaniye", "android")
    }

    data class RemoteAIConfig(
        val messages: Messages? = null,
        val models: List<ModelConfig>? = null,
        val stt_priority: List<String>? = null,
        val tts_priority: List<String>? = null
    )
    data class Messages(val welcome: String? = null, val global_announcement: String? = null, val offline_message: String? = null)
    data class ModelConfig(val model_id: String, val provider: String, val enabled: Boolean = true)

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

    // Get effective model priority list from remote config or fallback to hardcoded
    fun getModelPriority(): List<AIModel> {
        val config = loadCached()
        val remoteIds = config?.models?.filter { it.enabled }?.map { it.model_id.lowercase() } ?: emptyList()
        if (remoteIds.isEmpty()) return FALLBACK_MODEL_PRIORITY
        // Map remote model_id to AIModel enum, fallback to default list if not found
        return remoteIds.mapNotNull { id ->
            AIModel.values().find { it.modelId.equals(id, ignoreCase = true) }
        }.ifEmpty { FALLBACK_MODEL_PRIORITY }
    }

    fun getSTTPriority(): List<String> {
        val config = loadCached()
        return config?.stt_priority?.takeIf { it.isNotEmpty() } ?: FALLBACK_STT_PRIORITY
    }

    fun getTTSPriority(): List<String> {
        val config = loadCached()
        return config?.tts_priority?.takeIf { it.isNotEmpty() } ?: FALLBACK_TTS_PRIORITY
    }
}
