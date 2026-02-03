package com.persianai.assistant.core.voice

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional Whisper.cpp-based STT using GGUF model (e.g., whisper-tiny-q5_1.gguf).
 *
 * Tries filesDir copies first; falls back to bundled jniLibs.
 * If unavailable, caller should fall back to Vosk.
 */
class WhisperSttEngine(private val context: Context) {
    companion object {
        private const val TAG = "WhisperSttEngine"
        private val libsLoaded = AtomicBoolean(false)

        private external fun nativeCreateContext(modelPath: String): Long
        private external fun nativeTranscribe(
            contextPtr: Long,
            samples: FloatArray,
            language: String,
            translate: Boolean
        ): String?
        private external fun nativeFreeContext(contextPtr: Long)
    }

    private var ctxPtr: Long = 0L

    fun isAvailable(): Boolean {
        return try {
            val model = ensureModelAvailable() ?: return false
            val libsOk = loadLibrariesIfPresent()
            if (!libsOk) {
                Log.w(TAG, "Whisper libs not loaded; returning unavailable")
                return false
            }
            model.exists()
        } catch (e: Throwable) {
            Log.w(TAG, "Whisper availability check failed: ${e.message}")
            false
        }
    }

    fun close() {
        if (ctxPtr != 0L) {
            try {
                nativeFreeContext(ctxPtr)
            } catch (_: Exception) {
            }
            ctxPtr = 0L
        }
    }

    fun transcribe(audioFile: File): Result<String> {
        return try {
            val model = ensureModelAvailable()
                ?: return Result.failure(IllegalStateException("Whisper model not found"))
            if (!loadLibrariesIfPresent()) {
                return Result.failure(IllegalStateException("Whisper native libs not found"))
            }
            if (ctxPtr == 0L) {
                ctxPtr = nativeCreateContext(model.absolutePath)
                if (ctxPtr == 0L) {
                    return Result.failure(IllegalStateException("Failed to create Whisper context"))
                }
                Log.d(TAG, "✅ Whisper context created (${model.name})")
            }

            val pcm = readWavPcm16Mono(audioFile)
                ?: return Result.failure(IllegalStateException("Invalid WAV for Whisper"))
            if (pcm.isEmpty()) {
                return Result.failure(IllegalStateException("PCM empty"))
            }

            val floats = FloatArray(pcm.size) { idx -> pcm[idx] / 32768f }
            val text = nativeTranscribe(ctxPtr, floats, "fa", false)?.trim().orEmpty()
            if (text.isBlank()) {
                Result.failure(IllegalStateException("Whisper returned blank text"))
            } else {
                Result.success(text)
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Whisper JNI not available: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Whisper transcribe error: ${e.message}", e)
            Result.failure(e)
        } catch (e: Throwable) {
            Log.e(TAG, "Whisper transcribe fatal error: ${e.message}", e)
            Result.failure(IllegalStateException("Whisper failed to initialize", e))
        }
    }

    private fun loadLibrariesIfPresent(): Boolean {
        if (libsLoaded.get()) return true
        return try {
            fun tryLoadFrom(libDir: File): Boolean {
                val ggmlCandidates = listOf("libggml-base.so", "libggml-cpu.so", "libggml.so")
                val cxx = File(libDir, "libc++_shared.so")
                val core = File(libDir, "libwhisper.so")
                val jni = File(libDir, "libwhisper_jni.so")
                if (!core.exists() || !jni.exists()) return false
                val ggml = ggmlCandidates.map { File(libDir, it) }.firstOrNull { it.exists() } ?: return false
                return try {
                    if (cxx.exists()) {
                        System.load(cxx.absolutePath)
                    }
                    System.load(ggml.absolutePath)
                    System.load(core.absolutePath)
                    System.load(jni.absolutePath)
                    libsLoaded.set(true)
                    Log.d(TAG, "✅ Whisper native libs loaded from ${libDir.absolutePath} (${ggml.name})")
                    true
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed loading Whisper libs from ${libDir.absolutePath}: ${e.message}", e)
                    false
                }
            }

            val supportedAbis = Build.SUPPORTED_ABIS.toList()
            val candidateAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            for (abi in candidateAbis) {
                if (!supportedAbis.contains(abi)) continue
                val dir = File(context.filesDir, "whisper_native/$abi")
                if (tryLoadFrom(dir)) return true
            }

            val ggmlLibNames = listOf("c++_shared", "ggml-base", "ggml-cpu", "ggml")
            for (ggmlName in ggmlLibNames) {
                try {
                    System.loadLibrary(ggmlName)
                    // Only after ggml (or c++_shared) attempt to load whisper libs
                    if (ggmlName.startsWith("ggml") || ggmlName == "c++_shared") {
                        System.loadLibrary("ggml-base")
                        System.loadLibrary("ggml-cpu")
                        System.loadLibrary("whisper")
                        System.loadLibrary("whisper_jni")
                        libsLoaded.set(true)
                        Log.d(TAG, "✅ Whisper native libs loaded from bundled jniLibs via $ggmlName")
                        return true
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Whisper native libs not found or failed to load ($ggmlName): ${e.message}")
                }
            }
            false
        } catch (e: Throwable) {
            Log.w(TAG, "Whisper native libs load failed: ${e.message}")
            false
        }
    }

    private fun ensureModelAvailable(): File? {
        val dir = File(context.filesDir, "whisper_models")
        val f = File(dir, "whisper-tiny-q5_1.gguf")
        if (f.exists()) return f
        return try {
            dir.mkdirs()
            context.assets.open("whisper_models/whisper-tiny-q5_1.gguf").use { input ->
                f.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "✅ Whisper model copied from assets to ${f.absolutePath}")
            f
        } catch (e: Exception) {
            Log.w(TAG, "Whisper model not found in assets or copy failed: ${e.message}")
            null
        }
    }

    private fun readWavPcm16Mono(file: File): ShortArray? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 44) return null
                val header = ByteArray(44)
                raf.readFully(header)
                val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                buffer.position(22)
                val channels = buffer.short.toInt()
                val sampleRate = buffer.int
                buffer.position(34)
                val bitsPerSample = buffer.short.toInt()
                if (channels != 1 || sampleRate != 16000 || bitsPerSample != 16) return null
                val dataSize = (raf.length() - 44).toInt()
                if (dataSize <= 0 || dataSize % 2 != 0) return null
                val bytes = ByteArray(dataSize)
                raf.seek(44)
                raf.readFully(bytes)
                val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val out = ShortArray(shorts.remaining())
                shorts.get(out)
                out
            }
        } catch (_: Exception) {
            null
        }
    }
}
