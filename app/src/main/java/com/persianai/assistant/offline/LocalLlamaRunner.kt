package com.persianai.assistant.offline

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin Kotlin wrapper around native llama.cpp bridge (local_llama).
 *
 * The native side builds either the real backend (when LLAMA_CPP_DIR exists)
 * or a stub (when missing). We detect availability via nativeIsRealBackend().
 *
 * Usage:
 *  - Call isAvailable() to ensure native backend is present.
 *  - Call infer(prompt) with a downloaded GGUF file path.
 */
class LocalLlamaRunner {

    companion object {
        private const val TAG = "LocalLlamaRunner"
        private val libLoaded = AtomicBoolean(false)

        init {
            try {
                System.loadLibrary("local_llama")
                libLoaded.set(true)
                Log.d(TAG, "✅ local_llama library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "local_llama native library not found: ${e.message}")
            }
        }

        @JvmStatic private external fun nativeLoad(modelPath: String): Long
        @JvmStatic private external fun nativeInfer(handle: Long, prompt: String, maxTokens: Int): String?
        @JvmStatic private external fun nativeUnload(handle: Long)
        @JvmStatic private external fun nativeIsRealBackend(): Int
    }

    @Volatile private var handle: Long = 0L
    @Volatile private var loadedModelPath: String? = null

    fun isAvailable(): Boolean = libLoaded.get() && nativeIsRealBackend() != 0

    suspend fun infer(prompt: String, modelPath: String, maxTokens: Int = 256): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!isAvailable()) {
                    return@withContext Result.failure(IllegalStateException("local_llama backend not available"))
                }
                if (prompt.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Prompt is blank"))
                }
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    return@withContext Result.failure(IllegalStateException("Model file not found: $modelPath"))
                }

                ensureModelLoaded(modelFile.absolutePath)

                val out = nativeInfer(handle, prompt, maxTokens)?.trim().orEmpty()
                if (out.isBlank()) {
                    Result.failure(IllegalStateException("Local LLM returned empty response"))
                } else {
                    Result.success(out)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Local inference failed: ${e.message}", e)
                Result.failure(e as Exception? ?: Exception("Local inference failed"))
            }
        }

    @Synchronized
    private fun ensureModelLoaded(modelPath: String) {
        if (handle != 0L && modelPath == loadedModelPath) return
        // unload previous if different
        if (handle != 0L && modelPath != loadedModelPath) {
            try { nativeUnload(handle) } catch (_: Exception) {}
            handle = 0L
            loadedModelPath = null
        }
        handle = nativeLoad(modelPath)
        loadedModelPath = modelPath
        if (handle == 0L) {
            throw IllegalStateException("Failed to load local model: $modelPath")
        }
        Log.d(TAG, "✅ Local model loaded: $modelPath")
    }

    fun close() {
        try {
            if (handle != 0L) nativeUnload(handle)
        } catch (_: Exception) {
        } finally {
            handle = 0L
            loadedModelPath = null
        }
    }
}
