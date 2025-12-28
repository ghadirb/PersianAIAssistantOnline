package com.persianai.assistant.core.voice

import android.content.Context
import com.persianai.assistant.services.NewHybridVoiceRecorder
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SpeechToTextPipeline(private val context: Context) {

    private val recorder = NewHybridVoiceRecorder(context)

    suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Invalid audio file"))
            }
            val prefs = PreferencesManager(context)
            val mode = prefs.getWorkingMode()

            // Online-first when allowed; fallback to offline (Haaniye)
            if (mode != PreferencesManager.WorkingMode.OFFLINE) {
                val keys = prefs.getAPIKeys()
                val hasKeys = keys.isNotEmpty() && keys.any { it.isActive }
                if (hasKeys) {
                    val online = recorder.analyzeOnline(audioFile)
                    val onlineText = online.getOrNull()?.trim()
                    if (!onlineText.isNullOrBlank()) {
                        return@withContext Result.success(onlineText)
                    }
                }
            }

            val offline = recorder.analyzeOffline(audioFile)
            val offlineText = offline.getOrNull()?.trim()
            if (!offlineText.isNullOrBlank()) {
                return@withContext Result.success(offlineText)
            }

            Result.failure(IllegalStateException("STT returned blank"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
