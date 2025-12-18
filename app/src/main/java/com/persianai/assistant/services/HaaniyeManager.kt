package com.persianai.assistant.services

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Lightweight Haaniye model manager (scaffold)
 * - Checks for model files under app files/haaniye or assets/tts/haaniye
 * - Provides a safe placeholder inference method until ONNX runtime is integrated
 */
object HaaniyeManager {
    private const val TAG = "HaaniyeManager"
    private const val ASSET_DIR = "tts/haaniye"

    fun getModelDir(context: Context): File {
        // Prefer filesDir/haaniye (copied by CI/dev), fall back to assets path
        val filesDir = File(context.filesDir, "haaniye")
        return if (filesDir.exists() && filesDir.isDirectory) filesDir else File(context.filesDir, ASSET_DIR)
    }

    fun isModelAvailable(context: Context): Boolean {
        val dir = getModelDir(context)
        if (!dir.exists() || !dir.isDirectory) return false
        // Look for common ONNX filename
        val candidates = listOf("fa-haaniye_low.onnx", "fa-haaniye.onnx")
        return candidates.any { File(dir, it).exists() }
    }

    fun ensureModelPresent(context: Context): Boolean {
        val available = isModelAvailable(context)
        Log.d(TAG, "Model available: $available, dir=${getModelDir(context).absolutePath}")
        return available
    }

    suspend fun inferPlaceholder(context: Context, audioFile: File): String {
        // Placeholder inference: return a short summary with file metadata
        return try {
            val size = audioFile.length()
            val approxSec = (size / (128000L / 8L)).coerceAtLeast(0L)
            "[Haaniye-placeholder] فایل: ${audioFile.name}, مدت تقریبی: ${approxSec}s, حجم: ${size} بایت"
        } catch (e: Exception) {
            Log.e(TAG, "inferPlaceholder error", e)
            "خطا در تحلیل Haaniye"
        }
    }
}
