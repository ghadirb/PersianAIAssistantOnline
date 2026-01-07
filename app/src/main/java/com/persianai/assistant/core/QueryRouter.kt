package com.persianai.assistant.core

import android.content.Context
import android.util.Log
import com.persianai.assistant.ai.AIClient
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.AIProvider
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
            
            // Step 2: Try Online Model (only path)
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
            
            // Step 3: All failed (no offline fallback)
            Log.w(TAG, "❌ Online model failed")
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
            val apiKeys = prefs.getAPIKeys()
            val activeKeys = apiKeys.filter { it.isActive && !it.key.isNullOrBlank() }
            
            if (activeKeys.isEmpty()) {
                Log.w(TAG, "⚠️ No active API keys")
                return null
            }
            
            Log.d(TAG, "📊 Available providers: ${activeKeys.map { it.provider.name }}")

            // اولویت: OpenAI GPT-4o-mini سپس Avalai Gemini-2.5-Flash
            val candidates = mutableListOf<AIModel>()
            if (activeKeys.any { it.provider == AIProvider.OPENAI }) {
                candidates.add(AIModel.GPT_4O_MINI)
            }
            if (activeKeys.any { it.provider == AIProvider.AVALAI }) {
                candidates.add(AIModel.AVALAI_GEMINI_FLASH)
            }
            if (candidates.isEmpty()) {
                Log.w(TAG, "⚠️ No OpenAI/Avalai key active; skipping online chat")
                return null
            }

            val aiClient = AIClient(activeKeys)
            val messages = listOf(ChatMessage(role = MessageRole.USER, content = query))

            for (model in candidates) {
                try {
                    Log.d(TAG, "🌐 Trying online model: ${model.displayName}")
                    val response = aiClient.sendMessage(model, messages)
                    Log.d(TAG, "✅ Got online response from ${model.displayName}")
                    return OnlineResult(response.content, model.displayName)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ ${model.displayName} failed: ${e.message}")
                    continue
                }
            }

            Log.e(TAG, "❌ All online providers failed in fallback chain")
            null
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Online model failed: ${e.message}")
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
