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
                // تلاش برای تنظیم زبان فارسی
                var result = tts?.setLanguage(Locale("fa", "IR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // تلاش با فقط کد زبان
                    result = tts?.setLanguage(Locale("fa"))
                }
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // تلاش با Persian
                    result = tts?.setLanguage(Locale("per"))
                }
                
                isReady = result != TextToSpeech.LANG_MISSING_DATA && 
                         result != TextToSpeech.LANG_NOT_SUPPORTED
                
                if (isReady) {
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(0.9f)
                    Log.d("PersianVoice", "✅ TTS Persian Ready")
                } else {
                    Log.e("PersianVoice", "❌ TTS Persian NOT available")
                }
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
