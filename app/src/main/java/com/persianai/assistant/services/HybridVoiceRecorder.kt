package com.persianai.assistant.services

import android.content.Context
import android.os.Build
import android.util.Log
import com.persianai.assistant.utils.IviraIntegrationManager
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import kotlin.math.min

/**
 * سیستم ضبط صدای ترکیبی (Offline + Online)
 * 
 * اولویت:
 * 1. Ivira STT (Awasho → Avangardi)
 * 2. آفلاین STT (Haaniye)
 * 3. Fallback
 */
class HybridVoiceRecorder(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) {
    
    private val TAG = "HybridVoiceRecorder"
    private val engine = UnifiedVoiceEngine(context)
    private val iviraManager = IviraIntegrationManager(context)
    private var audioFile: File? = null
    private var isRecording = false
    private var recordingStartTime = 0L
    private var listener: RecorderListener? = null
    
    interface RecorderListener {
        fun onRecordingStarted()
        fun onRecordingCompleted(audioFile: File, durationMs: Long)
        fun onRecordingCancelled()
        fun onRecordingError(error: String)
        fun onAmplitudeChanged(amplitude: Int)
    }
    
    /**
     * شروع ضبط صدا
     */
    fun startRecording() {
        coroutineScope.launch {
            try {
                if (isRecording) {
                    Log.w(TAG, "⚠️ Recording already in progress")
                    return@launch
                }

                val result = engine.startRecording()
                if (result.isSuccess) {
                    recordingStartTime = System.currentTimeMillis()
                    isRecording = true
                    listener?.onRecordingStarted()
                    startAmplitudeMonitoring()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    listener?.onRecordingError("خطا در شروع ضبط: $err")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting recording", e)
                listener?.onRecordingError("خطا در شروع ضبط: ${e.message}")
            }
        }
    }
    
    /**
     * توقف ضبط و پردازش صدا
     */
    fun stopRecording() {
        coroutineScope.launch {
            try {
                if (!isRecording) {
                    Log.w(TAG, "⚠️ No recording in progress")
                    return@launch
                }

                val result = engine.stopRecording()
                if (result.isSuccess) {
                    val rec = result.getOrNull()
                    if (rec != null) {
                        isRecording = false
                        listener?.onRecordingCompleted(rec.file, rec.duration)
                    } else {
                        listener?.onRecordingError("Recording result empty")
                    }
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    listener?.onRecordingError("خطا در توقف ضبط: $err")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping recording", e)
                listener?.onRecordingError("خطا در توقف ضبط: ${e.message}")
            }
        }
    }
    
    /**
     * لغو ضبط
     */
    fun cancelRecording() {
        coroutineScope.launch {
            try {
                if (!isRecording) return@launch
                val result = engine.cancelRecording()
                if (result.isSuccess) {
                    isRecording = false
                    listener?.onRecordingCancelled()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    listener?.onRecordingError("خطا در لغو ضبط: $err")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cancelling recording", e)
                listener?.onRecordingError("خطا در لغو ضبط: ${e.message}")
            }
        }
    }
    
    /**
     * نظارت بر Amplitude
     */
    private fun startAmplitudeMonitoring() {
        coroutineScope.launch {
            while (isRecording) {
                try {
                    val amp = engine.getCurrentAmplitude()
                    listener?.onAmplitudeChanged(amp)
                    delay(100)
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Error monitoring amplitude", e)
                }
            }
        }
    }
    
    /**
     * تحلیل صدا با اولویت Ivira
     */
    suspend fun analyzeAudio(audioFile: File): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🔍 Analyzing audio...")
            
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.w(TAG, "⚠️ Audio file doesn't exist or is empty")
                return@withContext null
            }
            
            var result: String? = null
            
            // اولویت 1: Ivira STT
            iviraManager.processWithIviraPriority(
                operation = "stt",
                input = audioFile,
                onSuccess = { text, modelUsed ->
                    Log.d(TAG, "✅ Recognized by $modelUsed: $text")
                    result = text
                },
                onError = { error ->
                    Log.w(TAG, "⚠️ Ivira STT failed: $error")
                    // Fallback خواهد بود
                }
            )
            
            // اگر Ivira موفق بود، بازگردان نتیجه
            if (!result.isNullOrBlank()) {
                return@withContext result
            }
            
            // اولویت 2: آفلاین تحلیل
            result = analyzeOffline(audioFile)
            if (!result.isNullOrBlank()) {
                return@withContext result
            }
            
            Log.e(TAG, "❌ All analysis methods failed")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error analyzing audio", e)
            null
        }
    }
    
    /**
     * تحلیل Offline (Placeholder)
     */
    suspend fun analyzeOffline(audioFile: File): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🔍 Analyzing with offline model...")
            
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.w(TAG, "⚠️ Audio file doesn't exist")
                return@withContext null
            }
            
            // Placeholder: در آینده می‌توان Haaniye را اضافه کرد
            "تحلیل آفلاین: فایل صوتی شناسایی شد"
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in offline analysis", e)
            null
        }
    }
    
    /**
     * تحلیل Hybrid
     */
    suspend fun analyzeHybrid(audioFile: File): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "⚡ Starting hybrid analysis...")
            
            // اولویت: Ivira → آفلاین
            analyzeAudio(audioFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in hybrid analysis", e)
            null
        }
    }
    
    fun setListener(listener: RecorderListener) {
        this.listener = listener
    }
    
    fun isRecordingInProgress(): Boolean = isRecording
    
    fun getCurrentRecordingDuration(): Long {
        return if (isRecording) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            0
        }
    }
    
    fun getRecordingFile(): File? = audioFile
}
