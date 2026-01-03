package com.persianai.assistant.tts

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.persianai.assistant.utils.DefaultApiKeys
import com.persianai.assistant.utils.PreferencesManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import ai.onnxruntime.OnnxValue

class CoquiTtsManager(private val context: Context) {

    private val tag = "CoquiTtsManager"
    private val sampleRate = 22050

    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null
    @Volatile private var tokens: Map<String, Int>? = null

    private val assetDir = "tts/coqui"
    private val modelName = "model.onnx"

    private val modelUrl = "https://huggingface.co/karim23657/persian-tts-vits/resolve/main/persian-tts-male1-vits-coqui/model.onnx"
    private val modelDriveViewUrl = "https://drive.google.com/file/d/11PyQA4T0VQI4PlI6F_qye-89OBKLD1qC/view?usp=drive_link"
    private val modelDriveDirectUrl = "https://drive.google.com/uc?export=download&id=11PyQA4T0VQI4PlI6F_qye-89OBKLD1qC"

    fun getManualModelPath(): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val base = dir ?: context.filesDir
        return File(File(base, "coqui_tts"), modelName).absolutePath
    }

    fun getInternalModelPath(): String {
        return File(File(context.filesDir, "coqui_tts"), modelName).absolutePath
    }

    fun getDriveViewUrl(): String = modelDriveViewUrl

    fun isModelFilePresent(): Boolean {
        val f = getExistingModelFile()
        return f != null && f.exists() && f.length() > 0
    }

    fun downloadModelNow(force: Boolean = false): Boolean {
        val f = ensureModelPresent(forceDownload = force, allowAssetCopy = false)
        return f != null && f.exists() && f.length() > 0
    }

    fun ensureLoaded(): Boolean {
        if (session != null) return true
        return try {
            val modelFile = ensureModelPresent() ?: return false
            val e = OrtEnvironment.getEnvironment()
            val s = e.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
            env = e
            session = s

            try {
                Log.i(tag, "Loaded Coqui ONNX: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
                Log.i(tag, "inputs=${s.inputNames.joinToString(",")}; outputs=${s.outputNames.joinToString(",")}")
                s.inputInfo.forEach { (k, v) -> Log.i(tag, "inputInfo $k -> ${v.info}") }
                s.outputInfo.forEach { (k, v) -> Log.i(tag, "outputInfo $k -> ${v.info}") }
            } catch (_: Exception) {
            }

            true
        } catch (t: Throwable) {
            Log.w(tag, "Failed to load Coqui ONNX", t)
            session = null
            false
        }
    }

    fun isReady(): Boolean = session != null

    fun canSynthesizeText(text: String): Boolean {
        if (text.isBlank()) return false
        // If Coqui ONNX session is loaded and text frontend is available, we can synthesize
        return session != null && text.trim().isNotEmpty()
    }

    fun synthesizeToWav(text: String): File? {
        try {
            if (text.isBlank()) return null
            if (!ensureLoaded()) return null
            val s = session ?: return null
            val tokenIds = tokenize(text)
            if (tokenIds.isEmpty()) return null

            val inputName = s.inputNames.firstOrNull { it.contains("text", true) }
                ?: s.inputNames.firstOrNull { it.contains("input", true) }
                ?: s.inputNames.firstOrNull() ?: return null
            val lengthName = s.inputNames.firstOrNull { it.contains("length", true) }
            val scalesName = s.inputNames.firstOrNull { it.contains("scale", true) }

            val envLocal = env ?: OrtEnvironment.getEnvironment()
            val inputs = mutableMapOf<String, ai.onnxruntime.OnnxTensor>()
            inputs[inputName] = ai.onnxruntime.OnnxTensor.createTensor(envLocal, arrayOf(tokenIds))
            lengthName?.let { inputs[it] = ai.onnxruntime.OnnxTensor.createTensor(envLocal, longArrayOf(tokenIds.size.toLong())) }
            scalesName?.let { inputs[it] = ai.onnxruntime.OnnxTensor.createTensor(envLocal, floatArrayOf(0.667f)) }

            val results = s.run(inputs)
            val audioFloats: FloatArray = try {
                val raw = results[0].value
                when (raw) {
                    is Array<*> -> (raw.firstOrNull() as? FloatArray) ?: return null
                    is FloatArray -> raw
                    is java.nio.FloatBuffer -> {
                        val arr = FloatArray(raw.remaining()); raw.get(arr); arr
                    }
                    else -> return null
                }
            } finally {
                try { results.close() } catch (_: Exception) {}
                inputs.values.forEach { v -> try { v.close() } catch (_: Exception) {} }
            }
            if (audioFloats.isEmpty()) return null

            val wav = File(context.cacheDir, "coqui_tts_${System.currentTimeMillis()}.wav")
            writeWav(wav, audioFloats, sampleRate)
            return wav
        } catch (e: Exception) {
            Log.w(tag, "synthesizeToWav failed: ${e.message}", e)
            return null
        }
    }

    private fun tokenize(text: String): LongArray {
        val map = loadTokens()
        if (map.isEmpty()) return longArrayOf()
        val ids = mutableListOf<Long>()
        text.forEach { ch ->
            val key = ch.toString()
            map[key]?.let { ids.add(it.toLong()) }
        }
        return ids.toLongArray()
    }

    private fun loadTokens(): Map<String, Int> {
        tokens?.let { return it }
        val map = mutableMapOf<String, Int>()
        try {
            val assetPath = "$assetDir/tokens.txt"
            context.assets.open(assetPath).bufferedReader().useLines { seq ->
                seq.forEach { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val tok = parts[0]
                        val id = parts[1].toIntOrNull()
                        if (id != null) map[tok] = id
                    }
                }
            }
            Log.d(tag, "Loaded ${map.size} coqui tokens")
        } catch (e: Exception) {
            Log.w(tag, "Failed to load coqui tokens", e)
        }
        tokens = map
        return map
    }

    private fun writeWav(outFile: File, samples: FloatArray, sampleRate: Int) {
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { f ->
            val s = (f * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            pcm.putShort(s)
        }
        val pcmData = pcm.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val totalDataLen = pcmData.size + 36
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1) // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(pcmData.size)

        FileOutputStream(outFile).use { fos ->
            fos.write(header.array())
            fos.write(pcmData)
            fos.flush()
        }
    }

    private fun getExistingModelFile(): File? {
        val candidateDirs = mutableListOf<File>()

        // user-selected path first
        try {
            val custom = PreferencesManager(context).getCustomCoquiDir()
            if (!custom.isNullOrBlank()) {
                val f = findModelFile(File(custom), depth = 3)
                if (f != null) return f
            }
        } catch (_: Exception) {}

        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
            candidateDirs.add(File(it, "coqui_tts"))
            candidateDirs.add(it)
        }
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
            candidateDirs.add(it)
            candidateDirs.add(File(it, "coqui_tts"))
        }
        Environment.getExternalStorageDirectory()?.let {
            candidateDirs.add(it)
            candidateDirs.add(File(it, "Download"))
        }
        candidateDirs.add(File(context.filesDir, "coqui_tts"))

        candidateDirs.forEach { dir ->
            findModelFile(dir, depth = 2)?.let { return it }
        }
        return null
    }

    private fun findModelFile(dir: File, depth: Int): File? {
        if (depth < 0 || !dir.exists() || !dir.isDirectory) return null
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.equals(modelName, ignoreCase = true) && f.length() > 0) {
                Log.d(tag, "Found Coqui model at ${f.absolutePath}")
                return f
            }
            if (f.isDirectory && (f.name.contains("coqui", true) || depth > 0)) {
                findModelFile(f, depth - 1)?.let { return it }
            }
        }
        return null
    }

    private fun ensureModelPresent(forceDownload: Boolean = false, allowAssetCopy: Boolean = true): File? {
        val existing = if (!forceDownload) getExistingModelFile() else null
        if (existing != null) return existing

        val outDir = File(context.filesDir, "coqui_tts")
        if (!outDir.exists()) outDir.mkdirs()

        val outModel = File(outDir, modelName)
        if (!forceDownload && outModel.exists() && outModel.length() > 0) return outModel

        val assetPath = "$assetDir/$modelName"
        // 1) Try copy from assets (if you ship the model in APK)
        if (allowAssetCopy) {
            try {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outModel).use { output ->
                        input.copyTo(output)
                    }
                }
                if (outModel.exists() && outModel.length() > 0) return outModel
            } catch (_: Exception) {
                Log.i(tag, "Model asset not found or copy failed: $assetPath")
            }
        }

        // 2) Try download at runtime (recommended to avoid huge repo/APK size)
        return try {
            val tmp = File(outDir, "$modelName.tmp")
            if (tmp.exists()) tmp.delete()

            val ok = downloadWithFallback(tmp)
            if (!ok || tmp.length() <= 0L) {
                tmp.delete()
                Log.w(tag, "Downloaded model is empty")
                return null
            }

            if (outModel.exists()) outModel.delete()
            if (!tmp.renameTo(outModel)) {
                // fallback copy if rename fails
                FileOutputStream(outModel).use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
                tmp.delete()
            }

            if (outModel.exists() && outModel.length() > 0) outModel else null
        } catch (e: Exception) {
            Log.w(tag, "Model download failed", e)
            null
        }
    }

    private fun downloadWithFallback(tmp: File): Boolean {
        // Try HuggingFace first
        try {
            Log.i(tag, "Downloading Coqui model (HF): $modelUrl")
            downloadToFile(modelUrl, tmp)
            if (tmp.length() > 0 && !looksLikeHtml(tmp)) return true
            Log.w(tag, "HF download does not look like a binary file")
        } catch (e: Exception) {
            Log.w(tag, "HF download failed", e)
        }

        // Then Google Drive direct link
        try {
            if (tmp.exists()) tmp.delete()
            Log.i(tag, "Downloading Coqui model (Drive): $modelDriveDirectUrl")
            downloadToFile(modelDriveDirectUrl, tmp)
            if (tmp.length() > 0 && !looksLikeHtml(tmp)) return true
            Log.w(tag, "Drive download does not look like a binary file")
        } catch (e: Exception) {
            Log.w(tag, "Drive download failed", e)
        }

        return false
    }

    private fun looksLikeHtml(file: File): Boolean {
        return try {
            val head = file.inputStream().use { input ->
                val buf = ByteArray(256)
                val n = input.read(buf)
                if (n <= 0) return true
                String(buf, 0, n, Charsets.UTF_8)
            }
            val t = head.trimStart().lowercase()
            t.startsWith("<!doctype html") || t.startsWith("<html")
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadToFile(url: String, outFile: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream,*/*")
        }

        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP $code")
        }

        conn.inputStream.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        conn.disconnect()
    }

    fun close() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        env = null
    }
}
