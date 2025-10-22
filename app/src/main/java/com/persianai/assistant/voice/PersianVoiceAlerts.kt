package com.persianai.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class PersianVoiceAlerts(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isReady = false
    
    init {
        initTTS()
    }
    
    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("fa", "IR"))
                isReady = result != TextToSpeech.LANG_MISSING_DATA && 
                         result != TextToSpeech.LANG_NOT_SUPPORTED
                Log.d("PersianVoice", "TTS Ready: $isReady")
            }
        }
    }
    
    fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    // هشدارهای مسیریابی
    fun turnRight(distance: Int) {
        speak("$distance متر دیگر به راست بپیچید")
    }
    
    fun turnLeft(distance: Int) {
        speak("$distance متر دیگر به چپ بپیچید")
    }
    
    fun goStraight(distance: Int) {
        speak("$distance متر دیگر مستقیم بروید")
    }
    
    // هشدار سرعت
    fun speedLimit(limit: Int) {
        speak("سرعت مجاز $limit کیلومتر در ساعت است")
    }
    
    fun speedWarning(currentSpeed: Int, limit: Int) {
        speak("سرعت شما $currentSpeed است. سرعت مجاز $limit")
    }
    
    // هشدار دوربین
    fun cameraAhead(distance: Int) {
        speak("دوربین در $distance متر جلوتر")
    }
    
    // هشدار ترافیک
    fun heavyTraffic() {
        speak("ترافیک سنگین در جلو")
    }
    
    fun trafficClear() {
        speak("مسیر باز است")
    }
    
    // هشدار توقف
    fun stopSign() {
        speak("علامت توقف در جلو")
    }
    
    // اعلام رسیدن
    fun arrived() {
        speak("به مقصد رسیدید")
    }
    
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
