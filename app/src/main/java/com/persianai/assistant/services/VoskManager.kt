package com.persianai.assistant.services

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.os.Environment
import com.persianai.assistant.utils.PreferencesManager

object VoskManager {
    private const val TAG = "VoskManager"
    private const val TARGET_SAMPLE_RATE = 16000

    @Volatile
    private var model: Model? = null

    /**
     * Ensure model is available under filesDir/vosk; if not, copy from assets/vosk; otherwise download/extract from URL.
     */
    private fun ensureModel(context: Context): Boolean {
        if (model != null) return true
        synchronized(this) {
            if (model != null) return true
            val modelDir = resolveOrDownloadModel(context) ?: return false
            return try {
                model = Model(modelDir.absolutePath)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Vosk model: ${e.message}")
                false
            }
        }
    }

    /**
     * Try external/shared storage before assets/download.
     */
    private fun resolveOrDownloadModel(context: Context): File? {
        // User-selected directory (if any) has highest priority
        try {
            val custom = PreferencesManager(context).getCustomVoskDir()
            if (!custom.isNullOrBlank()) {
                val dir = File(custom)
                if (isValidVoskDir(dir)) {
                    Log.d(TAG, "Using user-selected Vosk dir: ${dir.absolutePath}")
                    return dir
                }
            }
        } catch (_: Exception) {}

        // 1) External common locations (no copy, just use in-place)
        val externalDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStorageDirectory(),
            context.getExternalFilesDir(null),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        )
        externalDirs.forEach { base ->
            // shallow scan
            base.listFiles()?.forEach { dir ->
                if (dir.isDirectory && (dir.name.contains("vosk", true) || dir.name.contains("rhasspy", true))) {
                    if (isValidVoskDir(dir)) {
                        Log.d(TAG, "Using external Vosk model at ${dir.absolutePath}")
                        return dir
                    }
                }
            }
            // direct path vosk-model-small-fa-rhasspy-0.15
            val rhasspy = File(base, "vosk-model-small-fa-rhasspy-0.15")
            if (isValidVoskDir(rhasspy)) {
                Log.d(TAG, "Using external Vosk model at ${rhasspy.absolutePath}")
                return rhasspy
            }
            // recursive scan depth 2 for any directory containing conf/am
            findVoskDirRecursive(base, depth = 2)?.let { return it }
        }

        // 2) app filesDir (copied or previously downloaded)
        val filesDirModel = File(context.filesDir, "vosk")
        if (isValidVoskDir(filesDirModel)) return filesDirModel

