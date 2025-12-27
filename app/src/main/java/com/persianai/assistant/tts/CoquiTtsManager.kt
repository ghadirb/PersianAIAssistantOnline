package com.persianai.assistant.tts

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class CoquiTtsManager(private val context: Context) {

    private val tag = "CoquiTtsManager"

    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null

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
        return session != null && text.length > 0 && text.trim().isNotEmpty()
    }

    private fun getExistingModelFile(): File? {
        // 1) Prefer a user-provided model in external app-specific downloads
        try {
            val extBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (extBase != null) {
                val manualDir = File(extBase, "coqui_tts")
                val manualModel = File(manualDir, modelName)
                if (manualModel.exists() && manualModel.length() > 0) return manualModel
            }
        } catch (_: Exception) {
        }

        // 2) Fallback to internal storage
        val internalDir = File(context.filesDir, "coqui_tts")
        val internalModel = File(internalDir, modelName)
        if (internalModel.exists() && internalModel.length() > 0) return internalModel

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
