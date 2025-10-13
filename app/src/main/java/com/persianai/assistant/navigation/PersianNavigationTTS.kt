package com.persianai.assistant.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

/**
 * تبدیل متن به گفتار فارسی برای مسیریابی
 * استفاده از Android TTS با زبان فارسی
 */
class PersianNavigationTTS(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingMessages = mutableListOf<String>()
    
    companion object {
        private const val TAG = "PersianNavigationTTS"
    }
    
    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("fa", "IR"))
                
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Persian language not supported, using default")
                    // استفاده از زبان پیش‌فرض
                    tts?.setLanguage(Locale.getDefault())
                }
                
                // تنظیمات برای خواندن بهتر
                tts?.setSpeechRate(0.85f) // کمی آهسته‌تر برای وضوح بیشتر
                tts?.setPitch(1.0f)
                
                isReady = true
                Log.d(TAG, "Persian TTS initialized successfully")
                
                // پخش پیام‌های در صف
                pendingMessages.forEach { message ->
                    speakNow(message)
                }
                pendingMessages.clear()
            } else {
                Log.e(TAG, "Failed to initialize TTS")
            }
        }
    }
    
    /**
     * خواندن متن فارسی
     */
    suspend fun speak(text: String) {
        if (isReady) {
            speakNow(text)
        } else {
            // اگر آماده نیست، در صف قرار بگیرد
            pendingMessages.add(text)
        }
    }
    
    private fun speakNow(text: String) {
        try {
            // تبدیل اعداد انگلیسی به فارسی
            val persianText = convertNumbersToPersian(text)
            
            tts?.speak(persianText, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
            Log.d(TAG, "Speaking: $persianText")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text", e)
        }
    }
    
    /**
     * متوقف کردن پخش
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }
    
    /**
     * بستن TTS
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
    
    /**
     * تبدیل اعداد انگلیسی به فارسی برای خواندن بهتر
     */
    private fun convertNumbersToPersian(text: String): String {
        var result = text
        
        // تبدیل اعداد به حروف فارسی
        val numbers = mapOf(
            "0" to "صفر",
            "1" to "یک",
            "2" to "دو",
            "3" to "سه",
            "4" to "چهار",
            "5" to "پنج",
            "6" to "شش",
            "7" to "هفت",
            "8" to "هشت",
            "9" to "نه",
            "10" to "ده",
            "20" to "بیست",
            "30" to "سی",
            "40" to "چهل",
            "50" to "پنجاه",
            "60" to "شصت",
            "70" to "هفتاد",
            "80" to "هشتاد",
            "90" to "نود",
            "100" to "صد",
            "200" to "دویست",
            "300" to "سیصد",
            "400" to "چهارصد",
            "500" to "پانصد",
            "1000" to "هزار"
        )
        
        // جایگزینی اعداد معمول
        numbers.forEach { (english, persian) ->
            result = result.replace("\\b$english\\b".toRegex(), persian)
        }
        
        return result
    }
    
    /**
     * دستورات مسیریابی فارسی
     */
    object NavigationCommands {
        const val TURN_RIGHT = "به راست بپیچید"
        const val TURN_LEFT = "به چپ بپیچید"
        const val GO_STRAIGHT = "مستقیم بروید"
        const val MAKE_U_TURN = "دور بزنید"
        const val ARRIVE_DESTINATION = "به مقصد رسیدید"
        const val ROUNDABOUT_ENTER = "وارد میدان شوید"
        const val ROUNDABOUT_EXIT = "از خروجی خارج شوید"
        const val MERGE_LEFT = "به چپ ادغام شوید"
        const val MERGE_RIGHT = "به راست ادغام شوید"
        
        fun inDistance(meters: Int): String {
            return when {
                meters < 50 -> "در $meters متری"
                meters < 100 -> "پس از صد متر"
                meters < 500 -> "در $meters متری"
                meters < 1000 -> "در یک کیلومتری"
                else -> "در ${meters / 1000} کیلومتری"
            }
        }
    }
}
