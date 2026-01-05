package com.persianai.assistant.config

import android.content.Context
import android.util.Log
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.utils.PreferencesManager

/**
 * ✅ API Keys کی تشکیل اور initialization
 * 
 * یہ configuration صرف دستی اضافے کے لیے ہے۔
 * براہ کرم اپنی keys یہاں ڈالیں:
 */
object APIKeyConfig {
    
    private const val TAG = "APIKeyConfig"
    
    /**
     * ✅ پہلے سے موجود keys (براہ کرم update کریں):
     * 
     * مثال:
     * APIKey(
     *     key = "sk-proj-YOUR_ACTUAL_OPENAI_KEY_HERE",
     *     provider = AIProvider.OPENAI,
     *     baseUrl = "https://api.openai.com/v1",
     *     isActive = true
     * )
     */
    fun getInitialAPIKeys(): List<APIKey> {
        return listOf(
            // ✅ OpenAI (REQUIRED - سب سے اہم)
            APIKey(
                provider = AIProvider.OPENAI,
                key = "",  // ⚠️ یہاں اپنی OpenAI key ڈالیں (sk-proj-...)
                baseUrl = "https://api.openai.com/v1",
                isActive = true  // ✅ فعال کریں
            ),
            
            // ✅ AIML (Optional - fallback)
            /*
            APIKey(
                provider = AIProvider.AIML,
                key = "YOUR_AIML_API_KEY",  // اگر موجود ہے تو
                baseUrl = "https://api.aimlapi.com",
                isActive = false  // اگر test نہ کرنا ہے تو false
            ),
            */
            
            // ❌ OpenRouter - DISABLED (insufficient credits)
            /*
            APIKey(
                provider = AIProvider.OPENROUTER,
                key = "sk-or-v1-...",
                baseUrl = "https://openrouter.ai/api/v1",
                isActive = false  // ❌ Insufficient credits
            ),
            */
            
            // ❌ Gladia - DISABLED (403 forbidden)
            /*
            APIKey(
                provider = AIProvider.GLADIA,
                key = "gladia-key",
                baseUrl = "https://api.gladia.io",
                isActive = false  // ❌ Invalid key
            ),
            */
            
            // ✅ Liara (Optional - voice features)
            /*
            APIKey(
                provider = AIProvider.LIARA,
                key = "YOUR_LIARA_API_KEY",
                baseUrl = "https://ai.liara.ir/api/...",
                isActive = false  // اگر test نہ کرنا
            ),
            */
        )
    }
    
    /**
     * ✅ Initialization - یہ app شروع میں چل۔
     */
    fun initializeKeys(context: Context) {
        try {
            Log.d(TAG, "🔄 Initializing API Keys...")
            
            val prefs = PreferencesManager(context)
            val existingKeys = prefs.getAPIKeys()
            
            if (existingKeys.isEmpty()) {
                Log.d(TAG, "📝 No existing keys found, setting up defaults...")
                val initialKeys = getInitialAPIKeys()
                    .filter { !it.key.isNullOrBlank() }  // صرف non-empty keys
                
                if (initialKeys.isEmpty()) {
                    Log.w(TAG, "⚠️ No valid keys to initialize - user must add keys manually")
                    return
                }
                
                prefs.saveAPIKeys(initialKeys)
                Log.d(TAG, "✅ Initial keys saved: ${initialKeys.size}")
                
                initialKeys.forEach { key ->
                    Log.d(TAG, "   - ${key.provider.name}: ${if (key.isActive) "✔ ACTIVE" else "✕"}")
                }
            } else {
                Log.d(TAG, "✅ Existing keys found: ${existingKeys.size}")
                existingKeys.forEach { key ->
                    Log.d(TAG, "   - ${key.provider.name}: ${if (key.isActive) "✔ ACTIVE" else "✕"} (${key.key.take(8)}...)")
                }
            }
            
            // Validate keys
            validateKeys(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing keys", e)
        }
    }
    
    /**
     * ✅ Keys کی تصدیق - کون سی keys working ہیں
     */
    private fun validateKeys(context: Context) {
        try {
            val prefs = PreferencesManager(context)
            val keys = prefs.getAPIKeys()
            
            Log.d(TAG, "📊 API Key Validation:")
            
            val activeProviders = keys.filter { it.isActive }
                .map { it.provider.name }
                .distinct()
            
            Log.d(TAG, "   Active providers: ${activeProviders.joinToString(", ")}")
            
            val openaiKey = keys.find { it.provider == AIProvider.OPENAI && it.isActive }
            if (openaiKey != null) {
                Log.d(TAG, "   ✅ OpenAI: Available")
            } else {
                Log.w(TAG, "   ⚠️ OpenAI: NOT SET (app may not work)")
            }
            
            // Warning اگر کوئی بھی key نہیں
            if (keys.isEmpty()) {
                Log.e(TAG, "   ❌ ERROR: No API keys configured!")
                Log.e(TAG, "   💡 Please add at least one API key in Settings or APIKeyConfig.kt")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating keys", e)
        }
    }
    
    /**
     * ✅ Manual key addition (اگر user dashboard میں add نہ کر سکے)
     */
    fun addManualKey(context: Context, key: APIKey): Boolean {
        return try {
            if (key.key.isNullOrBlank()) {
                Log.w(TAG, "Cannot add key: empty key value")
                return false
            }
            
            val prefs = PreferencesManager(context)
            val existingKeys = prefs.getAPIKeys().toMutableList()
            
            // Remove duplicate provider if exists
            existingKeys.removeAll { it.provider == key.provider }
            
            // Add new key
            existingKeys.add(key)
            
            prefs.saveAPIKeys(existingKeys)
            Log.d(TAG, "✅ Manual key added: ${key.provider.name}")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding manual key", e)
            false
        }
    }
    
    /**
     * ✅ Priority order for model selection
     */
    fun getPreferredProvider(context: Context): AIProvider? {
        val prefs = PreferencesManager(context)
        val activeKeys = prefs.getAPIKeys().filter { it.isActive }
        
        // ✅ Priority:
        return when {
            activeKeys.any { it.provider == AIProvider.OPENAI } -> AIProvider.OPENAI
            activeKeys.any { it.provider == AIProvider.LIARA } -> AIProvider.LIARA
            activeKeys.any { it.provider == AIProvider.AIML } -> AIProvider.AIML
            activeKeys.any { it.provider == AIProvider.OPENROUTER } -> AIProvider.OPENROUTER
            else -> null
        }
    }
}