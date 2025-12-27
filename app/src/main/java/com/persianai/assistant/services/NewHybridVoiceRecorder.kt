package com.persianai.assistant.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import com.persianai.assistant.services.HybridAnalysisResult
import com.persianai.assistant.ai.AIClient
import com.persianai.assistant.utils.PreferencesManager

/**
 * New Hybrid Voice Recorder - Completely rewritten for stability
 * 
 * Features:
 * - Offline analysis using Haaniye model (ONNX)
 * - Online analysis using external APIs
 * - Robust error handling and crash prevention
 * - Proper permission management
 * - Safe resource cleanup
 */
class NewHybridVoiceRecorder(private val context: Context) {
    
    private val TAG = "NewHybridVoice"
    
    // Core recording state (thread-safe)
    private val isRecording = AtomicBoolean(false)
    private val recordingStartTime = AtomicLong(0)
    private var currentFile: File? = null
    private var mediaRecorder: MediaRecorder? = null
    private var amplitudeCallback: ((Int) -> Unit)? = null
    
    // Haaniye model integration
    private var haaniyeModel: Any? = null // Placeholder for ONNX model
    private val haaniyeAssets = "tts/haaniye"
    
    // Amplitude monitoring
    private var amplitudeHandler: Handler? = null
    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            if (isRecording.get()) {
                try {
                    mediaRecorder?.maxAmplitude?.let { amplitude ->
                        amplitudeCallback?.invoke(amplitude)
                    }
                    amplitudeHandler?.postDelayed(this, 100) // Every 100ms
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading amplitude", e)
                }
            }
        }
    }
    
    /**
     * Check all required permissions
     */
    fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Request required permissions
     */
    suspend fun requestPermissions(): Boolean = withContext(Dispatchers.Main) {
        // This should be called from an Activity with proper permission handling.
        // For now, return true if RECORD_AUDIO exists.
        hasRequiredPermissions()
    }
    
    /**
     * Start recording with comprehensive error handling
     */
    suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "🎤 Starting new hybrid recording...")
            
            // Check permissions first
            if (!hasRequiredPermissions()) {
                Log.e(TAG, "❌ Missing required permissions")
                return@withContext Result.failure(SecurityException("Required permissions not granted"))
            }
            
            // Stop any existing recording
            stopRecording()
            
            // Create recording directory
            val recordingDir = File(context.cacheDir, "recordings").apply {
                if (!exists()) mkdirs()
            }
            
            // Generate unique filename
            val timestamp = System.currentTimeMillis()
            currentFile = File(recordingDir, "voice_$timestamp.m4a")
            
            // Initialize MediaRecorder with safety checks
            mediaRecorder = createMediaRecorderSafely()
                ?: return@withContext Result.failure(IllegalStateException("Failed to create MediaRecorder"))
            
            // Configure MediaRecorder
            setupMediaRecorder(mediaRecorder!!, currentFile!!)
            
            // Start recording
            try {
                mediaRecorder!!.prepare()
                mediaRecorder!!.start()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start recording", e)
                cleanupMediaRecorder()
                return@withContext Result.failure(e)
            }
            
            // Update state
            isRecording.set(true)
            recordingStartTime.set(System.currentTimeMillis())
            
            // Start amplitude monitoring
            startAmplitudeMonitoring()
            
            Log.d(TAG, "✅ Recording started successfully: ${currentFile?.absolutePath}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting recording", e)
            cleanup()
            Result.failure(e)
        }
    }
    
    /**
     * Create MediaRecorder with version compatibility
     */
    private fun createMediaRecorderSafely(): MediaRecorder? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaRecorder", e)
            null
        }
    }
    
    /**
     * Setup MediaRecorder configuration
     */
    private fun setupMediaRecorder(recorder: MediaRecorder, file: File) {
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000) // Good quality
                setAudioSamplingRate(44100) // CD quality
                setOutputFile(file.absolutePath)
                setMaxDuration(300000) // 5 minutes max
            }
            Log.d(TAG, "✅ MediaRecorder configured")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup MediaRecorder", e)
            throw e
        }
    }
    
    /**
     * Stop recording and process the audio
     */
    suspend fun stopRecording(): Result<RecordingResult> = withContext(Dispatchers.Main) {
        try {
            if (!isRecording.get()) {
                Log.w(TAG, "⚠️ No recording in progress")
                return@withContext Result.failure(IllegalStateException("No recording in progress"))
            }
            
            Log.d(TAG, "🛑 Stopping recording...")
            
            // Stop amplitude monitoring
            stopAmplitudeMonitoring()
            
            // Stop MediaRecorder safely
            var duration = 0L
            mediaRecorder?.let { recorder ->
                try {
                    duration = System.currentTimeMillis() - recordingStartTime.get()
                    recorder.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping recorder", e)
                } finally {
                    cleanupMediaRecorder()
                }
            }
            
            // Update state
            isRecording.set(false)
            
            // Verify audio file
            val file = currentFile
            if (file == null || !file.exists() || file.length() == 0L) {
                Log.e(TAG, "❌ Audio file is missing or empty")
                return@withContext Result.failure(IllegalStateException("Audio file is invalid"))
            }
            
            Log.d(TAG, "✅ Recording stopped: ${file.absolutePath} (${file.length()} bytes, ${duration}ms)")
            
            // Create recording result
            val result = RecordingResult(
                file = file,
                duration = duration,
                timestamp = System.currentTimeMillis()
            )
            
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping recording", e)
            cleanup()
            Result.failure(e)
        }
    }
    
    /**
     * Cancel recording (stop and delete file)
     */
    suspend fun cancelRecording(): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "❌ Cancelling recording...")
            
            // Stop recording
            if (isRecording.get()) {
                mediaRecorder?.let { recorder ->
                    try {
                        recorder.stop()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping recorder during cancel", e)
                    }
                }
            }
            
            // Delete file
            currentFile?.delete()
            currentFile = null
            
            // Cleanup
            cleanup()
            
            Log.d(TAG, "✅ Recording cancelled")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling recording", e)
            cleanup()
            Result.failure(e)
        }
    }
    
    /**
     * Start amplitude monitoring
     */
    private fun startAmplitudeMonitoring() {
        amplitudeHandler = Handler(Looper.getMainLooper())
        amplitudeHandler?.post(amplitudeRunnable)
    }

    /**
     * Stop amplitude monitoring
     */
    private fun stopAmplitudeMonitoring() {
        amplitudeHandler?.removeCallbacks(amplitudeRunnable)
        amplitudeHandler = null
    }

    /**
     * Set amplitude callback
     */
    fun setAmplitudeCallback(callback: (Int) -> Unit) {
        amplitudeCallback = callback
    }

    fun getCurrentAmplitude(): Int {
        return try {
            if (isRecording.get()) mediaRecorder?.maxAmplitude ?: 0 else 0
        } catch (_: Exception) {
            0
        }
    }

    fun isRecordingInProgress(): Boolean = isRecording.get()

    fun getCurrentRecordingDuration(): Long {
        return if (isRecording.get()) System.currentTimeMillis() - recordingStartTime.get() else 0L
    }

    fun getRecordingFile(): File? = currentFile

    suspend fun analyzeOffline(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Invalid audio file"))
            }

            if (haaniyeModel == null) {
                val available = HaaniyeManager.ensureModelPresent(context)
                haaniyeModel = if (available) "haaniye_model_placeholder" else null
            }

            if (haaniyeModel == null) {
                return@withContext Result.failure(IllegalStateException("Haaniye model not available"))
            }

            val text = HaaniyeManager.inferOffline(context, audioFile)
            if (text.isBlank()) {
                Log.w(TAG, "⚠️ Offline STT returned blank (Haaniye not implemented or failed).")
                Result.failure(IllegalStateException("Offline STT not available"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeOnline(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Invalid audio file"))
            }

            val prefs = PreferencesManager(context)
            val mode = prefs.getWorkingMode()
            if (mode == PreferencesManager.WorkingMode.OFFLINE) {
                return@withContext Result.failure(IllegalStateException("WorkingMode is OFFLINE"))
            }

            val keys = prefs.getAPIKeys()
            val hasKeys = keys.isNotEmpty() && keys.any { it.isActive }
            if (!hasKeys) {
                return@withContext Result.failure(IllegalStateException("No active API key"))
            }

            val client = AIClient(keys)
            val text = client.transcribeAudio(audioFile.absolutePath).trim()
            if (text.isBlank()) {
                Result.failure(IllegalStateException("Online STT returned blank"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeHybrid(audioFile: File): Result<HybridAnalysisResult> = withContext(Dispatchers.IO) {
        val offline = analyzeOffline(audioFile)
        val offlineText = offline.getOrNull()?.trim()

        // If offline succeeded with non-blank text, keep it as primary.
        if (!offlineText.isNullOrBlank()) {
            return@withContext Result.success(
                HybridAnalysisResult(
                    offlineText = offlineText,
                    onlineText = null,
                    primaryText = offlineText,
                    confidence = 0.7,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        // If offline failed/blank, attempt online only when allowed by WorkingMode.
        val online = analyzeOnline(audioFile)
        val onlineText = online.getOrNull()?.trim()

        val primary = onlineText.orEmpty()
        Result.success(
            HybridAnalysisResult(
                offlineText = offlineText,
                onlineText = onlineText,
                primaryText = primary,
                confidence = if (!onlineText.isNullOrBlank()) 0.75 else 0.0,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Cleanup media recorder resources
     */
    private fun cleanupMediaRecorder() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }
    }

    /**
     * Complete cleanup of resources
     */
    private fun cleanup() {
        try {
            stopAmplitudeMonitoring()
            cleanupMediaRecorder()
            isRecording.set(false)
            recordingStartTime.set(0L)
        } catch (_: Exception) {
        }
    }
}