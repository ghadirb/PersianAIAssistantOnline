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
            val prefs = PreferencesManager(context)
            val mode = prefs.getWorkingMode()
            
            Log.d(TAG, "Working mode: $mode")

            // فقط آنلاین (ONLINE یا HYBRID)؛ آفلاین را فعلاً غیرفعال می‌کنیم تا کرش نشود
            if (mode != PreferencesManager.WorkingMode.OFFLINE) {
                Log.d(TAG, "🌐 Attempting online transcription (Priority: Liara Gemini 2.0 Flash)...")
                val keys = prefs.getAPIKeys()
                val liarageminiKey = keys.firstOrNull { it.isActive && it.provider.name == "LIARA" }
                
                if (liarageminiKey != null) {
                    Log.d(TAG, "✔ Found active Liara key for Gemini 2.0 Flash")
                    val online = recorder.analyzeOnline(audioFile)
                    val onlineText = online.getOrNull()?.trim()
                    
                    if (!onlineText.isNullOrBlank()) {
                        Log.d(TAG, "✅ Online transcription (Gemini 2.0 Flash): $onlineText")
                        return@withContext Result.success(onlineText)
                    } else {
                        val err = online.exceptionOrNull()?.message ?: "Empty response"
                        Log.w(TAG, "⚠️ Online failed: $err")
                    }
                } else {
                    Log.w(TAG, "⚠️ No active Liara key found - trying other keys")
                    val activeKey = keys.firstOrNull { it.isActive }
                    if (activeKey != null) {
                        Log.d(TAG, "Using fallback key: ${activeKey.provider.name}")
                        val online = recorder.analyzeOnline(audioFile)
                        val onlineText = online.getOrNull()?.trim()
                        
                        if (!onlineText.isNullOrBlank()) {
                            Log.d(TAG, "✅ Online transcription (fallback): $onlineText")
                            return@withContext Result.success(onlineText)
                        }
                    }
                }
            }

            // در حالت ONLINE/HYBRID اگر آنلاین خالی بود، دیگر به آفلاین نرویم
            if (mode == PreferencesManager.WorkingMode.OFFLINE) {
                Log.d(TAG, "📱 WorkingMode=OFFLINE => calling analyzeOffline")
                val offline = recorder.analyzeOffline(audioFile)
                val offlineText = offline.getOrNull()?.trim()
                return@withContext if (!offlineText.isNullOrBlank()) {
                    Result.success(offlineText)
                } else {
                    Result.failure(Exception(offline.exceptionOrNull()?.message ?: "Offline STT not available"))
                }
            }

            Result.failure(IllegalStateException("Online STT returned blank"))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Transcription exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
