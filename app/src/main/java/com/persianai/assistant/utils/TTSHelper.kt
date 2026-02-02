package com.persianai.assistant.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*
import com.persianai.assistant.services.HaaniyeManager

/**
 * کمک‌کننده برای تبدیل متن به گفتار فارسی
 */
class TTSHelper(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val prefsManager = PreferencesManager(context)

    companion object {
        private const val TAG = "TTSHelper"
    }

    fun initialize(onReady: (() -> Unit)? = null) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("fa", "IR"))
                
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Persian language not supported on this device; disabling Android TTS")
                    // If Persian is unavailable, rely solely on Haaniye and skip Android TTS
                    tts?.shutdown()
                    tts = null
                    isInitialized = false
                    return@TextToSpeech
                }

                // تنظیمات صدا
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(0.9f) // کمی آهسته‌تر برای وضوح بیشتر
                
                isInitialized = true
                Log.d(TAG, "TTS initialized successfully")
                onReady?.invoke()
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }

        // Listener برای رویدادهای TTS
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS finished: $utteranceId")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error: $utteranceId, code: $errorCode")
            }
        })
    }

    /**
     * اعلام متن به صورت صوتی
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!prefsManager.isTTSEnabled()) {
            Log.d(TAG, "TTS is disabled")
            return
        }

        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet")
            return
        }

        // پاکسازی متن از emoji و کاراکترهای خاص
        val cleanText = cleanTextForTTS(text)

        if (cleanText.isBlank()) {
            Log.d(TAG, "Empty text after cleaning")
            return
        }

        // تلاش برای TTS آفلاین حانیه (اگر موفق نشد، اندروید)
        try {
            val handled = HaaniyeManager.speak(context, cleanText)
            if (handled) return
        } catch (e: Exception) {
            Log.w(TAG, "Haaniye TTS failed: ${e.message}")
        }

        Log.d(TAG, "Speaking (Android TTS): $cleanText")
        tts?.speak(cleanText, queueMode, null, "tts_${System.currentTimeMillis()}")
    }

    /**
     * توقف اعلام فعلی
     */
    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    /**
     * آزاد کردن منابع
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        Log.d(TAG, "TTS shutdown")
    }

    /**
     * پاکسازی متن برای TTS
     */
    private fun cleanTextForTTS(text: String): String {
        return text
            // حذف emoji
            .replace(Regex("[\\p{So}\\p{Sk}]"), "")
            // حذف لینک‌ها
            .replace(Regex("https?://\\S+"), "")
            // حذف کاراکترهای خاص اضافی
            .replace(Regex("[📱📋✅❌⚠️🔴💬📞🌐⚙️⚡]"), "")
            // حذف خطوط خالی اضافی
            .replace(Regex("\\n{2,}"), "\n")
            // حذف فاصله‌های اضافی
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * چک کردن در دسترس بودن
     */
    fun isAvailable(): Boolean {
        return isInitialized && tts != null
    }

    /**
     * تنظیم سرعت گفتار
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    /**
     * تنظیم pitch صدا
     */
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }
}
