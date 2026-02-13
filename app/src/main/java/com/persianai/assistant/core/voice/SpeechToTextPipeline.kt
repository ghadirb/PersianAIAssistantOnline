package com.persianai.assistant.core.voice

import android.content.Context
import android.util.Log
import com.persianai.assistant.services.NewHybridVoiceRecorder
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.core.voice.WhisperSttEngine
import com.persianai.assistant.api.IviraAPIClient
import com.persianai.assistant.api.AIModelManager
import com.persianai.assistant.config.RemoteAIConfigManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SpeechToTextPipeline(private val context: Context) {

    private val TAG = "SpeechToTextPipeline"
    private val recorder = NewHybridVoiceRecorder(context)
    private val whisper = WhisperSttEngine(context)
    private val iviraClient = IviraAPIClient(context)
    private val aiModelManager = AIModelManager(context)
    private val remoteConfigManager = RemoteAIConfigManager.getInstance(context)

    suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.e(TAG, "❌ Audio file invalid: ${audioFile.absolutePath}")
                return@withContext Result.failure(IllegalArgumentException("Invalid audio file"))
            }
            
            Log.d(TAG, "🎤 Starting transcription for: ${audioFile.absolutePath}")

            // 1) موقتاً Ivira STT غیرفعال شده تا خطاهای 404/HTML مزاحم نباشد
            //    اکنون ابتدا فقط STT ابری (GAPGPT/Liara/OpenAI) استفاده می‌شود.

            // 2) تلاش برای STT ابری با اولویت از remote config
            val sttPriority = remoteConfigManager.getSTTPriority()
            Log.d(TAG, "STT priority from remote config: $sttPriority")
            runCatching {
                val deferred = CompletableDeferred<String>()
                aiModelManager.transcribeAudio(
                    audioFile = audioFile,
                    onSuccess = { deferred.complete(it) },
                    onError = { deferred.completeExceptionally(Exception(it)) }
                )
                val cloudText = deferred.await().trim()
                if (cloudText.isNotBlank()) {
                    Log.d(TAG, "✅ STT via Cloud (priority: ${sttPriority.joinToString(",")})")
                    return@withContext Result.success(cloudText)
                }
            }.onFailure { e ->
                Log.w(TAG, "Cloud STT failed: ${e.message}")
            }

            // 3) تلاش برای Whisper (اگر کتابخانه و مدل موجود باشد) سپس بازگشت به Vosk
            if (whisper.isAvailable()) {
                Log.d(TAG, "📱 Offline transcription via Whisper (GGUF)")
                val w = whisper.transcribe(audioFile)
                val whisperText = w.getOrNull()?.trim()
                if (!whisperText.isNullOrBlank()) {
                    Log.d(TAG, "✅ STT via local Whisper")
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
                Log.d(TAG, "✅ STT via Vosk offline")
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
