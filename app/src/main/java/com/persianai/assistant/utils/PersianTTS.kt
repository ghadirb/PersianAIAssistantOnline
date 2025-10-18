package com.persianai.assistant.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

class PersianTTS(private val context: Context) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var isReady = false
    
    init {
        tts = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("fa", "IR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                android.util.Log.e("PersianTTS", "Persian language not supported")
                isReady = false
            } else {
                isReady = true
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
            }
        }
    }
    
    fun speak(text: String, priority: Int = TextToSpeech.QUEUE_ADD) {
        if (isReady) {
            tts?.speak(text, priority, null, "utteranceId")
        }
    }
    
    fun speakSpeedCamera(distance: Int) {
        val text = "دوربین کنترل سرعت $distance متر جلوتر"
        speak(text, TextToSpeech.QUEUE_FLUSH)
    }
    
    fun speakSpeedBump(distance: Int) {
        val text = "سرعت‌گیر $distance متر جلوتر"
        speak(text, TextToSpeech.QUEUE_FLUSH)
    }
    
    fun speakSpeedLimit(limit: Int) {
        val text = "محدودیت سرعت $limit کیلومتر بر ساعت"
        speak(text)
    }
    
    fun speakTraffic(level: String) {
        val text = when (level) {
            "heavy" -> "ترافیک سنگین در جلو"
            "moderate" -> "ترافیک نیمه‌سنگین"
            "light" -> "ترافیک روان"
            else -> "وضعیت ترافیک عادی"
        }
        speak(text)
    }
    
    fun speakNavigation(instruction: String) {
        speak(instruction, TextToSpeech.QUEUE_FLUSH)
    }
    
    fun stop() {
        tts?.stop()
    }
    
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
