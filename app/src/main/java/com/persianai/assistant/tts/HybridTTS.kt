package com.persianai.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.persianai.assistant.integration.IviraIntegrationManager
import com.persianai.assistant.utils.IviraProcessingHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

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
                    var result = googleTTS?.setLanguage(Locale("fa", "IR"))
                    
                    if (result == TextToSpeech.LANG_MISSING_DATA || 
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        result = googleTTS?.setLanguage(Locale.ENGLISH)
                    }
                    
                    isReady = result != TextToSpeech.LANG_MISSING_DATA && 
                             result != TextToSpeech.LANG_NOT_SUPPORTED
                    
                    if (isReady) {
                        googleTTS?.setPitch(1.0f)
                        googleTTS?.setSpeechRate(0.9f)
                        Log.d(TAG, "TTS Ready")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }
    
    fun speak(
        text: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        Log.d(TAG, "Speaking")
        
        scope.launch {
            try {
                val audioBytes = IviraProcessingHelper.processTTSWithIviraPriority(
                    context = context,
                    text = text,
                    onResult = { onSuccess?.invoke() },
                    onError = { fallbackToGoogleTTS(text, onSuccess, onError) }
                )
                
                if (audioBytes != null && audioBytes.isNotEmpty()) {
                    onSuccess?.invoke()
                } else {
                    fallbackToGoogleTTS(text, onSuccess, onError)
                }
            } catch (e: Exception) {
                fallbackToGoogleTTS(text, onSuccess, onError)
            }
        }
    }
    
    private fun fallbackToGoogleTTS(
        text: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            if (!isReady || googleTTS == null) {
                onError?.invoke("TTS not ready")
                return
            }
            
            googleTTS?.speak(text, TextToSpeech.QUEUE_ADD, null)
            onSuccess?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            onError?.invoke("TTS error")
        }
    }
    
    fun getStatus(): String {
        return when {
            !isReady && googleTTS == null -> "Not available"
            !isReady -> "Incomplete"
            else -> "Ready"
        }
    }
    
    fun shutdown() {
        try {
            googleTTS?.stop()
            googleTTS?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }
}