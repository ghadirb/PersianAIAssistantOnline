package com.persianai.assistant.integration

import android.content.Context
import android.util.Log
import com.persianai.assistant.api.IviraAPIClient
import com.persianai.assistant.utils.IviraTokenManager
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مدیر یکپارچه Ivira Integration
 * 
 * اولویت‌ها:
 * 1. API متنی: Vira → GPT-5 Mini → GPT-5 Nano → Gemma 3
 * 2. TTS (متن به صدا): Avangardi → Google TTS
 * 3. STT (صدا به متن): Awasho → Offline
 * 4. Chat: اول Ivira، سپس offline models
 */
class IviraIntegrationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "IviraIntegration"
        
        // Status codes
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_PARTIAL = "PARTIAL"
        const val STATUS_UNAVAILABLE = "UNAVAILABLE"
    }
    
    private val tokenManager = IviraTokenManager(context)
    private val apiClient = IviraAPIClient(context)
    private val prefsManager = PreferencesManager(context)
    
    /**
     * مقدار دهی و بارگذاری توکن‌های Ivira
     * اگر توکن‌ها از طریق PreferencesManager موجود باشند، از آنها استفاده می‌شود
     */
    suspend fun initializeIviraTokens(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Initializing Ivira tokens...")
            
            val storedTokens = prefsManager.getIviraTokens()
            
            if (storedTokens.isNotEmpty()) {
                Log.d(TAG, "✅ Found ${storedTokens.size} stored Ivira tokens")
                return@withContext true
            }
            
            Log.w(TAG, "⚠️ No Ivira tokens found in preferences")
            return@withContext false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing Ivira tokens", e)
            false
        }
    }

    /**
     * بارگذاری مجدد توکن‌ها از منبع رمزشده (برای تنظیمات)
     */
    suspend fun reloadTokensManually(): Result<Map<String, String>> {
        return tokenManager.fetchEncryptedTokensFromUrl()
    }

    /**
     * وضعیت خلاصه برای نمایش در تنظیمات
     */
    fun getTokenStatusForSettings(): String = getTokensStatus()
    
    /**
     * دریافت وضعیت توکن‌ها
     */
    fun getTokensStatus(): String {
        val hasTokens = tokenManager.hasTokens()
        val available = apiClient.getAvailableTokensInfo()
        val activeCount = available.count { it.value }
        
        return when {
            hasTokens && activeCount > 0 -> "✅ $STATUS_SUCCESS ($activeCount models available)"
            hasTokens -> "$STATUS_PARTIAL (tokens exist but no models)"
            else -> "❌ $STATUS_UNAVAILABLE"
        }
    }
    
    /**
     * استفاده از Ivira برای پیام‌های متنی
     */
    suspend fun sendMessageViaIvira(
        message: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            if (!tokenManager.hasTokens()) {
                onError("❌ توکن Ivira موجود نیست")
                return@withContext false
            }
            
            var resultReceived = false
            
            apiClient.sendMessage(
                message = message,
                onResponse = { response ->
                    Log.d(TAG, "✅ Got response from Ivira")
                    onSuccess(response)
                    resultReceived = true
                },
                onError = { error ->
                    Log.w(TAG, "⚠️ Ivira error: $error")
                    onError(error)
                    resultReceived = false
                }
            )
            
            resultReceived
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in sendMessageViaIvira", e)
            onError("خطا: ${e.message}")
            false
        }
    }
    
    /**
     * استفاده از Ivira برای تبدیل متن به صدا (TTS)
     */
    suspend fun textToSpeechViaIvira(
        text: String,
        onSuccess: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            if (!tokenManager.hasTokens()) {
                onError("❌ توکن Ivira موجود نیست")
                return@withContext false
            }
            
            var resultReceived = false
            
            apiClient.textToSpeech(
                text = text,
                onSuccess = { audioBytes ->
                    Log.d(TAG, "✅ Got audio from Ivira TTS")
                    onSuccess(audioBytes)
                    resultReceived = true
                },
                onError = { error ->
                    Log.w(TAG, "⚠️ Ivira TTS error: $error")
                    onError(error)
                    resultReceived = false
                }
            )
            
            resultReceived
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in TTS", e)
            onError("خطا: ${e.message}")
            false
        }
    }
    
    /**
     * استفاده از Ivira برای تبدیل صدا به متن (STT)
     */
    suspend fun speechToTextViaIvira(
        audioFile: java.io.File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            if (!tokenManager.hasTokens()) {
                onError("❌ توکن Ivira موجود نیست")
                return@withContext false
            }
            
            var resultReceived = false
            
            apiClient.speechToText(
                audioFile = audioFile,
                onSuccess = { text ->
                    Log.d(TAG, "✅ Got text from Ivira STT")
                    onSuccess(text)
                    resultReceived = true
                },
                onError = { error ->
                    Log.w(TAG, "⚠️ Ivira STT error: $error")
                    onError(error)
                    resultReceived = false
                }
            )
            
            resultReceived
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in STT", e)
            onError("خطا: ${e.message}")
            false
        }
    }
    
    /**
     * بررسی آیا Ivira موجود و آماده است
     */
    fun isIviraReady(): Boolean {
        return tokenManager.hasTokens() && apiClient.hasTokens()
    }
    
    /**
     * دریافت اطلاعات توکن‌های موجود
     */
    fun getAvailableTokensInfo(): Map<String, Boolean> {
        return apiClient.getAvailableTokensInfo()
    }
    
    /**
     * خاموش کردن و تمیز کردن
     */
    fun shutdown() {
        try {
            Log.d(TAG, "Shutting down Ivira Integration Manager")
            // در آینده می‌توان اضافه کرد
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
}
