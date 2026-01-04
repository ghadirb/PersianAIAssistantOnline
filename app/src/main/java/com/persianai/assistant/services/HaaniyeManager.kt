package com.persianai.assistant.services

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Stub: offline Haaniye removed. Keeps signature to avoid compile errors.
 */
object HaaniyeManager {
    fun inferOffline(context: Context, audioFile: File): String {
        Log.w("HaaniyeManager", "Offline Haaniye removed; returning empty transcription.")
        return ""
    }
}
