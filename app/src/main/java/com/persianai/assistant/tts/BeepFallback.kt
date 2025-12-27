package com.persianai.assistant.tts

import android.media.AudioAttributes
import android.media.ToneGenerator
import android.media.AudioManager

object BeepFallback {
    fun beep() {
        try {
            @Suppress("DEPRECATION")
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            tg.release()
        } catch (_: Exception) {
        }
    }
}
