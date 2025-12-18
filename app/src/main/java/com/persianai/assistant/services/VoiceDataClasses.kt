package com.persianai.assistant.services

import java.io.File
import kotlinx.coroutines.launch

/**
 * Data class for recording result
 */
data class RecordingResult(
    val file: File,
    val duration: Long,
    val timestamp: Long
)

/**
 * Data class for hybrid analysis result
 */
data class HybridAnalysisResult(
    val offlineText: String?,
    val onlineText: String?,
    val primaryText: String,
    val confidence: Double,
    val timestamp: Long
)

/**
 * Safe wrapper for voice recording operations
 */
class SafeVoiceRecordingHelper(private val context: android.content.Context) {
    
    private val TAG = "SafeVoiceHelper"
    private val engine = UnifiedVoiceEngine(context)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())
    
    interface RecordingListener {
        fun onRecordingStarted()
        fun onRecordingCompleted(result: RecordingResult)
        fun onRecordingCancelled()
        fun onRecordingError(error: String)
        fun onAmplitudeChanged(amplitude: Int)
    }
    
    private var currentListener: RecordingListener? = null
    
    /**
     * Set up the recording helper with comprehensive error handling
     */
    fun setup() {
        try {
            // Poll amplitude periodically while recording
            scope.launch {
                while (true) {
                    try {
                        if (engine.isRecordingInProgress()) {
                            val amp = engine.getCurrentAmplitude()
                            currentListener?.onAmplitudeChanged(amp)
                        }
                    } catch (_: Exception) {}
                    kotlinx.coroutines.delay(150)
                }
            }
            android.util.Log.d(TAG, "✅ SafeVoiceHelper setup completed")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error setting up SafeVoiceHelper", e)
        }
    }
    
    /**
     * Start recording with full error handling
     */
    suspend fun startRecording(): Boolean {
        return try {
            android.util.Log.d(TAG, "🎤 Starting safe recording...")
            
            val result = engine.startRecording()
            if (result.isSuccess) {
                android.util.Log.d(TAG, "✅ Recording started successfully")
                currentListener?.onRecordingStarted()
                true
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                android.util.Log.e(TAG, "❌ Failed to start recording: $error")
                currentListener?.onRecordingError("خطا در شروع ضبط: $error")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Exception starting recording", e)
            currentListener?.onRecordingError("خطا: ${e.message}")
            false
        }
    }
    
    /**
     * Stop recording and return result
     */
    suspend fun stopRecording(): RecordingResult? {
        return try {
            android.util.Log.d(TAG, "🛑 Stopping recording...")
            
            val result = engine.stopRecording()
            if (result.isSuccess) {
                val recordingResult = result.getOrThrow()
                android.util.Log.d(TAG, "✅ Recording stopped: ${recordingResult.file.absolutePath}")
                currentListener?.onRecordingCompleted(recordingResult)
                recordingResult
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                android.util.Log.e(TAG, "❌ Failed to stop recording: $error")
                currentListener?.onRecordingError("خطا در توقف ضبط: $error")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Exception stopping recording", e)
            currentListener?.onRecordingError("خطا: ${e.message}")
            null
        }
    }
    
    /**
     * Cancel recording safely
     */
    suspend fun cancelRecording(): Boolean {
        return try {
            android.util.Log.d(TAG, "❌ Cancelling recording...")
            
            val result = engine.cancelRecording()
            if (result.isSuccess) {
                android.util.Log.d(TAG, "✅ Recording cancelled successfully")
                currentListener?.onRecordingCancelled()
                true
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                android.util.Log.e(TAG, "❌ Failed to cancel recording: $error")
                currentListener?.onRecordingError("خطا در لغو ضبط: $error")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Exception cancelling recording", e)
            currentListener?.onRecordingError("خطا: ${e.message}")
            false
        }
    }
    
    /**
     * Analyze recording with hybrid approach
     */
    suspend fun analyzeRecording(recordingResult: RecordingResult): HybridAnalysisResult? {
        return try {
            android.util.Log.d(TAG, "🔍 Starting hybrid analysis...")
            
            val result = engine.analyzeHybrid(recordingResult.file)
            if (result.isSuccess) {
                val analysisResult = result.getOrThrow()
                android.util.Log.d(TAG, "✅ Hybrid analysis completed")
                analysisResult
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                android.util.Log.e(TAG, "❌ Failed to analyze recording: $error")
                currentListener?.onRecordingError("خطا در تحلیل: $error")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Exception analyzing recording", e)
            currentListener?.onRecordingError("خطا: ${e.message}")
            null
        }
    }
    
    /**
     * Set recording listener
     */
    fun setListener(listener: RecordingListener) {
        this.currentListener = listener
    }
    
    /**
     * Check if recording is in progress
     */
    fun isRecording(): Boolean {
        return try {
            engine.isRecordingInProgress()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking recording status", e)
            false
        }
    }
    
    /**
     * Get current recording duration
     */
    fun getRecordingDuration(): Long {
        return try {
            engine.getCurrentRecordingDuration()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting recording duration", e)
            0L
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            android.util.Log.d(TAG, "🧹 Cleaning up SafeVoiceHelper")
            try { scope.cancel() } catch (_: Exception) {}
            // Additional cleanup if needed
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error during cleanup", e)
        }
    }
}
