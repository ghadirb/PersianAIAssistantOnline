package com.persianai.assistant.services

import android.content.Context
import android.os.Build
import android.util.Log
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
 * - Offline: استفاده از مدل Haaniye برای تحلیل فوری
 * - Online: آپلود به سرورهای aimlapi یا Qwen2.5 برای تحلیل پیشرفته
 */
class HybridVoiceRecorder(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) {
    
    private val TAG = "HybridVoiceRecorder"
    private val engine = UnifiedVoiceEngine(context)
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
     * شروع ضبط صدا با محافظت کاملی
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
                    // Start amplitude monitoring loop
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
     * لغو ضبط و حذف فایل
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
     * نظارت بر Amplitude (شدت صدا)
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
     * تحلیل Offline (استفاده از Haaniye)
     */
    suspend fun analyzeOffline(audioFile: File): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🔍 Analyzing with offline model (Haaniye)...")
            
            // Check if audio file exists
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.w(TAG, "⚠️ Audio file doesn't exist or is empty")
                return@withContext null
            }
            
            // For now, return placeholder text
            // In production, implement Haaniye model loading here
            "تحلیل آفلاین: فایل صوتی شناسایی شد (${audioFile.length()} بایت)"
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in offline analysis", e)
            null
        }
    }
    
    /**
     * تحلیل Online (aimlapi / Qwen2.5)
     */
    suspend fun analyzeOnline(audioFile: File): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🌐 Uploading to online model...")
            
            // Check if audio file exists
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.w(TAG, "⚠️ Audio file doesn't exist or is empty")
                return@withContext null
            }
            
            val httpClient = OkHttpClient()
            
            // Try using aimlapi for speech recognition
            val apiKey = "your-aimlapi-key" // TODO: Get from preferences
            if (apiKey.isBlank() || apiKey == "your-aimlapi-key") {
                Log.d(TAG, "⚠️ API key not configured, returning placeholder")
                return@withContext "تحلیل آنلاین: نیازمند کلید API"
            }
            
            // Upload audio file to aimlapi
            val audioBytes = audioFile.readBytes()
            val body = audioBytes.toRequestBody("audio/m4a".toMediaType())
            
            val request = Request.Builder()
                .url("https://api.aimlapi.com/v1/audio/transcribe")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "❌ API Error: ${response.code} ${response.message}")
                    return@use "خطا در تحلیل آنلاین"
                }
                
                val responseBody = response.body?.string() ?: return@use "پاسخ خالی"
                
                // Parse JSON response
                try {
                    val json = JSONObject(responseBody)
                    val text = json.optString("result", json.optString("text", responseBody))
                    Log.d(TAG, "✅ Online analysis result: $text")
                    text
                } catch (e: Exception) {
                    Log.d(TAG, "⚠️ Could not parse JSON, returning raw response")
                    responseBody
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in online analysis", e)
            null
        }
    }
    
    /**
     * تحلیل ترکیبی (Offline سپس Online)
     */
    suspend fun analyzeHybrid(audioFile: File): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "⚡ Starting hybrid analysis...")
            
            // Step 1: Offline analysis
            val offlineResult = analyzeOffline(audioFile)
            Log.d(TAG, "✅ Offline analysis done: $offlineResult")
            
            // Step 2: Parallel online analysis
            val onlineResult = async { analyzeOnline(audioFile) }.await()
            Log.d(TAG, "✅ Online analysis done: $onlineResult")
            
            // استفاده از بهترین نتیجه
            offlineResult ?: onlineResult
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in hybrid analysis", e)
            null
        }
    }
    
    /**
     * پاکسازی منابع
     */
    private fun cleanup() {
        try {
            // Delegate cleanup to the engine (cancel asynchronously) and reset local state.
            try {
                coroutineScope.launch {
                    try { engine.cancelRecording() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            // Ignore
        } finally {
            isRecording = false
            audioFile = null
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
