package com.persianai.assistant.core

import android.content.Context
import android.util.Log
import com.persianai.assistant.ai.AIClient
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.ai.SimpleOfflineResponder
import com.persianai.assistant.api.IviraAPIClient
import com.persianai.assistant.utils.IviraTokenManager
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.offline.LocalLlamaRunner
import com.persianai.assistant.utils.ModelDownloadManager
import com.persianai.assistant.utils.PreferencesManager
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Query Router مرکزی
 * 1) اگر اکشن قابل اجرا باشد آن را انجام می‌دهد
 * 2) در حالت HYBRID/ONLINE اول آنلاین را امتحان می‌کند
 * 3) در صورت شکست یا حالت OFFLINE به آفلاین برمی‌گردد
 */
class QueryRouter(private val context: Context) {

    private val TAG = "QueryRouter"
    private val actionExecutor = ActionExecutor(context)
    private val prefs = PreferencesManager(context)
    private val offlineAssistant = AdvancedPersianAssistant(context)
    private val modelDownloadManager = ModelDownloadManager(context)
    private val localLlama = LocalLlamaRunner()
    private val iviraClient = IviraAPIClient(context)
    private val iviraTokenManager = IviraTokenManager(context)

    suspend fun routeQuery(query: String): QueryResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Routing query: $query")
            val workingMode = prefs.getWorkingMode()
            val apiKeys = prefs.getAPIKeys()
            val activeKeys = apiKeys.filter { it.isActive && !it.key.isNullOrBlank() }

            // 1) اقدام مستقیم (Reminder/Alarm/Note)
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

            // 2) تلاش آنلاین Ivira (token-based) پیش از کلیدهای سنتی
            val iviraResult = tryIviraOnline(query)
            if (iviraResult != null) return@withContext iviraResult

            // 3) حالت آفلاین یا بدون کلید → اول تلاش با مدل آفلاین GGUF (در صورت موجود) سپس پاسخ ساده
            if (workingMode == PreferencesManager.WorkingMode.OFFLINE || activeKeys.isEmpty()) {
                val local = tryLocalModel(query)
                if (local != null) {
                    return@withContext QueryResult(
                        success = true,
                        response = local,
                        source = "offline",
                        actionExecuted = false,
                        model = "local-gguf"
                    )
                }
                val offline = offlineAssistant.processRequest(query)
                val response = resolveOfflineResponse(query, offline, workingMode)
                return@withContext QueryResult(
                    success = true,
                    response = response,
                    source = "offline",
                    actionExecuted = false,
                    model = "offline-assistant"
                )
            }

            // 4) تلاش آنلاین (online-first برای HYBRID/ONLINE)
            val onlineResult = tryOnlineModel(query, activeKeys)
            if (onlineResult != null) {
                return@withContext QueryResult(
                    success = true,
                    response = onlineResult.response,
                    source = "online",
                    actionExecuted = false,
                    model = onlineResult.model
                )
            }

            // 5) بازگشت به آفلاین در صورت شکست آنلاین: ابتدا GGUF محلی سپس پاسخ ساده
            Log.w(TAG, "❌ Online model failed, falling back to offline assistant")
            val local = tryLocalModel(query)
            if (local != null) {
                return@withContext QueryResult(
                    success = true,
                    response = local,
                    source = "offline",
                    actionExecuted = false,
                    model = "local-gguf"
                )
            }
            val offline = offlineAssistant.processRequest(query)
            val response = resolveOfflineResponse(query, offline, workingMode)
            return@withContext QueryResult(
                success = true,
                response = response,
                source = "offline",
                actionExecuted = false,
                model = "offline-assistant"
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

    private suspend fun tryOnlineModel(query: String, activeKeys: List<APIKey>): OnlineResult? {
        return try {
            if (activeKeys.isEmpty()) {
                Log.w(TAG, "⚠️ No active API keys")
                return null
            }

            val aiClient = AIClient(activeKeys)
            val messages = listOf(ChatMessage(role = MessageRole.USER, content = query))

            val activeProviders = activeKeys.map { it.provider }.toSet()
            val candidates = AIModel.values()
                .filter { activeProviders.contains(it.provider) && it.provider != AIProvider.LOCAL }
                .ifEmpty { listOf(AIModel.getDefaultModel()) }

            // FIX: Limit retries to 1 per model to speed up fallback (was trying all providers, causing 30+ sec delays)
            val maxRetries = 1
            var retryCount = 0
            
            for (model in candidates) {
                if (retryCount >= maxRetries) {
                    Log.w(TAG, "⚠️ Max retries reached ($maxRetries), falling back to offline")
                    break
                }
                
                try {
                    Log.d(TAG, "🌐 Trying online model: ${model.displayName}")
                    val response = aiClient.sendMessage(model, messages)
                    Log.d(TAG, "✅ Got online response from ${model.displayName}")
                    return OnlineResult(response.content, model.displayName)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ ${model.displayName} failed: ${e.message}")
                    retryCount++
                }
            }

            Log.e(TAG, "❌ All online providers failed (retries exhausted), falling back to offline")
            null
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Online model failed: ${e.message}")
            null
        }
    }

    private fun resolveOfflineResponse(
        query: String,
        offline: AdvancedPersianAssistant.AssistantResponse,
        workingMode: PreferencesManager.WorkingMode
    ): String {
        // FIX: Better fallback chain - try AdvancedPersianAssistant first, then SimpleOfflineResponder
        
        // 1. Check if AdvancedPersianAssistant returned a good response
        if (!offline.text.isBlank() && offline.actionType != AdvancedPersianAssistant.ActionType.NEEDS_AI) {
            Log.d(TAG, "✅ Advanced offline assistant provided response")
            return offline.text
        }
        
        // 2. Fall back to SimpleOfflineResponder for generic responses
        val simpleResponse = SimpleOfflineResponder.respond(context, query)
        if (!simpleResponse.isNullOrBlank()) {
            Log.d(TAG, "✅ Simple offline responder provided fallback response")
            return simpleResponse
        }
        
        // 3. If offline mode and no response available, inform user
        if (workingMode == PreferencesManager.WorkingMode.OFFLINE) {
            return "⚠️ این درخواست نیاز به مدل آنلاین دارد و در حالت آفلاین قابل پاسخ نیست."
        }
        
        // 4. Default offline error message
        return "⚠️ فعلاً پاسخ آفلاین در دسترس نیست."
    }
}

data class QueryResult(
    val success: Boolean,
    val response: String,
    val source: String, // "action", "online", "offline", "none", "error"
    val actionExecuted: Boolean = false,
    val model: String? = null,
    val exception: Exception? = null
)

data class OnlineResult(
    val response: String,
    val model: String
)
