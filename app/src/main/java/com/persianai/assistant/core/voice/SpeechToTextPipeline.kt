package com.persianai.assistant.core.voice

import android.content.Context
import android.util.Log
import com.persianai.assistant.services.NewHybridVoiceRecorder
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SpeechToTextPipeline(private val context: Context) {

    private val TAG = "SpeechToTextPipeline"
    private val recorder = NewHybridVoiceRecorder(context)

    suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.e(TAG, "❌ Audio file invalid: ${audioFile.absolutePath}")
                return@withContext Result.failure(IllegalArgumentException("Invalid audio file"))
            }
            
            Log.d(TAG, "🎤 Starting transcription for: ${audioFile.absolutePath}")

            // تلاش آنلاین (اگر خطا/خالی بود به آفلاین می‌رویم)
            val online = recorder.analyzeOnline(audioFile)
            val onlineText = online.getOrNull()?.trim()
            if (!onlineText.isNullOrBlank()) {
                Log.d(TAG, "✅ Online transcription: $onlineText")
                return@withContext Result.success(onlineText)
            } else {
                val err = online.exceptionOrNull()?.message ?: "Empty response"
                Log.w(TAG, "⚠️ Online STT returned blank/error: $err")
            }

            // آفلاین Vosk به‌عنوان فallback قطعی
            Log.d(TAG, "📱 Falling back to offline Vosk transcription")
            val offline = recorder.analyzeOffline(audioFile)
            val offlineText = offline.getOrNull()?.trim()
            return@withContext if (!offlineText.isNullOrBlank()) {
                Result.success(offlineText)
            } else {
                Result.failure(Exception(offline.exceptionOrNull()?.message ?: "Offline STT not available"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Transcription exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
