package com.persianai.assistant.core.voice

import android.content.Context
import android.util.Log
import com.persianai.assistant.services.NewHybridVoiceRecorder
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.core.voice.WhisperSttEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SpeechToTextPipeline(private val context: Context) {

    private val TAG = "SpeechToTextPipeline"
    private val recorder = NewHybridVoiceRecorder(context)
    private val whisper = WhisperSttEngine(context)

    suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.e(TAG, "❌ Audio file invalid: ${audioFile.absolutePath}")
                return@withContext Result.failure(IllegalArgumentException("Invalid audio file"))
            }
            
            Log.d(TAG, "🎤 Starting transcription for: ${audioFile.absolutePath}")

            // تلاش برای Whisper (اگر کتابخانه و مدل موجود باشد) سپس بازگشت به Vosk
            if (whisper.isAvailable()) {
                Log.d(TAG, "📱 Offline transcription via Whisper (GGUF)")
                val w = whisper.transcribe(audioFile)
                val whisperText = w.getOrNull()?.trim()
                if (!whisperText.isNullOrBlank()) {
                    return@withContext Result.success(whisperText)
                } else {
                    Log.w(TAG, "Whisper failed or returned empty: ${w.exceptionOrNull()?.message}")
                }
            } else {
                Log.d(TAG, "Whisper not available; falling back to Vosk")
            }

            Log.d(TAG, "📱 Offline transcription via Vosk (fallback)")
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
