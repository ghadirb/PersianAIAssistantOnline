package com.persianai.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class HybridTTS(private val context: Context) {
    private var tts: TextToSpeech? = null
    var isReady = false
    
    init {
        tts = TextToSpeech(context) { 
            val result = tts?.setLanguage(Locale("fa", "IR"))
            isReady = result != TextToSpeech.LANG_MISSING_DATA
            Log.d("TTS", if (isReady) "Ready" else "Failed")
        }
    }
    
    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }
    
    fun shutdown() {
        tts?.shutdown()
    }
}
