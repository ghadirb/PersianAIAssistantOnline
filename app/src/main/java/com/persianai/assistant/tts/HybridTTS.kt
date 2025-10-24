package com.persianai.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

/**
 * Hybrid TTS Engine
 * - Google TTS (online/offline)
 * - Sherpa-ONNX (offline) - coming soon
 */
class HybridTTS(private val context: Context) {
    private var googleTTS: TextToSpeech? = null
    // private var offlineTTS: OfflineTts? = null // TODO: Add when Sherpa works
    
    var isReady = false
        private set
    
    companion object {
        private const val TAG = "HybridTTS"
    }
    
    init {
        initGoogleTTS()
        // initSherpaONNX() // TODO: Enable when dependency works
    }
    
    private fun initGoogleTTS() {
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
    }
    
    fun speak(text: String) {
        if (!isReady) {
            Log.w(TAG, "⚠️ TTS not ready")
            return
        }
        
        // Use Google TTS
        googleTTS?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        Log.d(TAG, "🔊 Speaking: $text")
    }
    
    fun shutdown() {
        googleTTS?.stop()
        googleTTS?.shutdown()
        Log.d(TAG, "🛑 TTS shutdown")
    }
}
