 (cd "$(git rev-parse --show-toplevel)" && git apply --3way <<'EOF' 
diff --git a/app/src/main/java/com/persianai/assistant/integration/IviraIntegrationManager.kt b/app/src/main/java/com/persianai/assistant/integration/IviraIntegrationManager.kt
index b45f1589c336cf4239a14afaf6ae4714794cf880..4a405dba82282e66cf863710a8a10069054d6e47 100644
--- a/app/src/main/java/com/persianai/assistant/integration/IviraIntegrationManager.kt
+++ b/app/src/main/java/com/persianai/assistant/integration/IviraIntegrationManager.kt
@@ -188,38 +188,45 @@ class IviraIntegrationManager(private val context: Context) {
                     resultReceived = false
                 }
             )
             
             resultReceived
         } catch (e: Exception) {
             Log.e(TAG, "❌ Error in STT", e)
             onError("خطا: ${e.message}")
             false
         }
     }
     
     /**
      * بررسی آیا Ivira موجود و آماده است
      */
     fun isIviraReady(): Boolean {
         return tokenManager.hasTokens() && apiClient.hasTokens()
     }
     
     /**
      * دریافت اطلاعات توکن‌های موجود
      */
     fun getAvailableTokensInfo(): Map<String, Boolean> {
         return apiClient.getAvailableTokensInfo()
     }
+
+    /**
+     * دریافت توکن‌های ذخیره‌شده برای بررسی وضعیت
+     */
+    fun getIviraTokens(): Map<String, String> {
+        return tokenManager.getAllTokens()
+    }
     
     /**
      * خاموش کردن و تمیز کردن
      */
     fun shutdown() {
         try {
             Log.d(TAG, "Shutting down Ivira Integration Manager")
             // در آینده می‌توان اضافه کرد
         } catch (e: Exception) {
             Log.e(TAG, "Error during shutdown", e)
         }
     }
 }
 
EOF
)