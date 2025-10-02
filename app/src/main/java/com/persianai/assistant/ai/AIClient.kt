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
            throw IllegalStateException("هیچ کلید فعالی برای ${model.provider.name} یافت نشد")
        }

        // تلاش با کلیدهای مختلف تا موفق شویم
        var lastError: Exception? = null
        for (apiKey in availableKeys) {
            try {
                return@withContext when (model.provider) {
                    AIProvider.OPENAI, AIProvider.OPENROUTER -> sendToOpenAI(model, messages, systemPrompt, apiKey)
                    AIProvider.ANTHROPIC -> sendToClaude(model, messages, systemPrompt, apiKey)
                }
            } catch (e: Exception) {
                lastError = e
                // اگر خطای 401 (Unauthorized) یا 400 بود، این کلید را علامت‌گذاری کن
                if (e.message?.contains("401") == true || e.message?.contains("400") == true) {
                    failedKeys.add(apiKey.key)
                }
                // ادامه به کلید بعدی
                continue
            }
        }
        
        // اگر همه کلیدها ناموفق بودند
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
        
        val apiUrl = if (apiKey.provider == AIProvider.OPENROUTER) {
            "https://openrouter.ai/api/v1/chat/completions"
        } else {
            "https://api.openai.com/v1/chat/completions"
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
            temperature = 0.1,  // خیلی پایین برای دقت JSON
            maxTokens = 4096
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
        val openAIKey = apiKeys.firstOrNull { 
            it.provider == AIProvider.OPENAI && it.isActive 
        } ?: throw IllegalStateException("کلید OpenAI یافت نشد برای Whisper")

        val file = java.io.File(audioFilePath)
        if (!file.exists()) {
            throw IllegalArgumentException("فایل صوتی یافت نشد")
        }

        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                okhttp3.RequestBody.Companion.create(
                    "audio/mpeg".toMediaType(),
                    file
                )
            )
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("language", "fa")
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${openAIKey.key}")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw Exception("خطای Whisper API: ${response.code} - $responseBody")
            }

            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            jsonResponse.get("text")?.asString ?: throw Exception("متن خالی از Whisper")
        }
    }
}