        // 3) try copy from assets, otherwise download
        if (!filesDirModel.exists()) filesDirModel.mkdirs()
        val copied = copyAssetsDir(context, "vosk", filesDirModel)
        if (!copied) {
            Log.w(TAG, "Vosk model not found in assets/vosk, attempting download...")
            val url = com.persianai.assistant.utils.DefaultApiKeys.getVoskFaModelUrl()
            if (url.isNullOrBlank()) return null
            val ok = downloadAndUnzip(url, filesDirModel)
            if (!ok) return null
        }
        return if (isValidVoskDir(filesDirModel)) filesDirModel else null
    }

    private fun isValidVoskDir(dir: File?): Boolean {
        if (dir == null || !dir.exists() || !dir.isDirectory) return false
        val missing = listMissingModelParts(dir)
        if (missing.isNotEmpty()) {
            Log.w(TAG, "Vosk dir missing files: ${missing.joinToString()} at ${dir.absolutePath}")
            return false
        }
        return true
    }

    private fun listMissingModelParts(dir: File): List<String> {
        val missing = mutableListOf<String>()
        val conf = File(dir, "conf/mfcc.conf")
        val am = File(dir, "am/final.mdl")
        val graph = File(dir, "graph/HCLG.fst")
        val graphAlt = File(dir, "graph/words.txt")

        if (!conf.exists() || conf.length() == 0L) missing.add("conf/mfcc.conf")
        if (!am.exists() || am.length() == 0L) missing.add("am/final.mdl")
        if ((!graph.exists() || graph.length() == 0L) && (!graphAlt.exists() || graphAlt.length() == 0L)) {
            missing.add("graph/HCLG.fst")
        }
        return missing
    }

    private fun findVoskDirRecursive(base: File, depth: Int): File? {
        if (depth < 0 || !base.exists() || !base.isDirectory) return null
        base.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                if (isValidVoskDir(f)) return f
                val name = f.name.lowercase()
                if (name.contains("vosk") || name.contains("rhasspy") || depth > 0) {
                    findVoskDirRecursive(f, depth - 1)?.let { return it }
                }
            }
        }
        return null
    }

    /**
     * Transcribe audio file using Vosk.
     */
    fun transcribe(context: Context, audioFile: File): String {
        return try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.w(TAG, "Vosk transcribe: invalid audio file")
                return ""
            }

            if (!ensureModel(context)) return ""
            val pcm = decodeToPcm16kMono(audioFile) ?: return ""
            if (pcm.isEmpty()) return ""

            val modelRef = model ?: return ""
            Recognizer(modelRef, TARGET_SAMPLE_RATE.toFloat()).use { recognizer ->
                val bb = ByteBuffer
                    .allocateDirect(pcm.size * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                pcm.forEach { bb.putShort(it) }
                bb.flip()

                val chunk = ByteArray(4096)
                while (bb.remaining() > 0) {
                    val len = minOf(chunk.size, bb.remaining())
                    bb.get(chunk, 0, len)
                    recognizer.acceptWaveForm(chunk, len)
                }
                val finalJson = recognizer.finalResult
                val text = extractText(finalJson)
                Log.d(TAG, "Vosk final text='$text'")
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vosk transcribe failed: ${e.message}", e)
            ""
        }
    }

    private fun extractText(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return try {
            val idx = json.indexOf("\"text\"")
            if (idx < 0) return ""
            val colon = json.indexOf(":", idx)
            val quote1 = json.indexOf("\"", colon + 1)
            val quote2 = json.indexOf("\"", quote1 + 1)
            if (quote1 >= 0 && quote2 > quote1) {
                json.substring(quote1 + 1, quote2).trim()
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Decode audio (m4a/mp4/aac) to PCM16 mono @16k.
     */
    private fun decodeToPcm16kMono(audioFile: File): ShortArray? {
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
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore */ }
                }
            }

            if (pcmOut.isEmpty()) return null

            val outFormat = try { codec.outputFormat } catch (_: Exception) { null }
            val sampleRate = try { outFormat?.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { null }
                ?: try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { null }
                ?: 44100

            val mono = pcmOut.toShortArray()
            if (sampleRate == TARGET_SAMPLE_RATE) return mono

            // Down/upsample with simple stride
            val ratio = sampleRate.toDouble() / TARGET_SAMPLE_RATE
            val targetLen = (mono.size / ratio).toInt().coerceAtLeast(1)
            val resampled = ShortArray(targetLen)
            for (i in 0 until targetLen) {
                val srcIdx = (i * ratio).toInt()
                if (srcIdx in mono.indices) {
                    resampled[i] = mono[srcIdx]
                }
            }
            resampled
        } catch (e: Exception) {
            Log.e(TAG, "decodeToPcm16kMono failed: ${e.message}")
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun copyAssetsDir(context: Context, assetPath: String, destDir: File): Boolean {
        return try {
            val am = context.assets
            val list = am.list(assetPath) ?: return false
            if (list.isEmpty()) return false
            if (!destDir.exists()) destDir.mkdirs()
            for (name in list) {
                val subAsset = if (assetPath.isEmpty()) name else "$assetPath/$name"
                val destFile = File(destDir, name)
                val children = am.list(subAsset)
                if (children != null && children.isNotEmpty()) {
                    copyAssetsDir(context, subAsset, destFile)
                } else {
                    am.open(subAsset).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyAssetsDir failed: ${e.message}")
            false
        }
    }

    private fun downloadAndUnzip(url: String, destDir: File): Boolean {
        return try {
            if (!destDir.exists()) destDir.mkdirs()
            ZipInputStream(URL(url).openStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadAndUnzip failed: ${e.message}")
            false
        }
    }
}
