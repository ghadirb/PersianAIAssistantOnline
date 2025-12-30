package com.persianai.assistant.services

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Lightweight Haaniye model manager (scaffold)
 * - Checks for model files under app files/haaniye or assets/tts/haaniye
 * - Provides a safe placeholder inference method until ONNX runtime is integrated
 */
object HaaniyeManager {
    private const val TAG = "HaaniyeManager"
    private const val ASSET_DIR = "tts/haaniye"

    private val ortInit = AtomicBoolean(false)
    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null
    @Volatile private var tokens: List<String>? = null

    private fun getModelFile(context: Context): File {
        val dir = getModelDir(context)
        // Prefer low model if available
        val low = File(dir, "fa-haaniye_low.onnx")
        return if (low.exists()) low else File(dir, "fa-haaniye.onnx")
    }

    private fun loadTokens(context: Context): List<String> {
        tokens?.let { return it }
        val list = try {
            val assetPath = "$ASSET_DIR/tokens.txt"
            context.assets.open(assetPath).bufferedReader().useLines { seq ->
                seq.mapNotNull { line ->
                    val parts = line.split(" ")
                    if (parts.isEmpty()) null else parts.firstOrNull()?.takeIf { it.isNotBlank() }
                }.toList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read tokens.txt", e)
            emptyList()
        }
        tokens = list
        return list
    }

    private fun ensureOrtSession(context: Context): Boolean {
        if (session != null) return true
        if (!ensureModelPresent(context)) return false

        // Single-threaded lazy init
        if (!ortInit.compareAndSet(false, true)) {
            // Someone else is initializing; spin briefly
            var spin = 0
            while (session == null && spin < 50) {
                try { Thread.sleep(20) } catch (_: Exception) {}
                spin++
            }
            return session != null
        }

        return try {
            val model = getModelFile(context)
            val e = OrtEnvironment.getEnvironment()
            val s = e.createSession(model.absolutePath, OrtSession.SessionOptions())
            env = e
            session = s

            // Log model IO to make future fixes easy
            try {
                val inputs = s.inputNames.joinToString(",")
                val outputs = s.outputNames.joinToString(",")
                Log.d(TAG, "ONNX loaded. inputs=[$inputs], outputs=[$outputs]")
                s.inputInfo.forEach { (k, v) -> Log.d(TAG, "inputInfo $k -> ${v.info}") }
                s.outputInfo.forEach { (k, v) -> Log.d(TAG, "outputInfo $k -> ${v.info}") }
            } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init ONNX session", e)
            session = null
            false
        } finally {
            ortInit.set(false)
        }
    }

    fun getModelDir(context: Context): File {
        // Prefer filesDir/haaniye (copied by CI/dev or extracted from assets)
        val filesDir = File(context.filesDir, "haaniye")
        return filesDir
    }

    fun isModelAvailable(context: Context): Boolean {
        val candidates = listOf("fa-haaniye_low.onnx", "fa-haaniye.onnx")
        val dir = getModelDir(context)
        
        // ابتدا چک کن که آیا در filesDir موجود است
        if (dir.exists() && dir.isDirectory) {
            if (candidates.any { File(dir, it).exists() }) {
                Log.d(TAG, "✅ Model available in filesDir: ${dir.absolutePath}")
                return true
            }
        }
        
        // دوم: چک کن assets
        val assetResult = ensureModelPresent(context)
        if (assetResult) {
            Log.d(TAG, "✅ Model available from assets")
        } else {
            Log.w(TAG, "⚠️ Model not found in filesDir or assets. Expected: ${dir.absolutePath}")
        }
        return assetResult
    }

    fun ensureModelPresent(context: Context): Boolean {
        val dir = getModelDir(context)
        if (!dir.exists()) dir.mkdirs()

        val candidates = listOf("fa-haaniye_low.onnx", "fa-haaniye.onnx")
        if (candidates.any { File(dir, it).exists() }) {
            Log.d(TAG, "Model available: true, dir=${dir.absolutePath}")
            return true
        }

        // اول تلاش: کپی مستقیم از assets حتی اگر لیست‌کردن شکست بخورد
        try {
            candidates.forEach { name ->
                val out = File(dir, name)
                if (out.exists()) return@forEach
                runCatching {
                    context.assets.open("$ASSET_DIR/$name").use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output) }
                    }
                    Log.d(TAG, "Copied Haaniye from assets: $name")
                }.onFailure {
                    // ignore, maybe not present in assets
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Asset copy failed: ${e.message}")
        }

        if (candidates.any { File(dir, it).exists() }) {
            Log.d(TAG, "Model extracted from assets, dir=${dir.absolutePath}")
            return true
        }

        // fallback: جستجو در مسیرهای خارجی برای کپی دستی
        val externalCandidates = buildList {
            context.getExternalFilesDir(null)?.let { add(File(it, "haaniye/fa-haaniye_low.onnx")) }
            context.getExternalFilesDir(null)?.let { add(File(it, "haaniye/fa-haaniye.onnx")) }
            add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "fa-haaniye_low.onnx"))
            add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "fa-haaniye.onnx"))
            add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "haaniye/fa-haaniye_low.onnx"))
            add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "haaniye/fa-haaniye.onnx"))
        }

        externalCandidates.forEach { src ->
            if (src.exists() && src.canRead()) {
                val out = File(dir, src.name)
                try {
                    src.copyTo(out, overwrite = true)
                    Log.d(TAG, "Copied Haaniye from external: ${src.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed copying from external ${src.absolutePath}: ${e.message}")
                }
            }
        }

        val finalOk = candidates.any { File(dir, it).exists() }
        if (!finalOk) {
            Log.d(TAG, "Model available: false, dir=${dir.absolutePath}")
        }
        return finalOk
    }

    suspend fun inferOffline(context: Context, audioFile: File): String {
        // Real attempt. If anything doesn't match the model requirements, return blank safely.
        return try {
            if (!ensureOrtSession(context)) {
                Log.d(TAG, "inferOffline: session not available")
                return ""
            }
            val s = session ?: return ""

            val waveform = decodeToFloatWaveform16kMono(audioFile) ?: return ""
            if (waveform.isEmpty()) {
                Log.w(TAG, "inferOffline: decoded waveform is empty")
                return ""
            }

            Log.d(TAG, "inferOffline: waveform decoded, length=${waveform.size}")

            // Heuristic: assume model accepts float waveform [1, N]
            val inputName = s.inputNames.firstOrNull() ?: return ""
            val shape = longArrayOf(1, waveform.size.toLong())

            Log.d(TAG, "inferOffline: inputName=$inputName, shape=${shape.contentToString()}")

            val fb: FloatBuffer = ByteBuffer
                .allocateDirect(waveform.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(waveform)
            fb.rewind()

            val inputTensor = OnnxTensor.createTensor(env ?: OrtEnvironment.getEnvironment(), fb, shape)

            val results = s.run(mapOf(inputName to inputTensor))
            results.use {
                val first = it.firstOrNull()?.value
                if (first == null) {
                    Log.w(TAG, "inferOffline: no output from model")
                    return ""
                }
                // Expected: logits [1, T, V] or [T, V]
                val decoded = greedyCtcDecode(first, loadTokens(context))
                Log.d(TAG, "inferOffline: decoded text = '$decoded'")
                decoded.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "inferOffline failed", e)
            ""
        }
    }

    private fun greedyCtcDecode(output: Any, tokens: List<String>): String {
        return try {
            when (output) {
                is Array<*> -> {
                    // Could be Array<FloatArray> or Array<Array<FloatArray>> depending on runtime
                    val flat = flattenLogits(output) ?: return ""
                    decodeFromLogits(flat, tokens)
                }
                is FloatArray -> {
                    // Not enough information
                    ""
                }
                else -> ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decode failed: ${e.message}")
            ""
        }
    }

    private fun decodeFromLogits(logits: Array<FloatArray>, tokens: List<String>): String {
        // logits: [T][V]
        var prev = -1
        val sb = StringBuilder()
        for (t in logits.indices) {
            val row = logits[t]
            var bestIdx = 0
            var bestVal = Float.NEGATIVE_INFINITY
            for (i in row.indices) {
                val v = row[i]
                if (v > bestVal) {
                    bestVal = v
                    bestIdx = i
                }
            }
            // CTC blank assumed 0
            if (bestIdx != 0 && bestIdx != prev) {
                val tok = tokens.getOrNull(bestIdx)
                if (!tok.isNullOrBlank() && tok != "_") sb.append(tok)
            }
            prev = bestIdx
        }
        return sb.toString()
    }

    private fun flattenLogits(arr: Array<*>): Array<FloatArray>? {
        // Handle common shapes:
        // 1) Array<Array<FloatArray>> => [1][T][V]
        // 2) Array<FloatArray> => [T][V]
        val first = arr.firstOrNull() ?: return null
        return when (first) {
            is Array<*> -> {
                val inner = first as? Array<*> ?: return null
                inner.mapNotNull { it as? FloatArray }.toTypedArray()
            }
            is FloatArray -> {
                arr.mapNotNull { it as? FloatArray }.toTypedArray()
            }
            else -> null
        }
    }

    private fun decodeToFloatWaveform16kMono(audioFile: File): FloatArray? {
        // Decode AAC/M4A to PCM 16-bit then convert to float [-1..1].
        // Keep it minimal and safe; if anything fails return null.
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        return try {
            extractor = MediaExtractor()
            extractor.setDataSource(audioFile.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmOut = ArrayList<Short>(16000 * 20)
            val info = MediaCodec.BufferInfo()
            var isEos = false
            var sawOutputEos = false
            while (!sawOutputEos) {
                if (!isEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, size, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val bb = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN)
                            while (bb.remaining() >= 2) {
                                pcmOut.add(bb.short)
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // ignore
                    }
                }
            }

            if (pcmOut.isEmpty()) return null

            // Resample to 16k if needed (best effort). If sampleRate unknown assume 44100.
            val outFormat = try { codec.outputFormat } catch (_: Exception) { null }
            val sampleRate = try { outFormat?.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { null }
                ?: try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { null }
                ?: 44100
            val channelCount = try { outFormat?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { null }
                ?: try { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { null }
                ?: 1

            val mono = if (channelCount <= 1) {
                pcmOut.toShortArray()
            } else {
                // downmix by taking first channel
                val shorts = pcmOut.toShortArray()
                val m = ShortArray(shorts.size / channelCount)
                var j = 0
                var i = 0
                while (i + (channelCount - 1) < shorts.size) {
                    m[j++] = shorts[i]
                    i += channelCount
                }
                m
            }

            val resampled = if (sampleRate == 16000) mono else resampleLinear(mono, sampleRate, 16000)
            val floats = FloatArray(resampled.size)
            for (i in resampled.indices) {
                floats[i] = (resampled[i] / 32768.0f).coerceIn(-1f, 1f)
            }
            floats
        } catch (e: Exception) {
            Log.w(TAG, "Audio decode failed: ${e.message}")
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun resampleLinear(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (srcRate <= 0 || dstRate <= 0) return input
        if (input.isEmpty()) return input
        val ratio = dstRate.toDouble() / srcRate.toDouble()
        val outSize = (input.size * ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outSize)
        for (i in 0 until outSize) {
            val srcPos = i / ratio
            val idx = srcPos.toInt().coerceIn(0, input.size - 1)
            val idx2 = (idx + 1).coerceIn(0, input.size - 1)
            val frac = (srcPos - idx).toFloat().coerceIn(0f, 1f)
            val v = (input[idx] * (1f - frac) + input[idx2] * frac)
            out[i] = v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}
