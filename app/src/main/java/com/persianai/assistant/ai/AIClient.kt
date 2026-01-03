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

    private fun callWhisperLike(url: String, key: String, body: okhttp3.MultipartBody): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string()
            if (!resp.isSuccessful) {
                android.util.Log.e("AIClient", "Whisper-like error ${resp.code}: $respBody")
                return ""
            }
            if (respBody.isNullOrBlank()) return ""
            return try {
                val json = gson.fromJson(respBody, JsonObject::class.java)
                json.get("text")?.asString ?: json.get("generated_text")?.asString ?: respBody
            } catch (_: Exception) {
                respBody
            }
        }
    }

    private fun callHuggingFaceWhisper(url: String, key: String, body: okhttp3.MultipartBody): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Accept", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string()
            if (!resp.isSuccessful) {
                android.util.Log.e("AIClient", "HF Whisper error ${resp.code}: $respBody")
                return ""
            }
            if (respBody.isNullOrBlank()) return ""
            return try {
                val json = gson.fromJson(respBody, JsonObject::class.java)
                json.get("text")?.asString ?: json.get("generated_text")?.asString ?: respBody
            } catch (_: Exception) {
                respBody
            }
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
        // Priority: Liara -> HF (any provider, key starts with hf_) -> OpenAI -> fallback HF default (raw)
        val liaraKey = apiKeys.firstOrNull { it.provider == AIProvider.LIARA && it.isActive }
        val hfKey = apiKeys.firstOrNull { it.isActive && it.key.startsWith("hf_") }
        val openAiKey = apiKeys.firstOrNull { it.provider == AIProvider.OPENAI && it.isActive }
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

            // اولویت: Liara -> HF -> OpenAI -> HF (خام) -> HF پیش‌فرض
            // Liara
            liaraKey?.let { key ->
                val baseUrl = key.baseUrl?.trim()?.trimEnd('/')
                    ?: "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1"
                val url1 = "$baseUrl/audio/transcriptions"
                android.util.Log.d("AIClient", "transcribeAudio using LIARA at $url1")
                callWhisperLike(url1, key.key, requestBody).takeIf { it.isNotBlank() }?.let { return@withContext it }

                val url2 = "$baseUrl/audio:transcribe"
                android.util.Log.d("AIClient", "Liara retry at $url2")
                callWhisperLike(url2, key.key, requestBody).takeIf { it.isNotBlank() }?.let { return@withContext it }
            }

            // HuggingFace (multipart)
            hfKey?.let { key ->
                val url = key.baseUrl?.trim()?.trimEnd('/')
                    ?: "https://api-inference.huggingface.co/models/openai/whisper-large-v3"
                android.util.Log.d("AIClient", "transcribeAudio using HF key at $url")
                callHuggingFaceWhisper(url, key.key, requestBody).takeIf { it.isNotBlank() }?.let { return@withContext it }
            }

            // OpenAI (اگر HF پاسخ نداد)
            openAiKey?.let { key ->
                val baseUrl = key.baseUrl?.trim()?.trimEnd('/') ?: "https://api.openai.com/v1"
                val url = "$baseUrl/audio/transcriptions"
                android.util.Log.d("AIClient", "transcribeAudio using OpenAI at $url")
                callWhisperLike(url, key.key, requestBody).takeIf { it.isNotBlank() }?.let { return@withContext it }
            }

            // HF raw bytes با توکن موجود یا پیش‌فرض
            val hfToken = hfKey?.key ?: fallbackHf
            if (!hfToken.isNullOrBlank()) {
                val raw = callHuggingFaceRaw(hfToken, mediaTypeStr, file)
                if (raw.isNotBlank()) return@withContext raw
            }

            ""
        } catch (e: Exception) {
            android.util.Log.e("AIClient", "Whisper transcription error: ${e.message}", e)
            return@withContext ""
        }
    }

    private fun callHuggingFaceRaw(token: String, mediaTypeStr: String, file: java.io.File): String {
        val url = "https://api-inference.huggingface.co/models/openai/whisper-large-v3?wait_for_model=true"
        val body = file.readBytes().toRequestBody(mediaTypeStr.toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string()
            if (!resp.isSuccessful) {
                android.util.Log.e("AIClient", "HF STT (raw) error: ${resp.code} - $respBody")
                return ""
            }
            if (respBody.isNullOrBlank()) return ""
            if (respBody.startsWith("<!doctype", true)) {
                android.util.Log.e("AIClient", "HF STT returned HTML (blocked)")
                return ""
            }
            return try {
                val json = gson.fromJson(respBody, JsonObject::class.java)
                json.get("text")?.asString ?: json.get("generated_text")?.asString ?: respBody
            } catch (_: Exception) {
                respBody
            }
        }
    }
}
