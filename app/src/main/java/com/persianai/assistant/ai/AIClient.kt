package com.persianai.assistant.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.persianai.assistant.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * کلاینت اصلی برای ارتباط با APIهای هوش مصنوعی
 * با قابلیت خودکار تعویض کلید در صورت خطا
 */
class AIClient(private val apiKeys: List<APIKey>) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)  // 60 سے 120
        .readTimeout(120, TimeUnit.SECONDS)     // 60 سے 120
        .writeTimeout(120, TimeUnit.SECONDS)    // 60 سے 120
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .addInterceptor { chain ->
            var request = chain.request()
            var attempt = 0
            var response: okhttp3.Response? = null
            var exception: Exception? = null
            
            while (attempt < 3) {
                try {
                    response = chain.proceed(request)
                    if (response.isSuccessful) return@addInterceptor response
                    if (response.code !in listOf(500, 502, 503, 504)) break
                    attempt++
                    response.close()
                } catch (e: Exception) {
                    exception = e
                    if (attempt < 2) {
                        Thread.sleep(1000L * (attempt + 1))
                    }
                    attempt++
                }
            }
            response ?: throw exception ?: Exception("Unknown error")
        }
        // SSL certificate trust (development میں)
        .sslSocketFactory(
            createSSLSocketFactory(),
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
        .hostnameVerifier { _, _ -> true }
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

        // Ivira: اینجا مدیریت نمی‌شود (توکن‌محور در QueryRouter/IviraAPIClient)
        if (model.provider == AIProvider.IVIRA) {
            throw IllegalStateException("IVIRA توسط QueryRouter/IviraAPIClient مدیریت می‌شود، AIClient نباید مستقیماً فراخوانی شود")
        }

        val priority = listOf(
            AIProvider.OPENAI,
            AIProvider.LIARA,
            AIProvider.AVALAI,
            AIProvider.OPENROUTER,
            AIProvider.AIML,
            AIProvider.GLADIA,
            AIProvider.ANTHROPIC,
            AIProvider.LOCAL
        )
        val availableKeys = apiKeys.filter {
            it.provider == model.provider && it.isActive && it.key.isNotBlank() && !failedKeys.contains(it.key)
        }.sortedBy { k ->
            priority.indexOf(k.provider).let { if (it == -1) Int.MAX_VALUE else it }
        }.filter {
            if (model.provider == AIProvider.OPENROUTER && it.key.startsWith("hf_")) {
                false
            } else true
        }
        
        if (availableKeys.isEmpty()) {
            android.util.Log.e("AIClient", "❌ No active keys for ${model.provider.name}")
            throw IllegalStateException("هیچ کلید فعالی برای ${model.provider.name} یافت نشد - برای استفاده از ویژگی‌های آنلاین کلید اضافه کنید")
        }

        var lastError: Exception? = null
        for (apiKey in availableKeys) {
            try {
                android.util.Log.d("AIClient", "🔄 تلاش برای ارسال پیام با ${model.provider.name} key: ${apiKey.key.take(8)}...")
                return@withContext when (model.provider) {
                    AIProvider.AIML, AIProvider.GLADIA -> sendToOpenAI(model, messages, systemPrompt, apiKey)
                    AIProvider.OPENAI, AIProvider.OPENROUTER, AIProvider.LIARA, AIProvider.AVALAI -> sendToOpenAI(model, messages, systemPrompt, apiKey)
                    AIProvider.ANTHROPIC -> sendToClaude(model, messages, systemPrompt, apiKey)
                    AIProvider.LOCAL -> throw IllegalStateException("مدل آفلاین نیاز به AIClient ندارد")
                    AIProvider.IVIRA -> throw IllegalStateException("IVIRA باید در QueryRouter/IviraAPIClient مدیریت شود")
                }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("AIClient", "⚠️ Key failed: ${e.message}")
                val errorMsg = e.message ?: ""
                if (errorMsg.contains("401") ||
                    errorMsg.contains("402") ||
                    errorMsg.contains("403") ||
                    errorMsg.contains("400") ||
                    errorMsg.contains("Invalid")
                ) {
                    failedKeys.add(apiKey.key)
                    android.util.Log.d("AIClient", "🚫 Key marked as permanently failed: ${apiKey.key.take(8)}...")
                }
                continue
            }
        }

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
            AIProvider.OPENAI -> baseUrl?.let { "$it/chat/completions" }
                ?: "https://api.openai.com/v1/chat/completions"
            AIProvider.LIARA -> baseUrl?.let { "$it/chat/completions" }
                ?: "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1/chat/completions"
            AIProvider.AVALAI -> baseUrl?.let { "$it/chat/completions" }
                ?: "https://avalai.ir/api/v1/chat/completions"
            AIProvider.OPENROUTER -> baseUrl?.let { "$it/chat/completions" }
                ?: "https://openrouter.ai/api/v1/chat/completions"
            AIProvider.AIML -> baseUrl?.let { "$it/chat/completions" }
                ?: "https://api.aimlapi.com/v1/chat/completions"
            AIProvider.GLADIA -> baseUrl?.let { "$it/chat/completions" }
                ?: "https://api.gladia.io/v1/chat/completions"
            else -> baseUrl?.let { "$it/chat/completions" }
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

        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer ${apiKey.key}")
            .addHeader("Content-Type", "application/json")
        if (apiKey.provider == AIProvider.OPENROUTER) {
            // OpenRouter نیاز به Referer و X-Title دارد
            requestBuilder.addHeader("HTTP-Referer", "https://openrouter.ai/")
            requestBuilder.addHeader("X-Title", "Persian AI Assistant")
        }
        val request = requestBuilder.post(body).build()

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
     * ✅ فقط OpenAI (سریع‌تر/سازگارتر). اگر نبود یا خالی بود، استثناء برمی‌گرداند.
     */
    suspend fun transcribeAudio(audioFilePath: String): String = withContext(Dispatchers.IO) {
        val openAiKey = apiKeys.firstOrNull { it.provider == AIProvider.OPENAI && it.isActive }

        // فایل
        val file = java.io.File(audioFilePath)
        if (!file.exists()) return@withContext ""

        if (openAiKey == null) {
            android.util.Log.w("AIClient", "No OpenAI key for STT")
            return@withContext ""
        }

        // بدنه استاندارد (OpenAI Whisper)
        val mediaTypeAudio = "audio/wav".toMediaType()
        val standardBody = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", file.name, okhttp3.RequestBody.create(mediaTypeAudio, file))
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("language", "fa")
            .build()

        return@withContext try {
            withTimeout(20000) {
                val baseUrl = openAiKey.baseUrl?.trim()?.trimEnd('/') ?: "https://api.openai.com/v1"
                val url = "$baseUrl/audio/transcriptions"
                android.util.Log.d("AIClient", "transcribeAudio using OpenAI at $url")
                callWhisperLike(url, openAiKey.key, standardBody)
            }
        } catch (e: Exception) {
            android.util.Log.e("AIClient", "OpenAI STT failed: ${e.message}")
            ""
        }
    }

    /**
     * AIML async STT دو مرحله‌ای: stt/create سپس polling روی stt/{id}
     */
    private suspend fun callAimlSttAsync(
        baseUrl: String,
        key: String,
        mediaTypeStr: String,
        file: java.io.File
    ): String {
        val createUrl = "$baseUrl/stt/create"
        val body = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                okhttp3.RequestBody.Companion.create(
                    mediaTypeStr.toMediaType(),
                    file
                )
            )
            .addFormDataPart("model", "#g1_whisper-small")
            .addFormDataPart("language", "fa")
            .build()

        val createReq = Request.Builder()
            .url(createUrl)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Accept", "application/json")
            .post(body)
            .build()

        val generationId = try {
            client.newCall(createReq).execute().use { resp ->
                val respBody = resp.body?.string()
                if (!resp.isSuccessful) {
                    android.util.Log.e("AIClient", "AIML stt/create error ${resp.code}: $respBody")
                    return ""
                }
                val json = gson.fromJson(respBody, JsonObject::class.java)
                json.get("generation_id")?.asString ?: ""
            }
        } catch (e: Exception) {
            android.util.Log.e("AIClient", "AIML stt/create exception: ${e.message}", e)
            return ""
        }

        if (generationId.isBlank()) return ""

        val pollUrl = "$baseUrl/stt/$generationId"
        repeat(5) { _ ->
            val pollReq = Request.Builder()
                .url(pollUrl)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            try {
                client.newCall(pollReq).execute().use { resp ->
                    val respBody = resp.body?.string()
                    if (!resp.isSuccessful) {
                        android.util.Log.e("AIClient", "AIML stt poll error ${resp.code}: $respBody")
                        return ""
                    }
                    if (respBody.isNullOrBlank()) return ""
                    val json = gson.fromJson(respBody, JsonObject::class.java)
                    val status = json.get("status")?.asString ?: ""
                    if (status.equals("waiting", true) || status.equals("active", true)) {
                        // keep polling
                    } else {
                        val result = json.getAsJsonObject("result")
                        val transcript = result
                            ?.getAsJsonObject("results")
                            ?.getAsJsonArray("channels")
                            ?.firstOrNull()
                            ?.asJsonObject
                            ?.getAsJsonArray("alternatives")
                            ?.firstOrNull()
                            ?.asJsonObject
                            ?.get("transcript")
                            ?.asString
                        if (!transcript.isNullOrBlank()) return transcript
                        return respBody
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AIClient", "AIML stt poll exception: ${e.message}", e)
                return ""
            }

            delay(1500)
        }

        return ""
    }

    private fun callHuggingFaceRaw(token: String, mediaTypeStr: String, file: java.io.File): String {
        val url = "https://router.huggingface.co/models/openai/whisper-large-v3?wait_for_model=true"
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

    private fun callGladiaTranscribe(url: String, key: String, body: okhttp3.MultipartBody): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("x-gladia-key", key)
            .addHeader("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string()
            if (!resp.isSuccessful) {
                android.util.Log.e("AIClient", "Gladia STT error ${resp.code}: $respBody")
                return ""
            }
            if (respBody.isNullOrBlank()) return ""
            return try {
                val json = gson.fromJson(respBody, JsonObject::class.java)
                // Prefer common fields
                json.get("text")?.asString
                    ?: json.get("transcription")?.asString
                    ?: json.get("result")?.asJsonObject?.get("transcription")?.asString
                    ?: respBody
            } catch (_: Exception) {
                respBody
            }
        }
    }

    private fun createSSLSocketFactory(): javax.net.ssl.SSLSocketFactory {
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, null)
            return sslContext.socketFactory
        } catch (e: Exception) {
            throw RuntimeException("Failed to create SSL socket factory", e)
        }
    }
}
