package com.persianai.assistant.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.cancelAndJoin
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

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
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var pcmBuffer: ByteArrayOutputStream? = null
    private var amplitudeCallback: ((Int) -> Unit)? = null
    private val lastAmplitude = AtomicInteger(0)
    
    // Vosk offline model
    @Volatile
    private var voskModel: Model? = null
    private val voskAssetsDir = "vosk"
    
    // Amplitude monitoring
    private var amplitudeHandler: Handler? = null
    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            if (isRecording.get()) {
                try {
                    amplitudeCallback?.invoke(lastAmplitude.get())
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

            // Generate unique filename (WAV)
            val timestamp = System.currentTimeMillis()
            currentFile = File(recordingDir, "voice_$timestamp.wav")

            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
                return@withContext Result.failure(IllegalStateException("Invalid minBuffer for AudioRecord"))
            }

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBuffer * 2
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@withContext Result.failure(IllegalStateException("AudioRecord not initialized"))
            }

            pcmBuffer = ByteArrayOutputStream()
            recorder.startRecording()
            audioRecord = recorder

            isRecording.set(true)
            recordingStartTime.set(System.currentTimeMillis())

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(minBuffer)
                while (isRecording.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        pcmBuffer?.write(buffer, 0, read)
                        // crude peak amplitude for UI (convert 16-bit PCM to absolute max)
                        var peak = 0
                        var i = 0
                        while (i + 1 < read) {
                            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                            val absVal = abs(sample)
                            if (absVal > peak) peak = absVal
                            i += 2
                        }
                        lastAmplitude.set(peak)
                    }
                }
            }

            // Start amplitude monitoring using AudioRecord buffer (approx)
            startAmplitudeMonitoring()

            Log.d(TAG, "✅ Recording started successfully (PCM/WAV): ${currentFile?.absolutePath}")
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
            
            // Stop AudioRecord safely
            var duration = 0L
            isRecording.set(false)
            try {
                recordingJob?.cancelAndJoin()
            } catch (_: Exception) {}
            recordingJob = null

            audioRecord?.let { ar ->
                try {
                    duration = System.currentTimeMillis() - recordingStartTime.get()
                    ar.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping AudioRecord", e)
                } finally {
                    ar.release()
                    audioRecord = null
                }
            }
            
            // Write WAV file
            val file = currentFile
            val pcmBytes = pcmBuffer?.toByteArray() ?: ByteArray(0)
            pcmBuffer = null

            if (file == null || pcmBytes.isEmpty()) {
                Log.e(TAG, "❌ Audio buffer is empty")
                return@withContext Result.failure(IllegalStateException("Audio buffer is empty"))
            }

            writeWavFile(file, pcmBytes, 16000, 1, 16)

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

            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.let { ar ->
                try {
                    ar.stop()
                } catch (_: Exception) {
                } finally {
                    ar.release()
                    audioRecord = null
                }
            }

            // Delete file/buffer
            pcmBuffer = null
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
        lastAmplitude.set(0)
    }

    /**
     * Set amplitude callback
     */
    fun setAmplitudeCallback(callback: (Int) -> Unit) {
        amplitudeCallback = callback
    }

    fun getCurrentAmplitude(): Int {
        return if (isRecording.get()) lastAmplitude.get() else 0
    }

    fun isRecordingInProgress(): Boolean = isRecording.get()

    fun getCurrentRecordingDuration(): Long {
        return if (isRecording.get()) System.currentTimeMillis() - recordingStartTime.get() else 0L
    }

    fun getRecordingFile(): File? = currentFile

    suspend fun analyzeOffline(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Invalid audio file"))
            }

            val model = ensureVoskModel() ?: return@withContext Result.failure(IllegalStateException("Vosk model not loaded"))
            val pcm = decodeToPcm(audioFile)
                ?: return@withContext Result.failure(IllegalStateException("Failed to decode audio to PCM"))

            val mono16k = resampleToMono16k(pcm.data, pcm.sampleRate, pcm.channels)
            if (mono16k.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("PCM buffer empty after resample"))
            }

            Log.d(TAG, "🎧 Running Vosk recognizer on ${mono16k.size} samples")
            Recognizer(model, 16000.0f).use { recognizer ->
                val ok = recognizer.acceptWaveForm(shortArrayToByteArray(mono16k), mono16k.size * 2)
                val json = if (ok) recognizer.result else recognizer.finalResult
                val text = extractTextFromVoskJson(json)
                if (text.isNullOrBlank()) {
                    Result.failure(IllegalStateException("Vosk returned empty text"))
                } else {
                    Result.success(text.trim())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Offline Vosk exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeOnline(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        // آنلاین اختیاری: اگر کلید نبود یا خطا داد، به آفلاین واگذار می‌شود
        return@withContext try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Invalid audio file"))
            }

            val prefs = PreferencesManager(context)
            val mode = prefs.getWorkingMode()
            if (mode == PreferencesManager.WorkingMode.OFFLINE) {
                return@withContext Result.failure(IllegalStateException("WorkingMode is OFFLINE"))
            }

            val keys = prefs.getAPIKeys().filter { it.isActive && it.key.isNotBlank() }
            if (keys.isEmpty()) {
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
            Log.e(TAG, "❌ Online analysis exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeHybrid(audioFile: File): Result<HybridAnalysisResult> = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(context)
        val mode = prefs.getWorkingMode()

        // اگر آفلاین نیستیم، مستقیماً آنلاین را صدا بزنیم
        if (mode != PreferencesManager.WorkingMode.OFFLINE) {
            val online = analyzeOnline(audioFile)
            val onlineText = online.getOrNull()?.trim()
            val primary = onlineText.orEmpty()
            return@withContext Result.success(
                HybridAnalysisResult(
                    offlineText = null,
                    onlineText = onlineText,
                    primaryText = primary,
                    confidence = if (!onlineText.isNullOrBlank()) 0.75 else 0.0,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        // فقط وقتی کاربر صراحتاً OFFLINE است، آفلاین را امتحان کنیم
        val offline = analyzeOffline(audioFile)
        val offlineText = offline.getOrNull()?.trim()
        return@withContext if (!offlineText.isNullOrBlank()) {
            Result.success(
                HybridAnalysisResult(
                    offlineText = offlineText,
                    onlineText = null,
                    primaryText = offlineText,
                    confidence = 0.7,
                    timestamp = System.currentTimeMillis()
                )
            )
        } else {
            Result.failure(Exception(offline.exceptionOrNull()?.message ?: "Offline STT disabled"))
        }
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
            pcmBuffer = null
            recordingJob?.cancel()
            recordingJob = null
            try { audioRecord?.release() } catch (_: Exception) {}
            audioRecord = null
        } catch (_: Exception) {
        }
    }

    // ----- Vosk helpers -----

    private data class PCMData(val data: ShortArray, val sampleRate: Int, val channels: Int)

    @Synchronized
    private fun ensureVoskModel(): Model? {
        if (voskModel != null) return voskModel
        try {
            val targetDir = File(context.filesDir, voskAssetsDir)
            if (!targetDir.exists()) {
                copyAssetsRecursively(voskAssetsDir, targetDir)
            }
            voskModel = Model(targetDir.absolutePath)
            Log.d(TAG, "✅ Vosk model loaded from ${targetDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed loading Vosk model: ${e.message}", e)
            voskModel = null
        }
        return voskModel
    }

    private fun copyAssetsRecursively(assetPath: String, outDir: File) {
        val assetManager = context.assets
        val items = assetManager.list(assetPath) ?: return
        if (!outDir.exists()) outDir.mkdirs()
        for (item in items) {
            val relPath = if (assetPath.isEmpty()) item else "$assetPath/$item"
            val outFile = File(outDir, item)
            val children = assetManager.list(relPath)
            if (children.isNullOrEmpty()) {
                assetManager.open(relPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                copyAssetsRecursively(relPath, outFile)
            }
        }
    }

    private fun decodeToPcm(file: File): PCMData? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex < 0) return null

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: return null)
            codec.configure(format, null, null, 0)
            codec.start()

            val outPcm = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var isEos = false
            while (true) {
                if (!isEos) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                        ?: continue
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk)
                    outputBuffer.clear()
                    outPcm.write(chunk)
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            val pcmBytes = outPcm.toByteArray()
            val shorts = ByteBuffer.wrap(pcmBytes).asShortBuffer()
            val data = ShortArray(shorts.remaining())
            shorts.get(data)
            return PCMData(data = data, sampleRate = sampleRate, channels = channels)
        } catch (e: Exception) {
            Log.e(TAG, "decodeToPcm error: ${e.message}", e)
            try { extractor.release() } catch (_: Exception) {}
            return null
        }
    }

    private fun resampleToMono16k(data: ShortArray, sampleRate: Int, channels: Int): ShortArray {
        if (data.isEmpty()) return data
        // downmix to mono
        val mono = if (channels > 1) {
            val monoData = ShortArray(data.size / channels)
            var m = 0
            var i = 0
            while (i + channels - 1 < data.size) {
                var sum = 0
                for (c in 0 until channels) {
                    sum += data[i + c].toInt()
                }
                monoData[m++] = (sum / channels).toShort()
                i += channels
            }
            monoData.copyOf(m)
        } else data

        if (sampleRate == 16000) return mono
        val ratio = sampleRate.toDouble() / 16000.0
        val outLen = (mono.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val srcIdx = (i * ratio).toInt()
            if (srcIdx in mono.indices) out[i] = mono[srcIdx]
        }
        return out
    }

    private fun shortArrayToByteArray(data: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(data.size * 2)
        for (s in data) buffer.putShort(s)
        return buffer.array()
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        try {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            val byteRate = sampleRate * channels * bitsPerSample / 8
            header.put("RIFF".toByteArray())
            header.putInt(36 + pcmData.size)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1) // AudioFormat PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(pcmData.size)

            FileOutputStream(file).use { out ->
                out.write(header.array())
                out.write(pcmData)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeWavFile error: ${e.message}", e)
            throw e
        }
    }

    private fun extractTextFromVoskJson(json: String): String? {
        return try {
            val obj = JSONObject(json)
            obj.optString("text", null)?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Vosk JSON: ${e.message}")
            null
        }
    }
}