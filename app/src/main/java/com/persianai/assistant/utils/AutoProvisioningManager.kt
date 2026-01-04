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
    // بدون هش کامیت تا با ویرایش محتوا نیاز به بیلد جدید نباشد
    private const val GIST_KEYS_URL =
        "https://gist.githubusercontent.com/ghadirb/626a804df3009e49045a2948dad89fe5/raw/keys.txt"
    
    /**
     * بارگذاری و فعال‌سازی کلیدها از gist (بدون تکیه بر وضعیت قبلی)
     */
    suspend fun autoProvision(context: Context): Result<List<APIKey>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 شروع بارگذاری خودکار کلیدها (بازنویسی‌شده)...")

            // 1) دانلود از gist
            val encryptedData = runCatching {
                Log.d(TAG, "📥 دانلود فایل رمزشده از gist: $GIST_KEYS_URL")
                DriveHelper.downloadFromUrl(GIST_KEYS_URL)
            }.getOrElse { e ->
                Log.e(TAG, "❌ خطا در دانلود از gist: ${e.message}")
                return@withContext Result.failure(e)
            }

            if (encryptedData.isBlank()) {
                Log.e(TAG, "❌ فایل دانلود شده خالی است")
                return@withContext Result.failure(Exception("فایل کلیدها خالی است"))
            }

            // 2) رمزگشایی
            val decryptedData = runCatching {
                EncryptionHelper.decrypt(encryptedData, DEFAULT_PASSWORD)
            }.onFailure {
                Log.e(TAG, "❌ خطا در رمزگشایی: ${it.message}")
                Log.e(TAG, "دانلود شده (پیش‌نمایش): ${encryptedData.take(120)}")
            }.getOrElse { e ->
                return@withContext Result.failure(e)
            }

            if (decryptedData.isBlank()) {
                Log.e(TAG, "❌ فایل رمزگشایی شده خالی است")
                return@withContext Result.failure(Exception("رمزگشایی ناموفق بود (خروجی خالی)"))
            }

            Log.d(TAG, "📝 محتوای رمزگشایی شده:")
            decryptedData.lines().forEach { line ->
                Log.d(TAG, "  > $line")
            }

            // 3) پارس و نرمال‌سازی
            val parsed = parseAPIKeys(decryptedData)
            if (parsed.isEmpty()) {
                Log.w(TAG, "⚠️ هیچ کلید معتبری یافت نشد")
                return@withContext Result.failure(Exception("هیچ کلید معتبری در فایل یافت نشد"))
            }

            val processedKeys = parsed.map { key ->
                val inferredProvider = key.provider
                val defaultBase = when {
                    inferredProvider == AIProvider.LIARA -> "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1"
                    inferredProvider == AIProvider.AIML -> "https://api.aimlapi.com/v1"
                    inferredProvider == AIProvider.OPENROUTER && key.key.startsWith("hf_") ->
                        "https://router.huggingface.co/models/openai/whisper-large-v3"
                    inferredProvider == AIProvider.OPENROUTER -> "https://openrouter.ai/api/v1"
                    inferredProvider == AIProvider.OPENAI -> "https://api.openai.com/v1"
                    else -> key.baseUrl
                }
                key.copy(
                    isActive = true,
                    baseUrl = key.baseUrl ?: defaultBase
                )
            }

            Log.d(TAG, "✅ تعداد کلیدهای پارس شده: ${processedKeys.size}")
            processedKeys.forEach { key ->
                Log.d(TAG, "  - ${key.provider.name}: ${key.key.take(10)}... base=${key.baseUrl}")
            }

            // 4) ذخیره و فعال‌سازی
            val prefsManager = PreferencesManager(context)
            prefsManager.saveAPIKeys(processedKeys)
            // اجباری آنلاین
            prefsManager.setWorkingMode(PreferencesManager.WorkingMode.ONLINE)
            Log.d(TAG, "✅ ${processedKeys.size} کلید در prefs ذخیره و فعال شد")

            Result.success(processedKeys)
        } catch (e: Exception) {
            Log.e(TAG, "خطای بارگذاری: ${e.message}", e)
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
            
            val (provider, key, baseUrl) = parseKeyLine(trimmed)
            if (provider != null && key.isNotBlank()) {
                keys.add(
                    APIKey(
                        provider = provider,
                        key = key,
                        baseUrl = baseUrl,
                        isActive = false // شروع غیرفعال، بعداً فعال می‌شود
                    )
                )
                Log.d(TAG, "✓ پارس: ${provider.name}")
            } else {
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
        
        // Case 1: explicit provider:key(:baseUrl) ONLY if provider token is recognized
        if (parts.size >= 2) {
            val provider = when (parts[0].lowercase()) {
                "liara" -> AIProvider.LIARA
                "openai", "gpt" -> AIProvider.OPENAI
                "anthropic", "claude" -> AIProvider.ANTHROPIC
                "openrouter" -> AIProvider.OPENROUTER
                "aiml", "aimlapi" -> AIProvider.AIML
                "huggingface", "hf" -> AIProvider.OPENROUTER
                else -> null
            }
            if (provider != null) {
                val key = parts.getOrNull(1) ?: ""
                val baseUrl = parts.getOrNull(2)
                return Triple(provider, key, baseUrl)
            }
        }

        // Case 2: raw key with no provider prefix (or unrecognized first token) -> infer by pattern
        val inferredProvider = inferProviderFromRawKey(line)
        return Triple(inferredProvider, line, null)
    }

    /**
     * Heuristic provider detection for raw keys (no prefix in file)
     * Priority: OpenRouter (sk-or), Liara (JWT-like), OpenAI (sk- or project), otherwise null.
     */
    private fun inferProviderFromRawKey(raw: String): AIProvider? {
        val trimmed = raw.trim()
        val lower = trimmed.lowercase()

        // OpenRouter keys start with sk-or
        if (lower.startsWith("sk-or")) return AIProvider.OPENROUTER

        // AIML API keys sometimes start with aiml_ or sk-aiml
        if (lower.startsWith("aiml") || lower.startsWith("sk-aiml")) return AIProvider.AIML

        // Liara keys in gist are JWT-like tokens starting with eyJ...
        if (trimmed.startsWith("eyJ")) return AIProvider.LIARA

        // HuggingFace tokens start with hf_
        if (lower.startsWith("hf_")) return AIProvider.OPENROUTER

        // OpenAI (and some project keys) start with sk- or sk-proj-
        if (lower.startsWith("sk-")) return AIProvider.OPENAI

        // Google-style API keys (AIza...) -> treat as OpenAI-compatible for now
        if (trimmed.startsWith("AIza")) return AIProvider.OPENAI

        // Hex-only 32-char keys -> treat as OpenAI to avoid dropping
        if (trimmed.matches(Regex("^[a-fA-F0-9]{32}\$"))) return AIProvider.OPENAI

        return null
    }
    
    /**
     * تست کلیدها
     */
    private fun getFreeFallbackKeys(): List<APIKey> {
        Log.d(TAG, "📡 بارگذاری free keys fallback...")
        val freeKeys = mutableListOf<APIKey>()
        
        // OpenRouter - دارای مدل‌های رایگان بسیار خوب (Gemini Nano، Llama 3.2، و غیره)
        // ⚠️ اگر key blank است، OpenRouter free endpoints بدون auth کار می‌کند
        freeKeys.add(APIKey(
            provider = AIProvider.OPENROUTER,
            key = "sk-or-free",  // OpenRouter free public key
            baseUrl = "https://openrouter.ai/api/v1",
            isActive = true
        ))
        
        // Free OpenAI endpoints (اگر تریل دسترس داشته باشید)
        // Note: این کلیدها عمومی هستند و ممکن است rate-limited باشند
        freeKeys.add(APIKey(
            provider = AIProvider.OPENAI,
            key = "sk-proj-free",  // OpenAI free trial key (اگر فعال باشد)
            baseUrl = "https://api.openai.com/v1",
            isActive = true
        ))
        
        // AIML API free tier
        freeKeys.add(APIKey(
            provider = AIProvider.AIML,
            key = "free-aiml-fallback",
            baseUrl = null,
            isActive = true
        ))
        
        Log.d(TAG, "✅ ${freeKeys.size} free fallback keys loaded (OpenRouter first priority)")
        freeKeys.forEach { key ->
            Log.d(TAG, "  - ${key.provider.name}: ${key.baseUrl ?: "default"}")
        }
        
        return freeKeys
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