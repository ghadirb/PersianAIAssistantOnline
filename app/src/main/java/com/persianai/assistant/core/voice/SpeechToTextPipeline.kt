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

            // Only attempt online if not OFFLINE mode
            if (mode != PreferencesManager.WorkingMode.OFFLINE) {
                Log.d(TAG, "🌐 Attempting online transcription (Priority: OpenAI Whisper)...")
                val keys = prefs.getAPIKeys()
                val activeKeys = keys.filter { it.isActive && !it.key.isNullOrBlank() }
                
                if (activeKeys.isEmpty()) {
                    Log.w(TAG, "⚠️ No active API keys for STT")
                } else {
                    // ✅ Priority order:
                    // 1. OpenAI Whisper
                    // 2. AIML STT
                    // 3. OpenRouter
                    // ❌ Skip Gladia (known to have issues)
                    
                    val sttsToTry = listOf(
                        activeKeys.filter { it.provider.name == "OPENAI" },
                        activeKeys.filter { it.provider.name == "AIML" },
                        activeKeys.filter { it.provider.name == "OPENROUTER" },
                        activeKeys.filter { it.provider.name == "LIARA" }
                    )
                    
                    for (providerKeys in sttsToTry) {
                        if (providerKeys.isEmpty()) continue
                        
                        val providerName = providerKeys[0].provider.name
                        Log.d(TAG, "✔ Trying $providerName STT")
                        
                        try {
                            val online = recorder.analyzeOnline(audioFile)
                            val onlineText = online.getOrNull()?.trim()
                            
                            if (!onlineText.isNullOrBlank()) {
                                Log.d(TAG, "✅ Online transcription ($providerName): $onlineText")
                                return@withContext Result.success(onlineText)
                            } else {
                                val err = online.exceptionOrNull()?.message ?: "Empty response"
                                Log.w(TAG, "⚠️ $providerName returned blank/error: $err")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "⚠️ $providerName STT failed: ${e.message}")
                        }
                    }
                    
                    Log.e(TAG, "❌ All online STT providers failed")
                }
            }

            // Offline fallback
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

            Result.failure(IllegalStateException("All STT providers failed"))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Transcription exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
