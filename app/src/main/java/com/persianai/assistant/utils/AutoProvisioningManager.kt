package com.persianai.assistant.utils

import android.content.Context
import android.util.Log
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مدیر بارگذاری خودکار کلیدهای API
 * استراتژی: اولویت Liara، سپس سایر providers
 */
object AutoProvisioningManager {
    
    private const val TAG = "AutoProvisioning"
    private const val DEFAULT_PASSWORD = "12345"
    private const val GIST_KEYS_URL =
        "https://gist.githubusercontent.com/ghadirb/626a804df3009e49045a2948dad89fe5/raw/5ec50251e01128e0ad8d380350a2002d5c5b585f/keys.txt"
    
    /**
     * بارگذاری خودکار کلیدها - اولویت Liara
     */
    suspend fun autoProvision(context: Context): Result<List<APIKey>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 شروع بارگذاری خودکار کلیدها...")
            
            val prefsManager = PreferencesManager(context)
            val existing = prefsManager.getAPIKeys()
            val hasActiveLiara = existing.any { it.provider == AIProvider.LIARA && it.isActive }
            
            if (hasActiveLiara) {
                Log.d(TAG, "✅ کلید Liara فعال است")
                return@withContext Result.success(existing)
            }
            
            // دانلود از gist
            Log.d(TAG, "📥 دانلود فایل رمزشده از gist...")
            val encryptedData = try {
                DriveHelper.downloadFromUrl(GIST_KEYS_URL)
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطا در دانلود از gist: ${e.message}")
                return@withContext Result.failure(e)
            }
            
            if (encryptedData.isBlank()) {
                Log.e(TAG, "❌ فایل دانلود شده خالی است")
                return@withContext Result.failure(Exception("فایل گیست خالی است"))
            }
            
            // رمزگشایی
            Log.d(TAG, "🔐 رمزگشایی فایل...")
            val decryptedData = try {
                EncryptionHelper.decrypt(encryptedData, DEFAULT_PASSWORD)
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطا در رمزگشایی: ${e.message}")
                return@withContext Result.failure(e)
            }
            
            if (decryptedData.isBlank()) {
                Log.e(TAG, "❌ فایل رمزگشایی شده خالی است")
                return@withContext Result.failure(Exception("رمزگشایی ناموفق"))
            }
            
            // پارس کلیدها
            Log.d(TAG, "📋 پارس کلیدها...")
            val allKeys = parseAPIKeys(decryptedData)
            
            if (allKeys.isEmpty()) {
                Log.w(TAG, "⚠️ هیچ کلید یافت نشد")
                Log.d(TAG, "Content: $decryptedData")
                return@withContext Result.failure(Exception("هیچ کلید معتبری در فایل یافت نشد"))
            }
            
            // فیلتر: تمام کلیدها فعال (تا از Dashboard انتخاب کند)
            // اولویت: Liara اول
            val liaraKeys = allKeys.filter { it.provider == AIProvider.LIARA }
                .map { it.copy(isActive = true) }
            val otherKeys = allKeys.filter { it.provider != AIProvider.LIARA }
                .map { it.copy(isActive = true) }  // تمام کلیدها فعال هستند، Dashboard انتخاب می‌کند
            val processedKeys = liaraKeys + otherKeys
            
            // ذخیره
            prefsManager.saveAPIKeys(processedKeys)
            prefsManager.setWorkingMode(PreferencesManager.WorkingMode.HYBRID)
            Log.d(TAG, "✅ ${processedKeys.size} کلید بارگذاری شد (اولویت Liara)")
            processedKeys.forEach { key ->
                Log.d(TAG, "  - ${key.provider.name}: ${if (key.isActive) "✔ فعال" else "✕ غیرفعال"}")
            }
            
            Result.success(processedKeys)
            
        } catch (e: Exception) {
            Log.e(TAG, "خطای بارگذاری: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * پارس کلیدها
     */
    private fun parseAPIKeys(data: String): List<APIKey> {
        val keys = mutableListOf<APIKey>()
        
        data.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            
            try {
                val (provider, key, baseUrl) = parseKeyLine(trimmed)
                if (provider != null && key.isNotBlank()) {
                    keys.add(APIKey(
                        provider = provider,
                        key = key,
                        baseUrl = baseUrl,
                        isActive = false // شروع غیرفعال، بعداً فعال می‌شود
                    ))
                    Log.d(TAG, "✓ پارس: ${provider.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "خط نامعتبر: $trimmed")
            }
        }
        
        return keys
    }
    
    /**
     * پارس یک خط
     * فرمت: provider:key:baseUrl (baseUrl اختیاری)
     */
    private fun parseKeyLine(line: String): Triple<AIProvider?, String, String?> {
        val parts = line.split(":").map { it.trim() }
        
        return when {
            parts.size >= 2 -> {
                val provider = when (parts[0].lowercase()) {
                    "liara" -> AIProvider.LIARA
                    "openai", "gpt" -> AIProvider.OPENAI
                    "anthropic", "claude" -> AIProvider.ANTHROPIC
                    "openrouter", "or" -> AIProvider.OPENROUTER
                    "aiml", "aimlapi" -> AIProvider.AIML
                    else -> return Triple(null, "", null)
                }
                
                val key = parts.getOrNull(1) ?: ""
                val baseUrl = parts.getOrNull(2)
                
                Triple(provider, key, baseUrl)
            }
            else -> Triple(null, "", null)
        }
    }
    
    /**
     * تست کلیدها
     */
    suspend fun validateAndUpdateKeys(context: Context): Int {
        val prefsManager = PreferencesManager(context)
        val keys = prefsManager.getAPIKeys()
        
        var validCount = 0
        val updatedKeys = mutableListOf<APIKey>()
        
        keys.forEach { key ->
            try {
                val isValid = testAPIKey(key)
                if (isValid) {
                    validCount++
                    updatedKeys.add(key.copy(isActive = true))
                    Log.d(TAG, "✅ معتبر: ${key.provider.name}")
                } else {
                    updatedKeys.add(key.copy(isActive = false))
                    Log.w(TAG, "❌ نامعتبر: ${key.provider.name}")
                }
            } catch (e: Exception) {
                updatedKeys.add(key.copy(isActive = false))
                Log.e(TAG, "خطا در تست: ${e.message}")
            }
        }
        
        prefsManager.saveAPIKeys(updatedKeys)
        return validCount
    }
    
    /**
     * تست یک کلید
     */
    private suspend fun testAPIKey(apiKey: APIKey): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val request = when (apiKey.provider) {
                AIProvider.LIARA -> {
                    val baseUrl = apiKey.baseUrl?.trim()?.trimEnd('/') 
                        ?: "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1"
                    okhttp3.Request.Builder()
                        .url("$baseUrl/models")
                        .addHeader("Authorization", "Bearer ${apiKey.key}")
                        .build()
                }
                AIProvider.OPENAI -> {
                    okhttp3.Request.Builder()
                        .url("https://api.openai.com/v1/models")
                        .addHeader("Authorization", "Bearer ${apiKey.key}")
                        .build()
                }
                AIProvider.ANTHROPIC -> {
                    okhttp3.Request.Builder()
                        .url("https://api.anthropic.com/v1/models")
                        .addHeader("x-api-key", apiKey.key)
                        .build()
                }
                else -> return@withContext true
            }
            
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "تست شکست: ${e.message}")
            false
        }
    }
}