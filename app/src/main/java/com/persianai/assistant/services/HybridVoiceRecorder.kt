package com.persianai.assistant.services

import android.content.Context
import android.media.MediaRecorder
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
    private var mediaRecorder: MediaRecorder? = null
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
        try {
            if (isRecording) {
                Log.w(TAG, "⚠️ Recording already in progress")
                return
            }
            
            // آماده‌سازی دایرکتوری
            val audioDir = File(context.cacheDir, "hybrid_voice")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            // ایجاد فایل جدید
            audioFile = File(audioDir, "recording_${System.currentTimeMillis()}.m4a")
            
            // تنظیم MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(192000) // بیشتر برای کیفیت بالا
                    setAudioSamplingRate(44100)
                    setOutputFile(audioFile?.absolutePath)
                    
                    prepare()
                    start()
                    
                    Log.d(TAG, "✅ Recording started: ${audioFile?.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error preparing recorder", e)
                    throw e
                }
            }
            
            recordingStartTime = System.currentTimeMillis()
            isRecording = true
            listener?.onRecordingStarted()
            
            // شروع Amplitude Monitoring
            startAmplitudeMonitoring()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting recording", e)
            cleanup()
            listener?.onRecordingError("خطا در شروع ضبط: ${e.message}")
        }
    }
    
    /**
     * توقف ضبط و پردازش صدا
     */
    fun stopRecording() {
        try {
            if (!isRecording) {
                Log.w(TAG, "⚠️ No recording in progress")
                return
            }
            
            mediaRecorder?.apply {
                try {
                    stop()
                    release()
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Error stopping recorder", e)
                }
            }
            mediaRecorder = null
            isRecording = false
            
            val duration = System.currentTimeMillis() - recordingStartTime
            
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "✅ Recording completed: ${file.absolutePath} (${file.length()} bytes)")
                    listener?.onRecordingCompleted(file, duration)
                } else {
                    Log.w(TAG, "⚠️ Audio file is empty")
                    listener?.onRecordingError("فایل صوتی خالی است")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping recording", e)
            listener?.onRecordingError("خطا در توقف ضبط: ${e.message}")
        }
    }
    
    /**
     * لغو ضبط و حذف فایل
     */
    fun cancelRecording() {
        try {
            if (!isRecording) return
            
            mediaRecorder?.apply {
                try {
                    stop()
                    release()
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Error during cancel", e)
                }
            }
            mediaRecorder = null
            
            // حذف فایل
            audioFile?.delete()
            audioFile = null
            
            isRecording = false
            Log.d(TAG, "✅ Recording cancelled")
            listener?.onRecordingCancelled()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling recording", e)
            cleanup()
        }
    }
    
    /**
     * نظارت بر Amplitude (شدت صدا)
     */
    private fun startAmplitudeMonitoring() {
        coroutineScope.launch {
            while (isRecording) {
                try {
                    mediaRecorder?.maxAmplitude?.let { amplitude ->
                        listener?.onAmplitudeChanged(amplitude)
                    }
                    delay(100) // هر 100ms
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
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // Ignore
                }
                try {
                    release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            mediaRecorder = null
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
    
    override fun finalize() {
        try {
            cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error in finalize", e)
        }
    }
}
