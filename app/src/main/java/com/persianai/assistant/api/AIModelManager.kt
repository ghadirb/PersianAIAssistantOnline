package com.persianai.assistant.api

import android.content.Context
import android.util.Log
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * مدیریت API های مختلف هوش مصنوعی
 */
class AIModelManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AIModelManager"
        
        // API Endpoints
        const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        const val OPENAI_WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions"
        const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
        const val AIML_API_URL = "https://api.aimlapi.com/v1/chat/completions"
        const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
        private const val LIARA_BASE_URL = "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1"
        const val LIARA_API_URL = "$LIARA_BASE_URL/chat/completions"
        const val LIARA_WHISPER_URL = "$LIARA_BASE_URL/audio/transcriptions"
        
        // GAPGPT base (OpenAI-compatible)
        const val GAPGPT_BASE_URL = "https://api.gapgpt.app/v1"
        const val GAPGPT_WHISPER_URL = "$GAPGPT_BASE_URL/audio/transcriptions"
        
        // Model Names
        const val MODEL_GPT_35_TURBO = "gpt-3.5-turbo"
        const val MODEL_GPT_4 = "gpt-4"
        const val MODEL_CLAUDE_3_OPUS = "claude-3-opus-20240229"
        const val MODEL_CLAUDE_3_SONNET = "claude-3-sonnet-20240229"
        const val MODEL_LLAMA_3_3_70B = "meta-llama/llama-3.3-70b-instruct"
        const val MODEL_DEEPSEEK_R1T2 = "deepseek/deepseek-r1-t2-chimera"
        const val MODEL_LLAMA_2_70B = "meta-llama/llama-2-70b-chat"
        const val MODEL_MIXTRAL_8X7B = "mistralai/mixtral-8x7b-instruct"
    }
    
    data class ModelConfig(
        val name: String,
        val displayName: String,
        val provider: String,
        val apiKey: String?,
        val endpoint: String,
        val requiresApiKey: Boolean = true,
        val isAvailable: Boolean = false,
        val features: List<String> = listOf()
    )
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val prefs = context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)
    private val prefsManager by lazy { PreferencesManager(context) }
    
    /**
     * دریافت لیست مدل‌های در دسترس (فقط OpenAI/GPT-4o-mini برای چت آنلاین)
     */
    fun getAvailableModels(): List<ModelConfig> {
        val openAiKey = prefs.getString("openai_api_key", null)
        val avalaiKey = prefs.getString("avalai_api_key", null)
        if (openAiKey.isNullOrEmpty()) return emptyList()

        val models = mutableListOf(
            ModelConfig(
                name = "gpt-4o-mini",
                displayName = "GPT-4o Mini (OpenAI)",
                provider = "OpenAI",
                apiKey = openAiKey,
                endpoint = OPENAI_API_URL,
                isAvailable = true,
                features = listOf("چت اصلی", "OpenAI")
            )
        )

        if (!avalaiKey.isNullOrEmpty()) {
            models.add(
                ModelConfig(
                    name = "gemini-2.5-flash",
                    displayName = "Gemini 2.5 Flash (Avalai)",
                    provider = "Avalai",
                    apiKey = avalaiKey,
                    endpoint = "https://avalai.ir/api/v1",
                    isAvailable = true,
                    features = listOf("چت اصلی", "Avalai")
                )
            )
        }
        return models
    }
    
    /**
     * ذخیره کلید API
     */
    fun saveApiKey(provider: String, apiKey: String) {
        val key = when (provider.toLowerCase()) {
            "openai" -> "openai_api_key"
            "claude", "anthropic" -> "claude_api_key"
            "openrouter" -> "openrouter_api_key"
            "aiml" -> "aiml_api_key"
            "liara" -> "liara_api_key"
            "avalai" -> "avalai_api_key"
            else -> return
        }
        
        prefs.edit().putString(key, apiKey).apply()
        Log.d(TAG, "API key saved for $provider")
        
        // همگام‌سازی با PreferencesManager برای AIClient
        val prefsManager = PreferencesManager(context)
        val apiProvider = when (provider.toLowerCase()) {
            "openai" -> AIProvider.OPENAI
            "claude", "anthropic" -> AIProvider.ANTHROPIC
            "openrouter" -> AIProvider.OPENROUTER
            "liara" -> AIProvider.LIARA
            "avalai" -> AIProvider.AVALAI
            else -> null
        }
        if (apiProvider != null) {
            val updatedKeys = prefsManager.getAPIKeys()
                .filter { it.provider != apiProvider }
                .toMutableList()
            val baseUrl = when (apiProvider) {
                AIProvider.LIARA -> LIARA_BASE_URL
                AIProvider.AVALAI -> "https://avalai.ir/api/v1"
                else -> null
            }
            updatedKeys.add(APIKey(apiProvider, apiKey, baseUrl = baseUrl, isActive = true))
            prefsManager.saveAPIKeys(updatedKeys)
        }
    }
    
    /**
     * ارسال پیام به مدل
     */
    suspend fun sendMessage(
        model: ModelConfig,
        message: String,
        onResponse: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            if (model.apiKey.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    onError("❌ کلید API تنظیم نشده است")
                }
                return@withContext
            }
            val requestBody = when {
                model.provider == "Anthropic" -> {
                    // Claude API format
                    JSONObject().apply {
                        put("model", model.name)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", message)
                            })
                        })
                        put("max_tokens", 4096)
                    }
                }
                model.provider == "OpenRouter" -> {
                    // OpenRouter format (با پیام system برای جلوگیری از خطاهای 400)
                    JSONObject().apply {
                        put("model", model.name)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "system")
                                put("content", "You are a helpful Persian AI assistant. Answer in Persian when user speaks Persian.")
                            })
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", message)
                            })
                        })
                        put("max_tokens", 2048)
                        put("temperature", 0.7)
                    }
                }
                else -> {
                    // OpenAI/AIML format
                    JSONObject().apply {
                        put("model", model.name.takeIf { it.isNotEmpty() } ?: MODEL_GPT_35_TURBO)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "system")
                                put("content", "You are a helpful Persian AI assistant. Answer in Persian when asked in Persian.")
                            })
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", message)
                            })
                        })
                        put("max_tokens", 2048)
                        put("temperature", 0.7)
                    }
                }
            }
            
            val request = Request.Builder()
                .url(model.endpoint)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .apply {
                    when (model.provider) {
                        "Anthropic" -> {
                            addHeader("x-api-key", model.apiKey ?: "")
                            addHeader("anthropic-version", "2023-06-01")
                        }
                        "OpenRouter" -> {
                            addHeader("Authorization", "Bearer ${model.apiKey}")
                            addHeader("HTTP-Referer", "com.persianai.assistant")
                        }
                        else -> {
                            addHeader("Authorization", "Bearer ${model.apiKey}")
                        }
                    }
                }
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                    val json = JSONObject(responseBody)
                    
                    val content = when {
                        model.provider == "Anthropic" -> {
                            json.getJSONArray("content")
                                .getJSONObject(0)
                                .getString("text")
                        }
                        json.has("choices") -> {
                            json.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                        }
                        else -> "خطا در پردازش پاسخ"
                    }
                    
                    withContext(Dispatchers.Main) {
                        onResponse(content)
                    }
                } else {
                    val errorMessage = when (response.code) {
                        401 -> "❌ کلید API نامعتبر است"
                        429 -> "⚠️ محدودیت نرخ درخواست - لطفاً کمی صبر کنید"
                        500 -> "❌ خطای سرور - لطفاً دوباره تلاش کنید"
                        else -> "خطا: ${response.code} - ${response.message}"
                    }
                    
                    withContext(Dispatchers.Main) {
                        onError(errorMessage)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            withContext(Dispatchers.Main) {
                onError("خطا در ارتباط: ${e.message}")
            }
        }
    }
    
    /**
     * تبدیل صدا به متن با Whisper (اولویت: GAPGPT → Liara → OpenAI)
     */
    suspend fun transcribeAudio(
        audioFile: java.io.File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val keys = prefsManager.getAPIKeys().filter { it.isActive && it.key.isNotBlank() }
        val gapgptKeys = keys.filter { it.provider == AIProvider.GAPGPT }
        val openAiKeys = keys.filter { it.provider == AIProvider.OPENAI }
        val liaraKeys = keys.filter { it.provider == AIProvider.LIARA }

        if (gapgptKeys.isEmpty() && liaraKeys.isEmpty() && openAiKeys.isEmpty()) {
            withContext(Dispatchers.Main) {
                onError("❌ برای استفاده از تبدیل صدا به متن، کلید API مورد نیاز است")
            }
            return@withContext
        }

        try {
            val mediaTypeStr = when (audioFile.extension.lowercase()) {
                "m4a", "mp4" -> "audio/mp4"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                "webm" -> "audio/webm"
                "mp3" -> "audio/mpeg"
                else -> "application/octet-stream"
            }

            // بدنه مشترک Multipart برای Whisper-compatible
            fun buildRequestBody(): MultipartBody =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        RequestBody.create(mediaTypeStr.toMediaType(), audioFile)
                    )
                    .addFormDataPart("model", "whisper-1")
                    .addFormDataPart("language", "fa")
                    .build()

            fun buildWhisperUrl(k: APIKey): String {
                val base = (k.baseUrl?.trim()?.trimEnd('/') ?: when (k.provider) {
                    AIProvider.GAPGPT -> GAPGPT_BASE_URL
                    AIProvider.LIARA -> LIARA_BASE_URL
                    else -> "https://api.openai.com/v1"
                })
                return "$base/audio/transcriptions"
            }

            val orderedKeys = gapgptKeys + openAiKeys + liaraKeys
            for (k in orderedKeys) {
                val url = buildWhisperUrl(k)
                try {
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer ${k.key}")
                        .post(buildRequestBody())
                        .build()

                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                            val json = JSONObject(responseBody)
                            val primaryText = json.optString("text")
                            val text = primaryText.takeIf { it.isNotBlank() } ?: json.optString("generated_text")
                            if (text.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    Log.d(TAG, "✅ STT via ${k.provider} whisper")
                                    onSuccess(text)
                                }
                                return@withContext
                            }
                        } else {
                            Log.w(TAG, "${k.provider} STT failed: url=$url code=${response.code} msg=${response.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "${k.provider} STT exception: ${e.message}", e)
                }
            }

            // اگر به اینجا رسید، همه تلاش‌های آنلاین شکست خورده‌اند
            withContext(Dispatchers.Main) {
                onError("⚠️ تبدیل صدا به متن آنلاین (GAPGPT/Liara/OpenAI) ناموفق بود")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing audio", e)
            withContext(Dispatchers.Main) {
                onError("خطا در پردازش فایل صوتی: ${e.message}")
            }
        }
    }
    
    /**
     * بررسی وجود کلید API
     */
    fun hasApiKey(): Boolean {
        val liaraKey = prefs.getString("liara_api_key", null)
        val openAIKey = prefs.getString("openai_api_key", null)
        val claudeKey = prefs.getString("claude_api_key", null)
        val openRouterKey = prefs.getString("openrouter_api_key", null)
        val aimlKey = prefs.getString("aiml_api_key", null)
        
        return !liaraKey.isNullOrEmpty() || !openAIKey.isNullOrEmpty() || !claudeKey.isNullOrEmpty() || 
               !openRouterKey.isNullOrEmpty() || !aimlKey.isNullOrEmpty()
    }
    
    /**
     * تولید متن با استفاده از AI (متد ساده)
     */
    suspend fun generateText(prompt: String): String = withContext(Dispatchers.IO) {
        val models = getAvailableModels()
        if (models.isEmpty()) {
            return@withContext "خطا: کلید API تنظیم نشده است"
        }

        var lastError: String? = null
        for (model in models) {
            try {
                val res = sendMessageBlocking(model, prompt)
                if (res.isSuccess) {
                    return@withContext res.getOrThrow()
                } else {
                    lastError = res.exceptionOrNull()?.message
                }
            } catch (e: Exception) {
                lastError = e.message
                continue
            }
        }
        val friendly = when {
            lastError?.contains("unable to resolve host", ignoreCase = true) == true -> "خطا: اتصال اینترنت برقرار نیست"
            lastError?.contains("Failed to connect", ignoreCase = true) == true -> "خطا: اتصال شبکه برقرار نیست"
            lastError?.contains("API key", ignoreCase = true) == true -> "خطا: کلید API نامعتبر یا تنظیم نشده است"
            else -> lastError ?: "همه مدل‌ها ناموفق بودند"
        }
        return@withContext ("خطا: $friendly")
    }

    private suspend fun sendMessageBlocking(model: ModelConfig, message: String): Result<String> {
        val deferred = CompletableDeferred<Result<String>>()
        sendMessage(
            model = model,
            message = message,
            onResponse = { deferred.complete(Result.success(it)) },
            onError = { deferred.complete(Result.failure(Exception(it))) }
        )
        return deferred.await()
    }
    
    /**
     * بررسی اعتبار کلید API
     */
    suspend fun validateApiKey(
        provider: String,
        apiKey: String,
        onResult: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val testMessage = "Hi"
            val endpoint = when (provider.toLowerCase()) {
                "openai" -> OPENAI_API_URL
                "openrouter" -> OPENROUTER_API_URL
                "aiml" -> AIML_API_URL
                "claude", "anthropic" -> CLAUDE_API_URL
                else -> {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Provider نامعتبر")
                    }
                    return@withContext
                }
            }
            
            // ساخت درخواست تست
            val requestBody = JSONObject().apply {
                when (provider.toLowerCase()) {
                    "claude", "anthropic" -> {
                        put("model", MODEL_CLAUDE_3_SONNET)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", testMessage)
                            })
                        })
                        put("max_tokens", 10)
                    }
                    else -> {
                        put("model", MODEL_GPT_35_TURBO)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", testMessage)
                            })
                        })
                        put("max_tokens", 10)
                    }
                }
            }
            
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Content-Type", "application/json")
                .apply {
                    when (provider.toLowerCase()) {
                        "claude", "anthropic" -> {
                            addHeader("x-api-key", apiKey)
                            addHeader("anthropic-version", "2023-06-01")
                        }
                        else -> {
                            addHeader("Authorization", "Bearer $apiKey")
                        }
                    }
                }
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            client.newCall(request).execute().use { response ->
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        onResult(true, "✅ کلید API معتبر است")
                    } else {
                        val message = when (response.code) {
                            401 -> "❌ کلید API نامعتبر است"
                            429 -> "⚠️ محدودیت نرخ - کلید معتبر است"
                            else -> "خطا: ${response.code}"
                        }
                        onResult(response.code == 429, message)
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(false, "خطا در اتصال: ${e.message}")
            }
        }
    }
}
