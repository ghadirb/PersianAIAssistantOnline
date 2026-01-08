package com.persianai.assistant.services

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import com.microsoft.onnxruntime.OrtEnvironment
import com.microsoft.onnxruntime.OrtSession

/**
 * Offline Haaniye TTS (piper/mimic3) minimal loader.
 * Copies bundled assets to filesDir/haaniye if present, initializes ONNX session to
 * ensure model integrity, then falls back to Android TTS until full synthesis is wired.
 */
object HaaniyeManager {
    private const val TAG = "HaaniyeManager"
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var initialized = false

    /**
     * Attempt to synthesize speech with offline Haaniye model.
     * Returns true if synthesis handled; false to allow caller fallback.
     */
    fun speak(context: Context, text: String): Boolean {
        return try {
            ensureModelCopied(context)
            if (!initialized) {
                initializeSession(context)
            }

            // TODO: Implement phonemization + vocoder pipeline.
            // Currently we only validate model load; playback is delegated to Android TTS.
            Log.w(TAG, "Haaniye inference not implemented yet; using Android TTS fallback")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Haaniye speak failed: ${e.message}")
            false
        }
    }

    private fun ensureModelCopied(context: Context) {
        val dest = File(context.filesDir, "haaniye")
        if (dest.exists() && dest.isDirectory && dest.list()?.isNotEmpty() == true) return

        val assetBase = "haaniye"
        try {
            copyAssetDir(context, assetBase, dest)
            Log.d(TAG, "Haaniye assets copied to ${dest.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy Haaniye assets: ${e.message}")
        }
    }

    private fun copyAssetDir(context: Context, assetDir: String, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        val assets = context.assets.list(assetDir) ?: return
        for (name in assets) {
            val path = "$assetDir/$name"
            val outFile = File(destDir, name)
            val sub = context.assets.list(path)
            if (sub.isNullOrEmpty()) {
                copyAssetFile(context, path, outFile)
            } else {
                copyAssetDir(context, path, outFile)
            }
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, outFile: File) {
        context.assets.open(assetPath).use { input ->
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun initializeSession(context: Context) {
        try {
            val modelFile = File(context.filesDir, "haaniye/fa-haaniye_low.onnx")
            if (!modelFile.exists()) {
                Log.w(TAG, "Model file missing: ${modelFile.absolutePath}")
                return
            }
            ortEnv = OrtEnvironment.getEnvironment()
            ortSession = ortEnv?.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
            initialized = ortSession != null
            Log.d(TAG, "Haaniye ONNX session initialized: $initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init ONNX session: ${e.message}")
            initialized = false
        }
    }
}
