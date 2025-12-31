package com.persianai.assistant.core

import android.content.Context
import android.util.Log
import com.persianai.assistant.ai.AIClient
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مرکزی Query Router
 * ہر query کو صحیح جگہ روٹ کرتا ہے:
 * 1. Action Executor (اگر pattern match ہو)
 * 2. Online Model (اگر آنلاین دستیاب ہو)
 * 3. Offline Model (fallback)
 */
class QueryRouter(private val context: Context) {
    
    private val TAG = "QueryRouter"
    private val actionExecutor = ActionExecutor(context)
    private val intentController = AIIntentController(context)
    private val prefs = PreferencesManager(context)
    
    suspend fun routeQuery(query: String): QueryResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Routing query: $query")
            
            // Step 1: Try Action Execution
            Log.d(TAG, "1️⃣ Checking for executable actions...")
            val actionResult = actionExecutor.executeFromQuery(query)
            if (actionResult.success && actionResult.action != null) {
                Log.d(TAG, "✅ Action executed: ${actionResult.action}")
                return@withContext QueryResult(
                    success = true,
                    response = actionResult.message,
                    source = "action",
                    actionExecuted = true,
                    model = null
                )
            }
            
            // Step 2: Try Online Model (if available)
            Log.d(TAG, "2️⃣ Checking for online model...")
            val onlineResult = tryOnlineModel(query)
            if (onlineResult != null) {
                Log.d(TAG, "✅ Online model responded: ${onlineResult.model}")
                return@withContext QueryResult(
                    success = true,
                    response = onlineResult.response,
                    source = "online",
                    actionExecuted = false,
                    model = onlineResult.model
                )
            }
            
            // Step 3: Fallback to Offline Model
            Log.d(TAG, "3️⃣ Falling back to offline model...")
            val offlineResult = tryOfflineModel(query)
            if (offlineResult != null) {
                Log.d(TAG, "✅ Offline model responded")
                return@withContext QueryResult(
                    success = true,
                    response = offlineResult.response,
                    source = "offline",
                    actionExecuted = false,
                    model = offlineResult.model
                )
            }
            
            // Step 4: All failed
            Log.w(TAG, "❌ All methods failed")
            return@withContext QueryResult(
                success = false,
                response = "معافی چاہتا ہوں، کوئی بھی طریقہ کام نہ کر سکا۔ براہ مہربانی دوبارہ کوشش کریں۔",
                source = "none",
                actionExecuted = false,
                model = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Router error: ${e.message}", e)
            QueryResult(
                success = false,
                response = "خطا: ${e.message}",
                source = "error",
                actionExecuted = false,
                model = null,
                exception = e
            )
        }
    }
    
    /**
     * آنلاین ماڈل کو try کریں
     */
    private suspend fun tryOnlineModel(query: String): OnlineResult? {
        return try {
            val workingMode = prefs.getWorkingMode()
            if (workingMode == PreferencesManager.WorkingMode.OFFLINE) {
                Log.d(TAG, "⏭️ Offline mode - skipping online")
                return null
            }
            
            val apiKeys = prefs.getAPIKeys()
            val activeKeys = apiKeys.filter { it.isActive }
            
            if (activeKeys.isEmpty()) {
                Log.w(TAG, "⚠️ No active API keys")
                return null
            }
            
            // Find best model
            val model = when {
                activeKeys.any { it.provider.name == "LIARA" } -> AIModel.GPT_4O_MINI
                activeKeys.any { it.provider.name == "OPENROUTER" } -> AIModel.QWEN_2_5_1B5
                activeKeys.any { it.provider.name == "OPENAI" } -> AIModel.GPT_35_TURBO
                else -> AIModel.QWEN_2_5_1B5
            }
            
            Log.d(TAG, "🌐 Trying online model: ${model.name}")
            
            val aiClient = AIClient(apiKeys)
            val messages = listOf(ChatMessage(role = MessageRole.USER, content = query))
            val response = aiClient.sendMessage(model, messages)
            
            Log.d(TAG, "✅ Got online response")
            OnlineResult(response.content, model.displayName)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Online model failed: ${e.message}")
            null
        }
    }
    
    /**
     * آفلاین ماڈل کو try کریں
     */
    private suspend fun tryOfflineModel(query: String): OfflineResult? {
        return try {
            Log.d(TAG, "📱 Trying offline model...")
            
            val response = com.persianai.assistant.offline.LocalLlamaRunner.infer(
                findOfflineModelPath() ?: return null,
                buildOfflinePrompt(query),
                maxTokens = 200
            )
            
            if (response.isNullOrBlank()) {
                Log.w(TAG, "⚠️ Offline model returned empty")
                return null
            }
            
            Log.d(TAG, "✅ Got offline response")
            OfflineResult(response.trim(), "TinyLlama")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Offline model failed: ${e.message}")
            null
        }
    }
    
    /**
     * آفلاین ماڈل کے لیے prompt بنائیں
     */
    private fun buildOfflinePrompt(query: String): String {
        return buildString {
            appendLine("آپ ایک ذہین دستیار ہو۔ صارف کے سوال کا جواب دیں۔")
            appendLine("جواب مختصر اور واضح ہونا چاہیے۔")
            appendLine()
            appendLine("صارف: $query")
            appendLine("دستیار:")
        }
    }
    
    /**
     * آفلاین ماڈل کا راستہ تلاش کریں
     */
    private fun findOfflineModelPath(): String? {
        return try {
            val manager = com.persianai.assistant.models.OfflineModelManager(context)
            val downloaded = manager.getDownloadedModels()
            downloaded.firstOrNull()?.second
        } catch (e: Exception) {
            Log.w(TAG, "Failed to find offline model: ${e.message}")
            null
        }
    }
}

/**
 * Query کا نتیجہ
 */
data class QueryResult(
    val success: Boolean,
    val response: String,
    val source: String, // "action", "online", "offline", "none", "error"
    val actionExecuted: Boolean = false,
    val model: String? = null,
    val exception: Exception? = null
)

/**
 * آنلاین ماڈل کا نتیجہ
 */
data class OnlineResult(
    val response: String,
    val model: String
)

/**
 * آفلاین ماڈل کا نتیجہ
 */
data class OfflineResult(
    val response: String,
    val model: String
)
