package com.persianai.assistant.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.persianai.assistant.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * کلاینت اصلی برای ارتباط با APIهای هوش مصنوعی
 * با قابلیت خودکار تعویض کلید در صورت خطا
 */
class AIClient(private val apiKeys: List<APIKey>) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    
    // ردیابی کلیدهای ناموفق
    private val failedKeys = mutableSetOf<String>()

    /**
     * ارسال پیام به مدل هوش مصنوعی با قابلیت تعویض خودکار کلید
     */
    suspend fun sendMessage(
        model: AIModel,
        messages: List<ChatMessage>,
        systemPrompt: String? = null
    ): ChatMessage = withContext(Dispatchers.IO) {
        
        // دریافت تمام کلیدهای فعال برای این provider
        val availableKeys = apiKeys.filter { 
            it.provider == model.provider && it.isActive && !failedKeys.contains(it.key)
        }
        
        if (availableKeys.isEmpty()) {
            android.util.Log.e("AIClient", "❌ No active keys for ${model.provider.name}")
            throw IllegalStateException("هیچ کلید فعالی برای ${model.provider.name} یافت نشد - برای استفاده از ویژگی‌های آنلاین کلید اضافه کنید")
        }

        // تلاش با کلیدهای مختلف تا موفق شویم
        var lastError: Exception? = null
        for (apiKey in availableKeys) {
            try {
                android.util.Log.d("AIClient", "🔄 تلاش برای ارسال پیام با ${model.provider.name} key: ${apiKey.key.take(8)}...")
                return@withContext when (model.provider) {
                    AIProvider.AIML -> sendToOpenAI(model, messages, systemPrompt, apiKey) // AIML API سازگار با OpenAI
                    AIProvider.OPENAI, AIProvider.OPENROUTER, AIProvider.LIARA -> sendToOpenAI(model, messages, systemPrompt, apiKey)
                    AIProvider.ANTHROPIC -> sendToClaude(model, messages, systemPrompt, apiKey)
                    AIProvider.LOCAL -> throw IllegalStateException("مدل آفلاین نیاز به AIClient ندارد")
                }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("AIClient", "⚠️ Key failed: ${e.message}")
                // اگر خطای 401 (Unauthorized) یا 400 بود، این کلید را علامت‌گذاری کن
                if (e.message?.contains("401") == true || e.message?.contains("400") == true) {
                    failedKeys.add(apiKey.key)
                }
                // ادامه به کلید بعدی
                continue
            }
        }
        
        // اگر همه کلیدها ناموفق بودند
        android.util.Log.e("AIClient", "❌ All keys failed: ${lastError?.message}")
        throw lastError ?: Exception("خطای نامشخص در ارسال پیام")
    }

    /**
     * ارسال به OpenAI یا OpenRouter
     */
    private suspend fun sendToOpenAI(
        model: AIModel,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        apiKey: APIKey
    ): ChatMessage = withContext(Dispatchers.IO) {
        
        val baseUrl = apiKey.baseUrl?.trim()?.trimEnd('/')
        val apiUrl = when (apiKey.provider) {
            AIProvider.OPENROUTER -> baseUrl?.let { "$it/api/v1/chat/completions" }
                ?: "https://openrouter.ai/api/v1/chat/completions"
            AIProvider.AIML -> baseUrl?.let { "$it/v1/chat/completions" }
                ?: "https://api.aimlapi.com/v1/chat/completions"
            AIProvider.OPENAI -> baseUrl?.let { "$it/v1/chat/completions" }
                ?: "https://api.openai.com/v1/chat/completions"
            AIProvider.LIARA -> baseUrl?.let {
                if (it.endsWith("/v1", ignoreCase = true)) "$it/chat/completions" else "$it/v1/chat/completions"
            } ?: "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1/chat/completions"
            else -> baseUrl?.let { "$it/v1/chat/completions" }
                ?: "https://api.openai.com/v1/chat/completions"
        }

        val messageList = mutableListOf<Map<String, String>>()
        
        if (systemPrompt != null) {
            messageList.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        
        messages.forEach { msg ->
            messageList.add(mapOf(
                "role" to when(msg.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                },
                "content" to msg.content
            ))
        }

        val requestBody = ChatRequest(
            model = model.modelId,
            messages = messageList,
            temperature = 0.0,  // صفر برای خروجی کاملاً قطعی
            maxTokens = 500     // کوتاه برای JSON
        )

        val jsonBody = gson.toJson(requestBody)
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer ${apiKey.key}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw Exception("خطای API: ${response.code} - $responseBody")
            }

            val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
            val content = chatResponse.choices.firstOrNull()?.message?.content
                ?: throw Exception("پاسخ خالی از API")

            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = content,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * ارسال به Claude (Anthropic)
     */
    private suspend fun sendToClaude(
        model: AIModel,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        apiKey: APIKey
    ): ChatMessage = withContext(Dispatchers.IO) {
        
        val apiUrl = "https://api.anthropic.com/v1/messages"

        val messageList = messages.map { msg ->
            mapOf(
                "role" to when(msg.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    else -> "user"
                },
                "content" to msg.content
            )
        }

        val jsonObject = JsonObject().apply {
            addProperty("model", model.modelId)
            add("messages", gson.toJsonTree(messageList))
            addProperty("max_tokens", 4096)
            if (systemPrompt != null) {
                addProperty("system", systemPrompt)
            }
        }

        val body = gson.toJson(jsonObject).toRequestBody(mediaType)

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("x-api-key", apiKey.key)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw Exception("خطای Claude API: ${response.code} - $responseBody")
            }

            val claudeResponse = gson.fromJson(responseBody, ClaudeResponse::class.java)
            val content = claudeResponse.content.firstOrNull()?.text
                ?: throw Exception("پاسخ خالی از Claude")

            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = content,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * تبدیل صوت به متن با Whisper API
     */
    suspend fun transcribeAudio(audioFilePath: String): String = withContext(Dispatchers.IO) {
        val openAiLikeKey = apiKeys.firstOrNull { it.provider == AIProvider.LIARA && it.isActive }
            ?: apiKeys.firstOrNull { it.provider == AIProvider.OPENAI && it.isActive }
        val hfKey = apiKeys.firstOrNull { it.provider == AIProvider.OPENROUTER && it.key.startsWith("hf_") }
            ?: apiKeys.firstOrNull { it.provider == AIProvider.OPENAI && it.key.startsWith("hf_") }
        val fallbackHf = com.persianai.assistant.utils.DefaultApiKeys.getHuggingFaceKey()

        val file = java.io.File(audioFilePath)
        if (!file.exists()) {
            android.util.Log.w("AIClient", "Audio file not found: $audioFilePath")
            return@withContext ""
        }

        try {
            val mediaTypeStr = when (file.extension.lowercase()) {
                "m4a", "mp4" -> "audio/mp4"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                "webm" -> "audio/webm"
                "mp3" -> "audio/mpeg"
                else -> "application/octet-stream"
            }
            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    okhttp3.RequestBody.Companion.create(
                        mediaTypeStr.toMediaType(),
                        file
                    )
                )
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("language", "fa")
                .build()

            // اولویت: Liara -> OpenAI -> HF Whisper
            val responseText = openAiLikeKey?.let { key ->
                val baseUrl = if (key.provider == AIProvider.LIARA) {
                    key.baseUrl?.trim()?.trimEnd('/') ?: "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1"
                } else {
                    key.baseUrl?.trim()?.trimEnd('/') ?: "https://api.openai.com/v1"
                }
                
                val audioUrl = when {
                    baseUrl.endsWith("/v1", ignoreCase = true) -> "$baseUrl/audio/transcriptions"
                    baseUrl.contains("/v1/", ignoreCase = true) -> baseUrl.replaceAfterLast('/', "audio/transcriptions")
                    else -> "$baseUrl/v1/audio/transcriptions"
                }
                
                android.util.Log.d("AIClient", "Whisper URL: $audioUrl")
                
                val request = Request.Builder()
                    .url(audioUrl)
                    .addHeader("Authorization", "Bearer ${key.key}")
                    .post(requestBody)
                    .build()
                    
                try {
                    client.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string()
                        if (!response.isSuccessful) {
                            android.util.Log.e("AIClient", "Whisper error: ${response.code} - $bodyStr")
                            null
                        } else {
                            val jsonResponse = gson.fromJson(bodyStr, JsonObject::class.java)
                            jsonResponse.get("text")?.asString
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AIClient", "Whisper exception: ${e.message}")
                    null
                }
            }

            if (!responseText.isNullOrBlank()) return@withContext responseText

            val hfToken = hfKey?.key ?: fallbackHf
            if (hfToken.isNullOrBlank()) return@withContext ""

            val hfReqBody = file.readBytes().toRequestBody(mediaTypeStr.toMediaType())
            val hfRequest = Request.Builder()
                .url("https://api-inference.huggingface.co/models/openai/whisper-large-v3")
                .addHeader("Authorization", "Bearer $hfToken")
                .post(hfReqBody)
                .build()
            client.newCall(hfRequest).execute().use { resp ->
                val bodyStr = resp.body?.string()
                if (!resp.isSuccessful) {
                    android.util.Log.e("AIClient", "HF STT error: ${resp.code} - $bodyStr")
                    return@withContext ""
                }
                if (bodyStr.isNullOrBlank()) return@withContext ""
                if (bodyStr.trim().startsWith("{")) {
                    return@withContext try {
                        val json = gson.fromJson(bodyStr, JsonObject::class.java)
                        json.get("text")?.asString ?: json.get("generated_text")?.asString ?: ""
                    } catch (_: Exception) {
                        bodyStr
                    }
                }
                return@withContext bodyStr
            }
        } catch (e: Exception) {
            android.util.Log.e("AIClient", "Whisper transcription error: ${e.message}", e)
            return@withContext ""
        }
    }
}
