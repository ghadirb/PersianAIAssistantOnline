 (cd "$(git rev-parse --show-toplevel)" && git apply --3way <<'EOF' 
diff --git a/app/src/main/java/com/persianai/assistant/core/QueryRouter.kt b/app/src/main/java/com/persianai/assistant/core/QueryRouter.kt
index d63a0e7ea22f3c7952b6d14d9ef1e9da423293b9..61b2726e4a1d7e43cce9738202f18cbd6a7eb9fb 100644
--- a/app/src/main/java/com/persianai/assistant/core/QueryRouter.kt
+++ b/app/src/main/java/com/persianai/assistant/core/QueryRouter.kt
@@ -202,55 +202,67 @@ class QueryRouter(private val context: Context) {
                 result = QueryResult(
                     success = true,
                     response = response,
                     source = "ivira",
                     actionExecuted = false,
                     model = "ivira-online"
                 )
             }
 
             result
         } catch (e: Exception) {
             Log.w(TAG, "⚠️ Ivira online failed: ${e.message}")
             null
         }
     }
 
     private suspend fun tryLocalModel(query: String): String? {
         return try {
             val modelDir = File(context.filesDir, "models")
             if (!modelDir.exists() || modelDir.listFiles().isNullOrEmpty()) {
                 Log.d(TAG, "ℹ️ No local models available")
                 return null
             }
 
             Log.d(TAG, "🏠 Trying local GGUF model")
-            // Try to use localLlama runner
-            val result = localLlama.run(query, modelDir.absolutePath)
-            if (!result.isNullOrBlank()) {
+            if (!localLlama.isAvailable()) {
+                Log.d(TAG, "ℹ️ Local llama backend not available")
+                return null
+            }
+
+            val modelFile = modelDir.listFiles()
+                ?.firstOrNull { it.extension.equals("gguf", ignoreCase = true) }
+                ?: run {
+                    Log.d(TAG, "ℹ️ No GGUF model file found in ${modelDir.absolutePath}")
+                    return null
+                }
+
+            val result = localLlama.infer(query, modelFile.absolutePath)
+            val response = result.getOrNull()
+            if (!response.isNullOrBlank()) {
                 Log.d(TAG, "✅ Local model response received")
-                return result
+                return response
             }
 
             null
         } catch (e: Exception) {
             Log.w(TAG, "⚠️ Local model failed: ${e.message}")
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
 
EOF
)