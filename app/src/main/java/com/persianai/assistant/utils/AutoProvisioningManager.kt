package com.persianai.assistant.utils

import android.content.Context
import android.util.Log
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مدیر بارگذاری خودکار کلیدهای API با اولویت‌بندی هوشمند
 * 
 * اولویت‌های کلید:
 * 1. AIML API (aimlapi.com) - رایگان
 * 2. OpenRouter (openrouter.ai) - رایگان/ارزان
 * 3. OpenAI (openai.com) - پولی
 * 4. HuggingFace (huggingface.co) - برای STT
 * 
 * مدل‌های اولویت‌دار (برای موبایل‌های ضعیف):
 * 1. Qwen2.5 1.5B
 * 2. LLaMA 3.2 1B quantized
 * 3. LLaMA 3.2 3B quantized
 * 4. GPT و مدل‌های رایگان دیگر
 */
object AutoProvisioningManager {
    
    private const val TAG = "AutoProvisioning"
    private const val DRIVE_FILE_ID = "17iwkjyGcxJeDgwQWEcsOdfbOxOah_0u0"
    private const val DEFAULT_PASSWORD = "12345"
    
    /**
     * بارگذاری خودکار کلیدها در ابتدای برنامه
     */
    suspend fun autoProvision(context: Context): Result<List<APIKey>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "شروع بارگذاری خودکار کلیدها...")
            
            val prefsManager = PreferencesManager(context)
            
            // چک کردن که قبلاً کلید داریم یا نه
            if (prefsManager.hasAPIKeys()) {
                Log.d(TAG, "کلیدهای قبلی یافت شد، بازگشت")
                return@withContext Result.success(prefsManager.getAPIKeys())
            }
            
            // دانلود فایل رمزشده
            Log.d(TAG, "دانلود فایل از Google Drive...")
            val encryptedData = try {
                DriveHelper.downloadEncryptedKeys()
            } catch (e: Exception) {
                Log.e(TAG, "خطا در دانلود", e)
                return@withContext Result.failure(Exception("خطا در دانلود فایل کلیدها: ${e.message}"))
            }
            
            // رمزگشایی
            Log.d(TAG, "رمزگشایی فایل...")
            val decryptedData = try {
                EncryptionHelper.decrypt(encryptedData, DEFAULT_PASSWORD)
            } catch (e: Exception) {
                Log.e(TAG, "خطا در رمزگشایی", e)
                return@withContext Result.failure(Exception("خطا در رمزگشایی کلیدها: ${e.message}"))
            }
            
            // پارس کلیدها
            Log.d(TAG, "پارس کلیدها...")
            val apiKeys = parseAPIKeys(decryptedData)
            
            if (apiKeys.isEmpty()) {
                Log.w(TAG, "هیچ کلید معتبری یافت نشد")
                return@withContext Result.failure(Exception("هیچ کلید معتبری در فایل یافت نشد"))
            }
            
            // ذخیره کلیدها
            prefsManager.saveAPIKeys(apiKeys)
            Log.d(TAG, "✅ ${apiKeys.size} کلید با موفقیت بارگذاری شد")
            
            // نمایش اطلاعات کلیدها
            apiKeys.groupBy { it.provider }.forEach { (provider, keys) ->
                Log.d(TAG, "  - ${provider.name}: ${keys.size} کلید")
            }
            
            Result.success(apiKeys)
            
        } catch (e: Exception) {
            Log.e(TAG, "خطای بارگذاری خودکار", e)
            Result.failure(e)
        }
    }
    
    /**
     * پارس کلیدها از فایل متنی با اولویت‌بندی
     */
    private fun parseAPIKeys(data: String): List<APIKey> {
        val keys = mutableListOf<APIKey>()
        
        // فرمت‌های پشتیبانی شده:
        // provider:key
        // key (با تشخیص خودکار)
        
        data.lines().forEach { line ->
            val trimmed = line.trim()
            
            // نادیده گرفتن خطوط خالی و کامنت
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                return@forEach
            }
            
            try {
                val (provider, key) = parseKeyLine(trimmed)
                if (provider != null && key.isNotBlank()) {
                    keys.add(APIKey(provider, key, true))
                    Log.d(TAG, "کلید پارس شد: ${provider.name} (${key.take(10)}...)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "خط نامعتبر نادیده گرفته شد: $trimmed")
            }
        }
        
        return prioritizeKeys(keys)
    }
    
    /**
     * پارس یک خط کلید با تشخیص خودکار provider
     */
    private fun parseKeyLine(line: String): Pair<AIProvider?, String> {
        // فرمت: provider:key
        if (line.contains(":")) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val providerName = parts[0].trim().lowercase()
                val key = parts[1].trim()
                
                val provider = when (providerName) {
                    "aiml", "aimlapi" -> AIProvider.AIML
                    "openrouter", "or" -> AIProvider.OPENROUTER
                    "openai", "gpt" -> AIProvider.OPENAI
                    "huggingface", "hf" -> AIProvider.OPENROUTER // ذخیره در OpenRouter برای سازگاری
                    "anthropic", "claude" -> AIProvider.ANTHROPIC
                    else -> null
                }
                
                return Pair(provider, key)
            }
        }
        
        // تشخیص خودکار از روی pattern کلید
        val key = line.trim()
        val provider = detectProvider(key)
        return Pair(provider, key)
    }
    
    /**
     * تشخیص خودکار provider از روی pattern کلید
     */
    private fun detectProvider(key: String): AIProvider? {
        return when {
            // AIML API
            key.startsWith("sk-aiml-") || key.length == 64 -> AIProvider.AIML
            
            // OpenRouter
            key.startsWith("sk-or-") -> AIProvider.OPENROUTER
            
            // OpenAI
            key.startsWith("sk-proj-") || (key.startsWith("sk-") && key.length > 40 && key.length < 55) -> AIProvider.OPENAI
            
            // HuggingFace
            key.startsWith("hf_") -> AIProvider.OPENROUTER // ذخیره با OpenRouter برای سازگاری
            
            // Anthropic (Claude)
            key.startsWith("sk-ant-") -> AIProvider.ANTHROPIC
            
            else -> null
        }
    }
    
    /**
     * اولویت‌بندی کلیدها بر اساس استراتژی:
     * 1. AIML (رایگان)
     * 2. OpenRouter (رایگان/ارزان)
     * 3. OpenAI (پولی)
     * 4. Anthropic (پولی)
     */
    private fun prioritizeKeys(keys: List<APIKey>): List<APIKey> {
        val priorityOrder = listOf(
            AIProvider.AIML,
            AIProvider.OPENROUTER,
            AIProvider.OPENAI,
            AIProvider.ANTHROPIC
        )
        
        return keys.sortedBy { key ->
            priorityOrder.indexOf(key.provider).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    }
    
    /**
     * تست کلیدها و غیرفعال کردن کلیدهای نامعتبر
     */
    suspend fun validateAndUpdateKeys(context: Context): Int {
        val prefsManager = PreferencesManager(context)
        val keys = prefsManager.getAPIKeys()
        
        if (keys.isEmpty()) return 0
        
        var validCount = 0
        val updatedKeys = mutableListOf<APIKey>()
        
        keys.forEach { key ->
            val isValid = testAPIKey(key)
            
            if (isValid) {
                validCount++
                updatedKeys.add(key.copy(isActive = true))
                Log.d(TAG, "✅ کلید معتبر: ${key.provider.name}")
            } else {
                updatedKeys.add(key.copy(isActive = false))
                Log.w(TAG, "❌ کلید نامعتبر: ${key.provider.name}")
            }
        }
        
        // ذخیره کلیدهای به‌روز شده
        prefsManager.saveAPIKeys(updatedKeys)
        
        return validCount
    }
    
    /**
     * تست یک کلید API
     */
    private suspend fun testAPIKey(apiKey: APIKey): Boolean = withContext(Dispatchers.IO) {
        try {
            // درخواست ساده برای تست کلید
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val request = when (apiKey.provider) {
                AIProvider.AIML -> {
                    okhttp3.Request.Builder()
                        .url("https://api.aimlapi.com/v1/models")
                        .addHeader("Authorization", "Bearer ${apiKey.key}")
                        .build()
                }
                AIProvider.OPENROUTER -> {
                    okhttp3.Request.Builder()
                        .url("https://openrouter.ai/api/v1/models")
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
                        .url("https://api.anthropic.com/v1/messages")
                        .addHeader("x-api-key", apiKey.key)
                        .addHeader("anthropic-version", "2023-06-01")
                        .build()
                }
                AIProvider.LOCAL -> {
                    // مدل آفلاین نیاز به تست شبکه ندارد؛ معتبر فرض می‌شود
                    return@withContext true
                }
            }
            
            client.newCall(request).execute().use { response ->
                // 200-299 = موفق
                response.isSuccessful
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "خطا در تست کلید ${apiKey.provider.name}", e)
            false
        }
    }
}
