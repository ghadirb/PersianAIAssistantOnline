package com.persianai.assistant.offline

import android.util.Log

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
        if (handle != 0L && loadedPath == path) return true
        if (handle != 0L) {
            nativeUnload(handle)
            handle = 0
        }
        val newHandle = nativeLoad(path)
        if (newHandle != 0L) {
            handle = newHandle
            loadedPath = path
            return true
        }
        return false
    }

    @Synchronized
    fun infer(path: String, prompt: String, maxTokens: Int = 96): String? {
        if (prompt.isBlank()) return null
        if (!ensureModel(path)) return null
        return nativeInfer(handle, prompt, maxTokens)
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
