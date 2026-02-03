diff --git a/app/src/main/java/com/persianai/assistant/core/QueryRouter.kt b/app/src/main/java/com/persianai/assistant/core/QueryRouter.kt
index 79fed8b40468ae34cc7ea45d8bf0ddf3cc56b8c4..34211d296397cc11e7f852c5645b595a6a3e512f 100644
--- a/app/src/main/java/com/persianai/assistant/core/QueryRouter.kt
+++ b/app/src/main/java/com/persianai/assistant/core/QueryRouter.kt
@@ -1,107 +1,110 @@
 package com.persianai.assistant.core
 
 import android.content.Context
 import android.util.Log
 import com.persianai.assistant.ai.AIClient
 import com.persianai.assistant.ai.AdvancedPersianAssistant
+import com.persianai.assistant.ai.SimpleOfflineResponder
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
     private val offlineAssistant = AdvancedPersianAssistant(context)
     
     suspend fun routeQuery(query: String): QueryResult = withContext(Dispatchers.IO) {
         try {
             Log.d(TAG, "🚀 Routing query: $query")
             val workingMode = prefs.getWorkingMode()
             val apiKeys = prefs.getAPIKeys()
             val activeKeys = apiKeys.filter { it.isActive && !it.key.isNullOrBlank() }
             
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
             
             // Step 2: If offline-only or no keys, respond offline immediately
             if (workingMode == PreferencesManager.WorkingMode.OFFLINE || activeKeys.isEmpty()) {
                 val offline = offlineAssistant.processRequest(query)
+                val response = resolveOfflineResponse(query, offline, workingMode)
                 return@withContext QueryResult(
                     success = true,
-                    response = offline.text,
+                    response = response,
                     source = "offline",
                     actionExecuted = false,
                     model = "offline-assistant"
                 )
             }
 
             // Step 3: Try Online Model
             Log.d(TAG, "2️⃣ Checking for online model...")
             val onlineResult = tryOnlineModel(query, activeKeys)
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
 
             // Step 4: Fallback offline if online failed
             Log.w(TAG, "❌ Online model failed, falling back to offline assistant")
             val offline = offlineAssistant.processRequest(query)
+            val response = resolveOfflineResponse(query, offline, workingMode)
             return@withContext QueryResult(
                 success = true,
-                response = offline.text,
+                response = response,
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
     
     /**
      * آنلاین ماڈل کو try کریں
      */
     private suspend fun tryOnlineModel(query: String, activeKeys: List<com.persianai.assistant.models.APIKey>): OnlineResult? {
         return try {
             if (activeKeys.isEmpty()) {
                 Log.w(TAG, "⚠️ No active API keys")
@@ -126,50 +129,68 @@ class QueryRouter(private val context: Context) {
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
+
+    private fun resolveOfflineResponse(
+        query: String,
+        offline: AdvancedPersianAssistant.AssistantResponse,
+        workingMode: PreferencesManager.WorkingMode
+    ): String {
+        if (offline.actionType == AdvancedPersianAssistant.ActionType.NEEDS_AI) {
+            val simple = SimpleOfflineResponder.respond(context, query)
+            if (!simple.isNullOrBlank()) return simple
+            if (workingMode == PreferencesManager.WorkingMode.OFFLINE) {
+                return "⚠️ این درخواست نیاز به مدل آنلاین دارد و در حالت آفلاین قابل پاسخ نیست."
+            }
+        }
+        return offline.text.ifBlank {
+            SimpleOfflineResponder.respond(context, query)
+                ?: "⚠️ فعلاً پاسخ آفلاین در دسترس نیست."
+        }
+    }
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
