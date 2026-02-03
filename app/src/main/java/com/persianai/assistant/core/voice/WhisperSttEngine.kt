diff --git a/app/src/main/java/com/persianai/assistant/core/voice/WhisperSttEngine.kt b/app/src/main/java/com/persianai/assistant/core/voice/WhisperSttEngine.kt
index c75dc3b48b9be4d23fa64f53a7dc6376dbc298a4..29762e3ade21d443e5f665fe354f740d6333df8f 100644
--- a/app/src/main/java/com/persianai/assistant/core/voice/WhisperSttEngine.kt
+++ b/app/src/main/java/com/persianai/assistant/core/voice/WhisperSttEngine.kt
@@ -17,52 +17,57 @@ import java.util.concurrent.atomic.AtomicBoolean
  *   filesDir/whisper_native/arm64-v8a/libwhisper.so
  *   filesDir/whisper_native/arm64-v8a/libwhisper_jni.so
  *   filesDir/whisper_models/whisper-tiny-q5_1.gguf
  *
  * If unavailable or any step fails, caller should fallback to Vosk.
  */
 class WhisperSttEngine(private val context: Context) {
     companion object {
         private const val TAG = "WhisperSttEngine"
         private val libsLoaded = AtomicBoolean(false)
 
         // JNI bindings from whisper-android-lib
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
-        val model = ensureModelAvailable() ?: return false
-        return loadLibrariesIfPresent() && model.exists()
+        return try {
+            val model = ensureModelAvailable() ?: return false
+            loadLibrariesIfPresent() && model.exists()
+        } catch (e: Throwable) {
+            Log.w(TAG, "Whisper availability check failed: ${e.message}")
+            false
+        }
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
 
     /**
      * Transcribe a WAV 16k mono PCM16 file using whisper.cpp.
      * Returns Result.success(text) or failure; caller should fallback to Vosk on failure.
      */
     fun transcribe(audioFile: File): Result<String> {
         return try {
             val model = ensureModelAvailable()
                 ?: return Result.failure(IllegalStateException("Whisper model not found"))
             if (!loadLibrariesIfPresent()) {
                 return Result.failure(IllegalStateException("Whisper native libs not found"))
             }
             if (ctxPtr == 0L) {
                 ctxPtr = nativeCreateContext(model.absolutePath)
@@ -75,99 +80,107 @@ class WhisperSttEngine(private val context: Context) {
             val pcm = readWavPcm16Mono(audioFile)
                 ?: return Result.failure(IllegalStateException("Invalid WAV for Whisper"))
 
             if (pcm.isEmpty()) {
                 return Result.failure(IllegalStateException("PCM empty"))
             }
 
             // Convert to float32 normalized [-1, 1]
             val floats = FloatArray(pcm.size)
             for (i in pcm.indices) {
                 floats[i] = pcm[i] / 32768f
             }
 
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
+        } catch (e: Throwable) {
+            Log.e(TAG, "Whisper transcribe fatal error: ${e.message}", e)
+            Result.failure(IllegalStateException("Whisper failed to initialize", e))
         }
     }
 
     private fun loadLibrariesIfPresent(): Boolean {
         if (libsLoaded.get()) return true
+        return try {
 
-        fun tryLoadFrom(libDir: File): Boolean {
-            val ggmlCandidates = listOf("libggml-cpu.so", "libggml.so")
-            val core = File(libDir, "libwhisper.so")
-            val jni = File(libDir, "libwhisper_jni.so")
-            if (!core.exists() || !jni.exists()) return false
-            val ggml = ggmlCandidates.map { File(libDir, it) }.firstOrNull { it.exists() } ?: return false
-            return try {
-                System.load(ggml.absolutePath)
-                System.load(core.absolutePath)
-                System.load(jni.absolutePath)
-                libsLoaded.set(true)
-                Log.d(TAG, "✅ Whisper native libs loaded from ${libDir.absolutePath} (${ggml.name})")
-                true
-            } catch (e: Throwable) {
-                Log.e(TAG, "Failed loading Whisper libs from ${libDir.absolutePath}: ${e.message}", e)
-                false
+            fun tryLoadFrom(libDir: File): Boolean {
+                val ggmlCandidates = listOf("libggml-cpu.so", "libggml.so")
+                val core = File(libDir, "libwhisper.so")
+                val jni = File(libDir, "libwhisper_jni.so")
+                if (!core.exists() || !jni.exists()) return false
+                val ggml = ggmlCandidates.map { File(libDir, it) }.firstOrNull { it.exists() } ?: return false
+                return try {
+                    System.load(ggml.absolutePath)
+                    System.load(core.absolutePath)
+                    System.load(jni.absolutePath)
+                    libsLoaded.set(true)
+                    Log.d(TAG, "✅ Whisper native libs loaded from ${libDir.absolutePath} (${ggml.name})")
+                    true
+                } catch (e: Throwable) {
+                    Log.e(TAG, "Failed loading Whisper libs from ${libDir.absolutePath}: ${e.message}", e)
+                    false
+                }
             }
-        }
 
-        // Try filesDir copies for any supported ABI (if caller pre-copied).
-        val supportedAbis = Build.SUPPORTED_ABIS.toList()
-        val candidateAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
-        for (abi in candidateAbis) {
-            if (!supportedAbis.contains(abi)) continue
-            val dir = File(context.filesDir, "whisper_native/$abi")
-            if (tryLoadFrom(dir)) return true
-        }
+            // Try filesDir copies for any supported ABI (if caller pre-copied).
+            val supportedAbis = Build.SUPPORTED_ABIS.toList()
+            val candidateAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
+            for (abi in candidateAbis) {
+                if (!supportedAbis.contains(abi)) continue
+                val dir = File(context.filesDir, "whisper_native/$abi")
+                if (tryLoadFrom(dir)) return true
+            }
 
-        // Fallback to bundled jniLibs (System.loadLibrary) with explicit load order.
-        val ggmlLibNames = listOf("ggml-cpu", "ggml")
-        for (ggmlName in ggmlLibNames) {
-            try {
-                System.loadLibrary(ggmlName)
-                System.loadLibrary("whisper")
-                System.loadLibrary("whisper_jni")
-                libsLoaded.set(true)
-                Log.d(TAG, "✅ Whisper native libs loaded from bundled jniLibs ($ggmlName)")
-                return true
-            } catch (e: Throwable) {
-                Log.w(TAG, "Whisper native libs not found or failed to load ($ggmlName): ${e.message}")
+            // Fallback to bundled jniLibs (System.loadLibrary) with explicit load order.
+            val ggmlLibNames = listOf("ggml-cpu", "ggml")
+            for (ggmlName in ggmlLibNames) {
+                try {
+                    System.loadLibrary(ggmlName)
+                    System.loadLibrary("whisper")
+                    System.loadLibrary("whisper_jni")
+                    libsLoaded.set(true)
+                    Log.d(TAG, "✅ Whisper native libs loaded from bundled jniLibs ($ggmlName)")
+                    return true
+                } catch (e: Throwable) {
+                    Log.w(TAG, "Whisper native libs not found or failed to load ($ggmlName): ${e.message}")
+                }
             }
+            false
+        } catch (e: Throwable) {
+            Log.w(TAG, "Whisper native libs load failed: ${e.message}")
+            false
         }
-        return false
     }
 
     private fun ensureModelAvailable(): File? {
         val dir = File(context.filesDir, "whisper_models")
         val f = File(dir, "whisper-tiny-q5_1.gguf")
         if (f.exists()) return f
         return try {
             // Try to copy from assets if present
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
