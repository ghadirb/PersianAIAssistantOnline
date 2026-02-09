package com.persianai.assistant.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * مدیریت توکن های Ivira API
 * کلیدها از لینک رمزشده دریافت می‌شوند و استفاده می‌شوند
 */
class IviraTokenManager(private val context: Context) {
    
    companion object {
        private const val TAG = "IviraTokenManager"
        
        // Ivira API Endpoints
        const val IVIRA_API_URL = "https://api.ivira.ai/v1/chat/completions"
        const val IVIRA_TTS_URL = "https://api.ivira.ai/v1/audio/speech"
        const val IVIRA_STT_URL = "https://api.ivira.ai/v1/audio/transcriptions"
        
        // Models Priority
        const val MODEL_VIRA = "vira"  // مدل زبانی ترکیبی ویرا (اولویت)
        const val MODEL_GPT5_MINI = "gpt-5-mini"  // GPT-5 Mini
        const val MODEL_GPT5_NANO = "gpt-5-nano"  // GPT-5 Nano
        const val MODEL_GEMMA3_27B = "gemma3-27b"  // Gemma 3 27B
        const val MODEL_AVANGARDI = "avangardi"  // آوانگار (TTS) - جدید
        const val MODEL_AWASHO = "awasho"  // آواشو (STT) - جدید
        
        // Token encryption key
        private const val ENCRYPTION_KEY_LENGTH = 32  // 256-bit
        private const val IV_LENGTH = 12  // 96-bit for GCM
        private const val TAG_LENGTH = 16  // 128-bit for GCM
        private const val ITERATIONS = 20000
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val prefs = context.getSharedPreferences("ivira_tokens", Context.MODE_PRIVATE)
    
    /**
     * دریافت توکن‌های رمزشده از لینک
     */
    suspend fun fetchEncryptedTokensFromUrl(
        url: String = "https://abrehamrahi.ir/o/public/UfAv7lIC/",
        password: String = "12345"
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Fetching encrypted tokens from $url")
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Failed to fetch tokens: ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            
            val encryptedContent = response.body?.string() 
                ?: return@withContext Result.failure(Exception("Empty response"))
            
            // فک کردن توکن‌ها
            val tokens = decryptTokens(encryptedContent, password)
            
            // ذخیره توکن‌ها
            saveTokens(tokens)
            
            Log.d(TAG, "✅ Successfully fetched and saved ${tokens.size} tokens")
            Result.success(tokens)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching tokens", e)
            Result.failure(e)
        }
    }
    
    /**
     * فک کردن توکن‌های رمزشده (Base64 + AES-GCM)
     */
    private fun decryptTokens(encryptedB64: String, password: String): Map<String, String> {
        try {
            // Decode Base64
            val encryptedBytes = Base64.decode(encryptedB64.trim(), Base64.DEFAULT)
            
            // Extract salt and IV
            val salt = encryptedBytes.sliceArray(0 until 16)
            val iv = encryptedBytes.sliceArray(16 until 28)
            val ciphertext = encryptedBytes.sliceArray(28 until encryptedBytes.size)
            
            // Derive key using PBKDF2
            val key = deriveKey(password, salt)
            
            // Decrypt using AES-GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            
            val decryptedBytes = cipher.doFinal(ciphertext)
            val decryptedText = String(decryptedBytes, Charsets.UTF_8)
            
            // Parse tokens (each line is a token)
            val tokens = mutableMapOf<String, String>()
            decryptedText.split("\n").forEachIndexed { index, token ->
                if (token.isNotBlank()) {
                    val modelName = getModelNameForToken(index)
                    tokens[modelName] = token
                    Log.d(TAG, "✅ Extracted token for $modelName")
                }
            }
            
            return tokens
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error decrypting tokens", e)
            throw e
        }
    }
    
    /**
     * اشتقاق کلید از رمز عبور
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, ENCRYPTION_KEY_LENGTH * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec)
        return SecretKeySpec(key.encoded, 0, key.encoded.size, "AES")
    }
    
    /**
     * نام مدل را برای هر توکن تعیین کنید
     */
    private fun getModelNameForToken(index: Int): String {
        return when (index) {
            0 -> MODEL_VIRA  // Vira (ترکیبی)
            1 -> MODEL_GPT5_MINI
            2 -> MODEL_GPT5_NANO
            3 -> MODEL_GEMMA3_27B
            4 -> MODEL_AVANGARDI  // TTS
            5 -> MODEL_AWASHO  // STT
            else -> "unknown_model_$index"
        }
    }
    
    /**
     * ذخیره توکن‌ها در SharedPreferences
     */
    private fun saveTokens(tokens: Map<String, String>) {
        prefs.edit().apply {
            clear()
            tokens.forEach { (model, token) ->
                putString("token_$model", token)
                Log.d(TAG, "💾 Saved token for $model")
            }
            apply()
        }
    }
    
    /**
     * بازیافت توکن برای یک مدل
     */
    fun getToken(model: String): String? {
        return prefs.getString("token_$model", null)
    }
    
    /**
     * بازیافت تمام توکن‌ها
     */
    fun getAllTokens(): Map<String, String> {
        val tokens = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("token_") && value is String) {
                val model = key.removePrefix("token_")
                tokens[model] = value
            }
        }
        return tokens
    }
    
    /**
     * ذخیره یا به‌روزرسانی یک توکن
     */
    fun setToken(model: String, token: String) {
        prefs.edit().putString("token_$model", token).apply()
    }
    
    /**
     * چک کردن وجود توکن‌ها
     */
    fun hasTokens(): Boolean = getAllTokens().isNotEmpty()
    
    /**
     * پاک کردن تمام توکن‌ها
     */
    fun clearTokens() {
        prefs.edit().clear().apply()
        Log.d(TAG, "🗑️ Cleared all tokens")
    }
    
    /**
     * دریافت مدل با اولویت (اگر مدل اول کار نکند، مدل دوم را بکار)
     */
    fun getTextModelInPriority(): List<String> {
        return listOf(
            MODEL_VIRA,        // اولویت 1
            MODEL_GPT5_MINI,   // اولویت 2
            MODEL_GPT5_NANO,   // اولویت 3
            MODEL_GEMMA3_27B   // اولویت 4
        ).filter { model ->
            getToken(model) != null
        }
    }
    
    /**
     * دریافت مدل TTS با اولویت
     */
    fun getTTSModelInPriority(): List<String> {
        return listOf(
            MODEL_AVANGARDI,  // اولویت 1
            MODEL_AWASHO      // اولویت 2
        ).filter { model ->
            getToken(model) != null
        }
    }
    
    /**
     * دریافت مدل STT با اولویت
     */
    fun getSTTModelInPriority(): List<String> {
        return listOf(
            MODEL_AWASHO,     // اولویت 1
            MODEL_AVANGARDI   // اولویت 2
        ).filter { model ->
            getToken(model) != null
        }
    }
}
