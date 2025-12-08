package com.persianai.assistant.ai

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.MainThread
import com.persianai.assistant.models.ChatMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * WebView bridge برای Puter.js (درون برنامه).
 * برای کار واقعی، کاربر باید در WebView احراز هویت کند (Puter login).
 * اگر پاسخ یا احراز هویت شکست بخورد، null برمی‌گردد تا fallback فعال شود.
 */
object PuterBridge {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false
    private var webView: WebView? = null
    private val readySignal = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    @Volatile private var sdkReady: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    @MainThread
    private fun initWebView(context: Context) {
        if (initialized) return
        webView = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            addJavascriptInterface(object {
                @JavascriptInterface
                fun ready() {
                    Log.d("PuterBridge", "WebView ready")
                    sdkReady = true
                    if (!readySignal.isCompleted) {
                        readySignal.complete(true)
                    }
                }

                @JavascriptInterface
                fun onResponse(payload: String?) {
                    try {
                        val obj = JSONObject(payload ?: "{}")
                        val id = obj.optString("id")
                        val text = obj.optString("text", null)
                        // اگر خطا بود، خروجی را null می‌فرستیم تا fallback فعال شود
                        pending[id]?.complete(text)
                        pending.remove(id)
                    } catch (e: Exception) {
                        Log.w("PuterBridge", "Parse error: ${e.message}")
                    }
                }
            }, "AndroidPuterBridge")
            loadUrl("file:///android_asset/puter_bridge.html")
        }
        initialized = true
    }

    fun init(context: Context) {
        mainHandler.post { initWebView(context) }
    }

    fun isReady(): Boolean {
        return sdkReady && readySignal.isCompleted
    }

    /**
     * @return پاسخ متنی Puter یا null در صورت خطا/تایم‌اوت.
     */
    suspend fun chat(userText: String, history: List<ChatMessage>): String? {
        ensureInitialized()
        // منتظر آماده‌شدن WebView
        if (!readySignal.isCompleted) {
            val ready = withTimeoutOrNull(8000) { readySignal.await() } ?: return null
            if (!ready) return null
        }

        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String?>()
        pending[requestId] = deferred

        val historyText = history.joinToString("\n") { msg ->
            "${msg.role.name.lowercase()}: ${msg.content}"
        }
        val payload = JSONObject().apply {
            put("id", requestId)
            put("prompt", userText)
            put("history", historyText)
        }.toString()

        withContext(Dispatchers.Main) {
            webView?.evaluateJavascript("window.onAndroidMessage(${JSONObject.quote(payload)});", null)
        }

        return withTimeoutOrNull(15000) { deferred.await() }
    }

    private suspend fun ensureInitialized() {
        if (!initialized) {
            withContext(Dispatchers.Main) {
                webView ?: initWebView(appContext ?: return@withContext)
            }
        }
    }

    // Context نگهداری‌شده برای init در صورت call از thread دیگر
    @Volatile
    private var appContext: Context? = null

    fun setContext(context: Context) {
        appContext = context.applicationContext
        if (!initialized) {
            init(context)
        }
    }
}
