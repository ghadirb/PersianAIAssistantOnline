package com.persianai.assistant.navigation.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.persianai.assistant.ai.AIModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * سیستم هشدار صوتی فارسی با پشتیبانی از حالت آفلاین و آنلاین
 */
class PersianVoiceAlertSystem(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var useOnlineVoice = false
    private val aiModel = AIModelManager(context)
    
    companion object {
        private const val TAG = "PersianVoiceAlert"
    }
    
    init {
        initializeTTS()
    }
    
    /**
     * راه‌اندازی Text-to-Speech فارسی
     */
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("fa", "IR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "زبان فارسی پشتیبانی نمی‌شود، از زبان انگلیسی استفاده می‌شود")
                    tts?.language = Locale.US
                }
                isInitialized = true
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
    }
    
    /**
     * تنظیم استفاده از هشدار آنلاین با کیفیت بالا
     */
    fun setUseOnlineVoice(enabled: Boolean) {
        useOnlineVoice = enabled
    }
    
    /**
     * پخش هشدار صوتی
     */
    fun speak(text: String, priority: Int = TextToSpeech.QUEUE_ADD) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet")
            return
        }
        
        if (useOnlineVoice && aiModel.hasApiKey()) {
            // استفاده از AI برای تولید صدای با کیفیت
            speakWithAI(text)
        } else {
            // استفاده از TTS آفلاین
            tts?.speak(text, priority, null, null)
            Log.d(TAG, "Speaking (offline): $text")
        }
    }
    
    /**
     * پخش هشدار با استفاده از AI (کیفیت بالا)
     */
    private fun speakWithAI(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // TODO: Implement AI voice generation if available
                // For now, fall back to regular TTS
                tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
                Log.d(TAG, "Speaking with AI: $text")
            } catch (e: Exception) {
                Log.e(TAG, "AI voice generation failed, using offline TTS", e)
                tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
            }
        }
    }
    
    /**
     * هشدار نزدیک شدن به دوربین سرعت
     */
    fun alertSpeedCamera(distance: Int, speedLimit: Int) {
        val message = when {
            distance > 500 -> "دوربین کنترل سرعت در ${distance} متر جلوتر. سرعت مجاز $speedLimit کیلومتر"
            distance > 200 -> "توجه! دوربین کنترل سرعت در ${distance} متر جلو"
            else -> "دوربین کنترل سرعت! سرعت را کاهش دهید"
        }
        speak(message, TextToSpeech.QUEUE_FLUSH)
    }
    
    /**
     * هشدار نزدیک شدن به سرعت‌گیر
     */
    fun alertSpeedBump(distance: Int) {
        val message = when {
            distance > 300 -> "سرعت‌گیر در ${distance} متر جلوتر"
            distance > 100 -> "توجه! سرعت‌گیر در ${distance} متر جلو"
            else -> "سرعت‌گیر! سرعت را کاهش دهید"
        }
        speak(message, TextToSpeech.QUEUE_FLUSH)
    }
    
    /**
     * هشدار محدودیت سرعت جاده
     */
    fun alertSpeedLimit(newLimit: Int) {
        speak("محدودیت سرعت جدید: $newLimit کیلومتر بر ساعت")
    }
    
    /**
     * هشدار ترافیک
     */
    fun alertTraffic(level: String, delay: Int) {
        val message = when (level) {
            "روان" -> "ترافیک روان است"
            "نیمه‌سنگین" -> "ترافیک نیمه‌سنگین در پیش رو. تاخیر احتمالی $delay دقیقه"
            "سنگین" -> "ترافیک سنگین! تاخیر احتمالی $delay دقیقه"
            "بسیار سنگین" -> "ترافیک بسیار سنگین! توصیه می‌شود مسیر جایگزین"
            else -> "وضعیت ترافیک: $level"
        }
        speak(message)
    }
    
    /**
     * هشدار مسیریابی (پیچ به راست، چپ و...)
     */
    fun alertNavigation(instruction: String, distance: Int) {
        val message = if (distance > 100) {
            "در ${distance} متر، $instruction"
        } else {
            "اکنون $instruction"
        }
        speak(message, TextToSpeech.QUEUE_FLUSH)
    }
    
    /**
     * هشدار خروج از مسیر
     */
    fun alertOffRoute() {
        speak("شما از مسیر خارج شده‌اید. در حال محاسبه مسیر جدید...", TextToSpeech.QUEUE_FLUSH)
    }
    
    /**
     * هشدار رسیدن به مقصد
     */
    fun alertArrival() {
        speak("شما به مقصد رسیدید", TextToSpeech.QUEUE_FLUSH)
    }
    
    /**
     * هشدار شروع مسیریابی
     */
    fun alertNavigationStart(distance: Double, duration: Int) {
        val distanceKm = String.format("%.1f", distance / 1000)
        val durationMin = duration / 60
        speak("مسیریابی شروع شد. مسافت $distanceKm کیلومتر، زمان تقریبی $durationMin دقیقه")
    }
    
    /**
     * توقف سخن
     */
    fun stop() {
        tts?.stop()
    }
    
    /**
     * آزادسازی منابع
     */
    fun cleanup() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
