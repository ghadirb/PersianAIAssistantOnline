package com.persianai.assistant.core.voice

import android.content.Context
import android.util.Log
import com.persianai.assistant.services.NewHybridVoiceRecorder
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.core.voice.WhisperSttEngine
import com.persianai.assistant.api.IviraAPIClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SpeechToTextPipeline(private val context: Context) {

    private val TAG = "SpeechToTextPipeline"
    private val recorder = NewHybridVoiceRecorder(context)
    private val whisper = WhisperSttEngine(context)
    private val iviraClient = IviraAPIClient(context)

    suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.e(TAG, "❌ Audio file invalid: ${audioFile.absolutePath}")
                return@withContext Result.failure(IllegalArgumentException("Invalid audio file"))
            }
            
            Log.d(TAG, "🎤 Starting transcription for: ${audioFile.absolutePath}")

            // 1) تلاش آنلاین Ivira STT (Awasho → Avangardi)؛ در صورت خطا/خالی به مرحله بعد
            runCatching {
                val deferred = CompletableDeferred<String>()
                iviraClient.speechToText(
                    audioFile = audioFile,
                    model = null,
                    onSuccess = { deferred.complete(it) },
                    onError = { deferred.completeExceptionally(Exception(it)) }
                )
                val text = deferred.await().trim()
                if (text.isNotBlank()) {
                    return@withContext Result.success(text)
                }
            }.onFailure { e ->
                Log.w(TAG, "Ivira STT failed: ${e.message}")
            }

            // 2) تلاش برای Whisper (اگر کتابخانه و مدل موجود باشد) سپس بازگشت به Vosk
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
