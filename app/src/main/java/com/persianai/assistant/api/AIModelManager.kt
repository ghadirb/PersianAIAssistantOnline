package com.persianai.assistant.api

import android.content.Context
import android.util.Log
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.utils.PreferencesManager
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
    
    /**
     * دریافت لیست مدل‌های در دسترس
     */
    fun getAvailableModels(): List<ModelConfig> {
        val models = mutableListOf<ModelConfig>()
        
        // AIML API (اولویت اول برای سبک/رایگان)
        val aimlKey = prefs.getString("aiml_api_key", null)
        if (!aimlKey.isNullOrEmpty()) {
            models.add(
                ModelConfig(
                    name = "aiml-gpt-3.5",
                    displayName = "AIML GPT-3.5 (سبک)",
                    provider = "AIML",
                    apiKey = aimlKey,
                    endpoint = AIML_API_URL,
                    isAvailable = true,
                    features = listOf("اولویت ۱", "رایگان/سبک", "مناسب گوشی‌های ضعیف‌تر")
                )
            )
        }
        
        // OpenAI Models (اولویت دوم)
        val openAIKey = prefs.getString("openai_api_key", null)
        if (!openAIKey.isNullOrEmpty()) {
            models.add(
                ModelConfig(
                    name = MODEL_GPT_35_TURBO,
                    displayName = "GPT-3.5 Turbo",
                    provider = "OpenAI",
                    apiKey = openAIKey,
                    endpoint = OPENAI_API_URL,
                    isAvailable = true,
                    features = listOf("چت عمومی", "ترجمه", "کدنویسی", "سریع و کم‌هزینه")
                )
            )
            
            models.add(
                ModelConfig(
                    name = MODEL_GPT_4,
                    displayName = "GPT-4",
                    provider = "OpenAI",
                    apiKey = openAIKey,
                    endpoint = OPENAI_API_URL,
                    isAvailable = true,
                    features = listOf("پیشرفته‌ترین مدل", "استدلال پیچیده", "خلاقیت بالا", "دقت بسیار بالا")
                )
            )
        }
        
        // OpenRouter Models (اولویت سوم) - سبک‌ها در ابتدای لیست
        val openRouterKey = prefs.getString("openrouter_api_key", null)
        if (!openRouterKey.isNullOrEmpty()) {
            // سبک و کم‌هزینه برای گوشی‌های ضعیف‌تر
            models.add(
                ModelConfig(
                    name = "qwen/qwen2.5-1.5b-instruct",
                    displayName = "Qwen2.5 1.5B (Free/Light)",
                    provider = "OpenRouter",
                    apiKey = openRouterKey,
                    endpoint = OPENROUTER_API_URL,
                    isAvailable = true,
                    features = listOf("سبک", "فارسی خوب", "مناسب گوشی‌های ضعیف‌تر")
                )
            )
            models.add(
                ModelConfig(
                    name = "meta-llama/Llama-3.2-1B-Instruct",
                    displayName = "LLaMA 3.2 1B (Light)",
                    provider = "OpenRouter",
                    apiKey = openRouterKey,
                    endpoint = OPENROUTER_API_URL,
                    isAvailable = true,
                    features = listOf("خیلی سبک", "صرفه‌جو", "پشتیبان سریع")
                )
            )
            models.add(
                ModelConfig(
                    name = "meta-llama/Llama-3.2-3B-Instruct",
                    displayName = "LLaMA 3.2 3B (Light)",
                    provider = "OpenRouter",
                    apiKey = openRouterKey,
                    endpoint = OPENROUTER_API_URL,
                    isAvailable = true,
                    features = listOf("سبک", "چندزبانه", "پشتیبان متوسط")
                )
            )

            // مدل‌های قوی‌تر ولی بعد از سبک‌ها
            models.add(
                ModelConfig(
                    name = MODEL_LLAMA_3_3_70B,
                    displayName = "Llama 3.3 70B Instruct (Free)",
                    provider = "OpenRouter",
                    apiKey = openRouterKey,
                    endpoint = OPENROUTER_API_URL,
                    isAvailable = true,
                    features = listOf("رایگان/مقرون‌به‌صرفه", "قدرتمند", "چندزبانه", "مناسب چت عمومی")
                )
            )
            
            models.add(
                ModelConfig(
                    name = MODEL_DEEPSEEK_R1T2,
                    displayName = "DeepSeek R1T2 Chimera (Free)",
                    provider = "OpenRouter",
                    apiKey = openRouterKey,
                    endpoint = OPENROUTER_API_URL,
                    isAvailable = true,
                    features = listOf("استدلال قوی", "مقرون‌به‌صرفه", "کاهش محدودیت رایگان")
                )
            )

            models.add(
                ModelConfig(
                    name = MODEL_LLAMA_2_70B,
                    displayName = "Llama 2 70B",
                    provider = "OpenRouter",
                    apiKey = openRouterKey,
                    endpoint = OPENROUTER_API_URL,
                    isAvailable = true,
                    features = listOf("متن‌باز", "چندزبانه", "پشتیبان")
                )
            )
            
            models.add(
                ModelConfig(
                    name = MODEL_MIXTRAL_8X7B,
                    displayName = "Mixtral 8x7B",
                    provider = "OpenRouter",
                    apiKey = openRouterKey,
                    endpoint = OPENROUTER_API_URL,
                    isAvailable = true,
                    features = listOf("سریع", "دقیق", "مناسب کدنویسی", "پشتیبان")
                )
            )
        }
        
        // Claude Models (Anthropic) - در انتهای اولویت
        val claudeKey = prefs.getString("claude_api_key", null)
        if (!claudeKey.isNullOrEmpty()) {
            models.add(
                ModelConfig(
                    name = MODEL_CLAUDE_3_OPUS,
                    displayName = "Claude 3 Opus",
                    provider = "Anthropic",
                    apiKey = claudeKey,
                    endpoint = CLAUDE_API_URL,
                    isAvailable = true,
                    features = listOf("قدرتمندترین Claude", "تحلیل عمیق", "خلاقیت بالا", "پاسخ‌های طولانی")
                )
            )
        }
        
        // اولویت‌بندی: AIML → OpenAI → OpenRouter سبک → OpenRouter قوی → Claude
        val priority = mapOf(
            "aiml-gpt-3.5" to 1,
            MODEL_GPT_35_TURBO to 5,
            MODEL_GPT_4 to 6,
            "qwen/qwen2.5-1.5b-instruct" to 10,
            "meta-llama/Llama-3.2-1B-Instruct" to 11,
            "meta-llama/Llama-3.2-3B-Instruct" to 12,
            MODEL_LLAMA_3_3_70B to 20,
            MODEL_DEEPSEEK_R1T2 to 21,
            MODEL_MIXTRAL_8X7B to 25,
            MODEL_LLAMA_2_70B to 26,
            MODEL_CLAUDE_3_OPUS to 40,
            MODEL_CLAUDE_3_SONNET to 41
        )
        return models.sortedBy { priority[it.name] ?: 100 }
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
            else -> null
        }
        if (apiProvider != null) {
            val updatedKeys = prefsManager.getAPIKeys()
                .filter { it.provider != apiProvider }
                .toMutableList()
            updatedKeys.add(APIKey(apiProvider, apiKey, true))
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
                    // OpenRouter format
                    JSONObject().apply {
                        put("model", model.name)
                        put("messages", JSONArray().apply {
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
     * تبدیل صدا به متن با Whisper
     */
    suspend fun transcribeAudio(
        audioFile: java.io.File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val openAIKey = prefs.getString("openai_api_key", null)
        
        if (openAIKey.isNullOrEmpty()) {
            withContext(Dispatchers.Main) {
                onError("❌ برای استفاده از تبدیل صدا به متن، کلید OpenAI API مورد نیاز است")
            }
            return@withContext
        }
        
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    RequestBody.create("audio/mp3".toMediaType(), audioFile)
                )
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("language", "fa")
                .build()
            
            val request = Request.Builder()
                .url(OPENAI_WHISPER_URL)
                .addHeader("Authorization", "Bearer $openAIKey")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                    val json = JSONObject(responseBody)
                    val text = json.getString("text")
                    
                    withContext(Dispatchers.Main) {
                        onSuccess(text)
                    }
                } else {
                    val errorMessage = when (response.code) {
                        401 -> "❌ کلید OpenAI API نامعتبر است"
                        413 -> "❌ فایل صوتی بیش از حد بزرگ است (حداکثر 25MB)"
                        429 -> "⚠️ محدودیت استفاده از API - لطفاً کمی صبر کنید"
                        else -> "خطا در تبدیل صدا: ${response.code}"
                    }
                    
                    withContext(Dispatchers.Main) {
                        onError(errorMessage)
                    }
                }
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
        val openAIKey = prefs.getString("openai_api_key", null)
        val claudeKey = prefs.getString("claude_api_key", null)
        val openRouterKey = prefs.getString("openrouter_api_key", null)
        val aimlKey = prefs.getString("aiml_api_key", null)
        
        return !openAIKey.isNullOrEmpty() || !claudeKey.isNullOrEmpty() || 
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
        
        val model = models.first()
        var result = ""
        
        try {
            sendMessage(
                model = model,
                message = prompt,
                onResponse = { result = it },
                onError = { result = "خطا: $it" }
            )
            
            // Wait a bit for async completion (not ideal but simple)
            kotlinx.coroutines.delay(2000)
        } catch (e: Exception) {
            result = "خطا: ${e.message}"
        }
        
        return@withContext result
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
