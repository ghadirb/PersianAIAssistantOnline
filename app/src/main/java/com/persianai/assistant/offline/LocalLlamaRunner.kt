package com.persianai.assistant.offline

import android.util.Log
import java.io.File

object LocalLlamaRunner {
    init {
        try {
            System.loadLibrary("local_llama")
        } catch (e: UnsatisfiedLinkError) {
            Log.w("LocalLlamaRunner", "native lib not loaded: ${e.message}")
        }
    }

    private var handle: Long = 0
    private var loadedPath: String? = null

    @Synchronized
    fun ensureModel(path: String): Boolean {
        try {
            if (handle != 0L && loadedPath == path) return true
            if (handle != 0L) {
                nativeUnload(handle)
                handle = 0
            }

            val f = File(path)
            if (!f.exists()) {
                Log.w("LocalLlamaRunner", "Model file does not exist: $path")
                return false
            }
            if (!f.canRead()) {
                Log.w("LocalLlamaRunner", "Model file not readable (permissions?): $path")
                return false
            }
            Log.i("LocalLlamaRunner", "Loading model from $path (size=${f.length()})")

            val newHandle = try {
                nativeLoad(path)
            } catch (e: UnsatisfiedLinkError) {
                Log.e("LocalLlamaRunner", "UnsatisfiedLinkError while calling nativeLoad", e)
                0L
            } catch (t: Throwable) {
                Log.e("LocalLlamaRunner", "Throwable while calling nativeLoad", t)
                0L
            }

            if (newHandle != 0L) {
                handle = newHandle
                loadedPath = path
                return true
            }

            Log.w("LocalLlamaRunner", "nativeLoad returned 0 for path=$path; ensure native lib is packaged and ABI matches device")
            return false
        } catch (e: Exception) {
            Log.e("LocalLlamaRunner", "Exception in ensureModel", e)
            return false
        }
    }

    @Synchronized
    fun infer(path: String, prompt: String, maxTokens: Int = 96): String? {
        if (prompt.isBlank()) return null
        if (!ensureModel(path)) return null
        return try {
            val result = nativeInfer(handle, prompt, maxTokens)
            Log.d("LocalLlamaRunner", "infer done. handle=$handle, path=$path, resultLen=${result?.length ?: 0}")
            result
        } catch (e: UnsatisfiedLinkError) {
            Log.e("LocalLlamaRunner", "UnsatisfiedLinkError during nativeInfer", e)
            null
        } catch (t: Throwable) {
            Log.e("LocalLlamaRunner", "Throwable during nativeInfer", t)
            null
        }
    }

    @Synchronized
    fun unload() {
        if (handle != 0L) {
            nativeUnload(handle)
            handle = 0
            loadedPath = null
        }
    }

    private external fun nativeLoad(path: String): Long
    private external fun nativeInfer(handle: Long, prompt: String, maxTokens: Int): String?
    private external fun nativeUnload(handle: Long)
}
