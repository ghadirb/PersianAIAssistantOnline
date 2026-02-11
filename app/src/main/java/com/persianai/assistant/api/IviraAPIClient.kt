package com.persianai.assistant.api

import android.content.Context
import android.util.Log
import com.persianai.assistant.utils.IviraTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Ivira API Integration
 * پیام متنی، صدا تبدیل به متن، و متن تبدیل به صدا
 */
class IviraAPIClient(private val context: Context) {
    
    companion object {
        private const val TAG = "IviraAPIClient"
    }
    
    private val tokenManager = IviraTokenManager(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * ارسال پیام متنی به مدل‌های Ivira
     * اولویت: Vira → GPT-5 Mini → GPT-5 Nano → Gemma 3
     */
    suspend fun sendMessage(
        message: String,
        model: String? = null,
        onResponse: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val models: List<String> = if (model != null) {
                listOf(model)
            } else {
                tokenManager.getTextModelInPriority()
            }
            
            if (models.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onError("❌ هیچ توکن Ivira موجود نیست")
                }
                return@withContext
            }
            
            var lastError: String? = null
            
            // سعی برای هر مدل
            for (currentModel in models) {
                try {
                    val token = tokenManager.getToken(currentModel)
                        ?: continue
                    
                    Log.d(TAG, "🔄 Trying model: $currentModel")
                    
                    val requestBody = JSONObject().apply {
                        put("model", currentModel)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "system")
                                put("content", "تو یک دستیار هوشمند و مفید است. پاسخ را به زبان فارسی بده.")
                            })
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", message)
                            })
                        })
                        put("max_tokens", 2048)
                        put("temperature", 0.7)
                    }
                    
                    val request = Request.Builder()
                        .url(IviraTokenManager.IVIRA_API_URL)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer $token")
                        .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    
                    httpClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                            // بررسی اینکه آیا پاسخ JSON است یا HTML
                            if (responseBody.trim().startsWith("<")) {
                                Log.w(TAG, "Ivira returned HTML instead of JSON for $currentModel")
                                lastError = "Ivira returned HTML response (possibly 404 page)"
                                return@use null
                            }
                            
                            try {
                                val json = JSONObject(responseBody)
                                val content = json.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content")
                                
                                Log.d(TAG, "✅ Got response from $currentModel")
                                withContext(Dispatchers.Main) {
                                    onResponse(content)
                                }
                                return@withContext
                            } catch (e: JSONException) {
                                Log.w(TAG, "Failed to parse JSON from Ivira for $currentModel: ${e.message}")
                                Log.w(TAG, "Response body: ${responseBody.take(200)}...")
                                lastError = "Invalid JSON response from $currentModel"
                                return@use null
                            }
                        } else {
                            lastError = "خطا: پاسخ خالی از $currentModel (HTTP ${response.code})"
                            Log.w(TAG, "Empty response from $currentModel (HTTP ${response.code})")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error with model $currentModel: ${e.message}")
                    lastError = e.message
                    continue
                }
            }
            
            // اگر تمام مدل‌ها ناموفق بودند
            withContext(Dispatchers.Main) {
                onError(lastError ?: "❌ تمام مدل‌ها ناموفق بودند")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            withContext(Dispatchers.Main) {
                onError("خطا: ${e.message}")
            }
        }
    }
    
    /**
     * تبدیل متن به صدا (TTS) با Avangardi/Awasho
     */
    suspend fun textToSpeech(
        text: String,
        model: String? = null,
        onSuccess: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val models: List<String> = if (model != null) {
                listOf(model)
            } else {
                tokenManager.getTTSModelInPriority()
            }
            
            if (models.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onError("❌ هیچ مدل TTS موجود نیست")
                }
                return@withContext
            }
            
            var lastError: String? = null
            
            for (currentModel in models) {
                try {
                    val token = tokenManager.getToken(currentModel)
                        ?: continue
                    
                    Log.d(TAG, "🔊 TTS with model: $currentModel")
                    
                    val requestBody = JSONObject().apply {
                        put("model", currentModel)
                        put("input", text)
                        put("voice", "fa")  // Persian voice
                        put("language", "fa")
                    }
                    
                    val request = Request.Builder()
                        .url(IviraTokenManager.IVIRA_TTS_URL)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer $token")
                        .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    
                    httpClient.newCall(request).execute().use { response ->
                        val audioBytes = response.body?.bytes()
                        if (response.isSuccessful && audioBytes != null) {
                            Log.d(TAG, "✅ Got audio from $currentModel")
                            withContext(Dispatchers.Main) {
                                onSuccess(audioBytes)
                            }
                            return@withContext
                        } else {
                            lastError = "خطا: صدا خالی از $currentModel"
                            Log.w(TAG, "Empty audio from $currentModel")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "TTS Error with $currentModel: ${e.message}")
                    lastError = e.message
                    continue
                }
            }
            
            withContext(Dispatchers.Main) {
                onError(lastError ?: "❌ خطا در تبدیل متن به صدا")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in TTS", e)
            withContext(Dispatchers.Main) {
                onError("خطا: ${e.message}")
            }
        }
    }
    
    /**
     * تبدیل صدا به متن (STT) با Awasho
     */
    suspend fun speechToText(
        audioFile: java.io.File,
        model: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val models: List<String> = if (model != null) {
                listOf(model)
            } else {
                tokenManager.getSTTModelInPriority()
            }
            
            if (models.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onError("❌ هیچ مدل STT موجود نیست")
                }
                return@withContext
            }
            
            var lastError: String? = null
            
            for (currentModel in models) {
                try {
                    val token = tokenManager.getToken(currentModel)
                        ?: continue
                    
                    Log.d(TAG, "🎤 STT with model: $currentModel")
                    
                    // Avanegar STT uses different format
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "audio",
                            audioFile.name,
                            RequestBody.create("audio/*".toMediaType(), audioFile)
                        )
                        .addFormDataPart("model", "default")
                        .addFormDataPart("srt", "false")
                        .addFormDataPart("inverseNormalizer", "false")
                        .addFormDataPart("timestamp", "false")
                        .addFormDataPart("spokenPunctuation", "false")
                        .addFormDataPart("punctuation", "false")
                        .addFormDataPart("numSpeakers", "0")
                        .addFormDataPart("diarize", "false")
                        .build()
                    
                    val request = Request.Builder()
                        .url(IviraTokenManager.IVIRA_STT_URL)
                        .addHeader("gateway-token", token)
                        .addHeader("accept", "application/json")
                        .post(requestBody)
                        .build()
                    
                    httpClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string()
                        Log.d(TAG, "Response: $responseBody")
                        if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                            val json = JSONObject(responseBody)
                            // Parse Avanegar response format
                            if (json.has("data") && json.getJSONObject("data").has("data")) {
                                val data = json.getJSONObject("data").getJSONObject("data")
                                if (data.has("aiResponse") && data.getJSONObject("aiResponse").has("result")) {
                                    val result = data.getJSONObject("aiResponse").getJSONObject("result")
                                    val text = result.getString("text")
                                    
                                    Log.d(TAG, "✅ STT success with $currentModel")
                                    withContext(Dispatchers.Main) {
                                        onSuccess(text)
                                    }
                                    return@withContext
                                }
                            }
                            lastError = "خطا: فرمت پاسخ نامعتبر از $currentModel"
                            Log.w(TAG, "Invalid response format from $currentModel")
                        } else {
                            lastError = "خطا: کد ${response.code} از $currentModel"
                            Log.w(TAG, "HTTP error ${response.code} from $currentModel")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "STT Error with $currentModel: ${e.message}")
                    lastError = e.message
                    continue
                }
            }
            
            withContext(Dispatchers.Main) {
                onError(lastError ?: "❌ خطا در تبدیل صدا به متن")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in STT", e)
            withContext(Dispatchers.Main) {
                onError("خطا: ${e.message}")
            }
        }
    }
    
    /**
     * بررسی وجود توکن‌ها
     */
    fun hasTokens(): Boolean = tokenManager.hasTokens()
    
    /**
     * دریافت اطلاعات توکن‌های موجود
     */
    fun getAvailableTokensInfo(): Map<String, Boolean> {
        val models = listOf(
            IviraTokenManager.MODEL_VIRA,
            IviraTokenManager.MODEL_GPT5_MINI,
            IviraTokenManager.MODEL_GPT5_NANO,
            IviraTokenManager.MODEL_GEMMA3_27B,
            IviraTokenManager.MODEL_AVANGARDI,
            IviraTokenManager.MODEL_AWASHO
        )
        
        return models.associateWith { model ->
            tokenManager.getToken(model) != null
        }
    }
}