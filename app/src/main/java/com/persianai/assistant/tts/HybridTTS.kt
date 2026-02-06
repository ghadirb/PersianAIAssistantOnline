package com.persianai.assistant.tts
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.persianai.assistant.integration.IviraIntegrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * Hybrid TTS Engine
 * 
 * اولویت:
 * 1. Ivira TTS (Avangardi → Awasho)
 * 2. Google TTS (آنلاین/آفلاین)
 * 3. System TTS (Fallback)
 */
class HybridTTS(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private var googleTTS: TextToSpeech? = null
    private val iviraManager = IviraIntegrationManager(context)
    
    var isReady = false
        private set
    
    companion object {
        private const val TAG = "HybridTTS"
    }
    
    init {
        initGoogleTTS()
    }
    
    private fun initGoogleTTS() {
        try {
            googleTTS = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    // Try Persian
                    var result = googleTTS?.setLanguage(Locale("fa", "IR"))
                    
                    // Fallback to English if Persian not available
                    if (result == TextToSpeech.LANG_MISSING_DATA || 
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "⚠️ Persian not available, trying English")
                        result = googleTTS?.setLanguage(Locale.ENGLISH)
                    }
                    
                    isReady = result != TextToSpeech.LANG_MISSING_DATA && 
                             result != TextToSpeech.LANG_NOT_SUPPORTED
                    
                    if (isReady) {
                        googleTTS?.setPitch(1.0f)
                        googleTTS?.setSpeechRate(0.9f)
                        Log.d(TAG, "✅ Google TTS Ready")
                    } else {
                        Log.e(TAG, "❌ TTS Failed")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Google TTS", e)
        }
    }
    
    /**
     * تبدیل متن به صدا با اولویت Ivira
     */
    fun speak(
        text: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        Log.d(TAG, "🔊 Speaking: $text")
        
        // اولویت: Ivira TTS
        scope.launch {
            var synthesized = false
            
            // سعی برای استفاده از Ivira
            iviraManager.processWithIviraPriority(
                operation = "tts",
                input = text,
                onSuccess = { _, modelUsed ->
                    Log.d(TAG, "✅ Synthesized with $modelUsed (Ivira)")
                    synthesized = true
                    onSuccess?.invoke()
                },
                onError = { error ->
                    Log.w(TAG, "⚠️ Ivira TTS failed: $error")
                    // Fallback to Google TTS
                    if (!synthesized) {
                        fallbackToGoogleTTS(text, onSuccess, onError)
                    }
                }
            )
        }
    }
    
    /**
     * Fallback به Google TTS
     */
    private fun fallbackToGoogleTTS(
        text: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            if (!isReady || googleTTS == null) {
                Log.e(TAG, "❌ Google TTS not ready")
                onError?.invoke("سیستم TTS آماده نیست")
                return
            }
            
            Log.d(TAG, "🔄 Fallback to Google TTS: $text")
            googleTTS?.speak(text, TextToSpeech.QUEUE_ADD, null) { utteranceId ->
                Log.d(TAG, "✅ Google TTS completed")
                onSuccess?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Google TTS error: ${e.message}", e)
            onError?.invoke("خطا در تبدیل به صدا: ${e.message}")
        }
    }
    
    /**
     * دریافت وضعیت TTS
     */
    fun getStatus(): String {
        return when {
            !isReady && googleTTS == null -> "❌ سیستم TTS آماده نیست"
            !isReady -> "⚠️ سیستم TTS ناقص"
            else -> "✅ سیستم TTS آماده"
        }
    }
    
    fun shutdown() {
        try {
            googleTTS?.stop()
            googleTTS?.shutdown()
            Log.d(TAG, "🛑 TTS shutdown")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
}
