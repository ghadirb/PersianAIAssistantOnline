package com.persianai.assistant.models

import android.content.Context
import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * مدیریت دانلود و استفاده از مدل‌های آفلاین
 */
class OfflineModelManager(private val context: Context) {
    
    companion object {
        // لینک‌های واقعی مدل‌ها از Hugging Face - آپدیت شده
        const val MODEL_SMALL_URL = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
        const val MODEL_MEDIUM_URL = "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf"
        const val MODEL_LARGE_URL = "https://huggingface.co/TheBloke/Llama-2-7B-Chat-GGUF/resolve/main/llama-2-7b-chat.Q4_K_M.gguf"
        
        // اندازه تقریبی مدل‌ها
        const val MODEL_SMALL_SIZE = 0.6f // GB - TinyLlama
        const val MODEL_MEDIUM_SIZE = 4.1f // GB - Mistral 7B
        const val MODEL_LARGE_SIZE = 3.8f // GB - Llama 2 7B
    }
    
    data class ModelInfo(
        val name: String,
        val url: String,
        val size: Float,
        val description: String,
        val features: List<String>
    )
    
    val availableModels = listOf(
        ModelInfo(
            name = "TinyLlama 1.1B",
            url = MODEL_SMALL_URL,
            size = MODEL_SMALL_SIZE,
            description = "مدل بسیار سبک و سریع برای گوشی‌های ضعیف",
            features = listOf(
                "پاسخ‌های فوق سریع",
                "مصرف RAM بسیار کم (2GB)",
                "مناسب برای همه گوشی‌ها",
                "کیفیت مناسب با وجود حجم کم"
            )
        ),
        ModelInfo(
            name = "Mistral 7B Instruct",
            url = MODEL_MEDIUM_URL,
            size = MODEL_MEDIUM_SIZE,
            description = "مدل قدرتمند و محبوب با عملکرد عالی",
            features = listOf(
                "بهترین نسبت کیفیت به حجم",
                "مصرف RAM متوسط (6GB)",
                "سرعت بالا و دقت خوب",
                "پشتیبانی چندزبانه"
            )
        ),
        ModelInfo(
            name = "Llama 2 7B Chat",
            url = MODEL_LARGE_URL,
            size = MODEL_LARGE_SIZE,
            description = "مدل استاندارد Meta برای چت",
            features = listOf(
                "محبوب‌ترین مدل چت",
                "پشتیبانی از دستورات پیچیده",
                "کیفیت بالا در پاسخ‌ها",
                "آموزش دیده روی داده‌های متنوع"
            )
        )
    )
    
    private val _downloadProgress = MutableLiveData<Float>()
    val downloadProgress: LiveData<Float> = _downloadProgress
    
    private val _downloadStatus = MutableLiveData<String>()
    val downloadStatus: LiveData<String> = _downloadStatus
    
    private val _isDownloading = MutableLiveData<Boolean>()
    val isDownloading: LiveData<Boolean> = _isDownloading
    
    private var downloadJob: Job? = null
    
    /**
     * دانلود مدل
     */
    fun downloadModel(modelInfo: ModelInfo, onComplete: (Boolean) -> Unit) {
        if (_isDownloading.value == true) {
            _downloadStatus.value = "⚠️ در حال دانلود مدل دیگری..."
            return
        }
        
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    _isDownloading.value = true
                    _downloadProgress.value = 0f
                    _downloadStatus.value = "🔄 شروع دانلود ${modelInfo.name}..."
                }
                
                val modelDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "models")
                if (!modelDir.exists()) {
                    modelDir.mkdirs()
                }
                
                val modelFile = File(modelDir, "${modelInfo.name.replace(" ", "_")}.gguf")
                
                // حجم مورد انتظار (تبدیل GB به بایت)
                val expectedSize = (modelInfo.size * 1024 * 1024 * 1024).toLong()
                
                // اگر فایل وجود داره، چک کن کامل دانلود شده یا نه
                if (modelFile.exists() && modelFile.length() >= expectedSize * 0.95) { // حداقل 95% حجم
                    withContext(Dispatchers.Main) {
                        _downloadStatus.value = "✅ مدل قبلاً دانلود شده!"
                        _downloadProgress.value = 100f
                        _isDownloading.value = false
                        onComplete(true)
                    }
                    return@launch
                } else if (modelFile.exists()) {
                    // اگر فایل ناقص است، حذف کن
                    modelFile.delete()
                    withContext(Dispatchers.Main) {
                        _downloadStatus.value = "🗑️ حذف فایل ناقص قبلی..."
                    }
                    delay(1000)
                }
                
                // استفاده از OkHttp برای دانلود بهتر
                android.util.Log.d("OfflineModelManager", "Downloading from: ${modelInfo.url}")
                android.util.Log.d("OfflineModelManager", "Saving to: ${modelFile.absolutePath}")
                
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                
                val request = Request.Builder()
                    .url(modelInfo.url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw IOException("Download failed: ${response.code}")
                }
                
                val body = response.body
                if (body == null) {
                    throw IOException("Response body is null")
                }
                
                val fileLength = body.contentLength()
                android.util.Log.d("OfflineModelManager", "File size: $fileLength bytes")
                
                if (fileLength < 1000000 && fileLength > 0) { // Less than 1MB - suspicious
                    throw IOException("File size too small: $fileLength bytes")
                }
                
                val input = BufferedInputStream(body.byteStream())
                val output = FileOutputStream(modelFile)
                
                val buffer = ByteArray(8192)
                var total = 0L
                var count: Int
                
                while (input.read(buffer).also { count = it } != -1) {
                    if (!isActive) break
                    
                    total += count
                    output.write(buffer, 0, count)
                    
                    val progress = (total * 100f / fileLength)
                    withContext(Dispatchers.Main) {
                        _downloadProgress.value = progress
                        _downloadStatus.value = "📥 دانلود: ${progress.roundToInt()}% (${formatSize(total)}/${formatSize(fileLength.toLong())})"
                    }
                }
                
                output.flush()
                output.close()
                input.close()
                
                withContext(Dispatchers.Main) {
                    if (isActive) {
                        _downloadStatus.value = "✅ دانلود کامل شد!"
                        _isDownloading.value = false
                        onComplete(true)
                        
                        // ذخیره اطلاعات مدل
                        saveModelInfo(modelInfo, modelFile.absolutePath)
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _downloadStatus.value = "❌ خطا در دانلود: ${e.message}"
                    _isDownloading.value = false
                    onComplete(false)
                }
            }
        }
    }
    
    /**
     * لغو دانلود
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        _isDownloading.value = false
        _downloadStatus.value = "⛔ دانلود لغو شد"
    }
    
    /**
     * ذخیره اطلاعات مدل
     */
    private fun saveModelInfo(modelInfo: ModelInfo, path: String) {
        val prefs = context.getSharedPreferences("offline_models", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val json = JSONObject().apply {
            put("name", modelInfo.name)
            put("path", path)
            put("size", modelInfo.size)
            put("description", modelInfo.description)
            put("downloadDate", System.currentTimeMillis())
        }
        
        editor.putString(modelInfo.name, json.toString())
        editor.apply()
    }
    
    /**
     * دریافت مدل‌های دانلود شده
     */
    fun getDownloadedModels(): List<Pair<ModelInfo, String>> {
        // ابتدا اسکن پوشه برای مدل‌های دستی
        scanForManuallyDownloadedModels()
        
        val prefs = context.getSharedPreferences("offline_models", Context.MODE_PRIVATE)
        val downloadedModels = mutableListOf<Pair<ModelInfo, String>>()
        
        for (model in availableModels) {
            val modelData = prefs.getString(model.name, null)
            if (modelData != null) {
                try {
                    val json = JSONObject(modelData)
                    val path = json.getString("path")
                    if (File(path).exists()) {
                        val fileSize = File(path).length()
                        val expectedSize = (model.size * 1024 * 1024 * 1024).toLong()
                        
                        // چک حجم فایل - باید حداقل 80% باشد
                        if (fileSize >= expectedSize * 0.8) {
                            downloadedModels.add(Pair(model, path))
                            android.util.Log.d("OfflineModelManager", "✅ Valid model: ${model.name} - ${formatSize(fileSize)}")
                        } else {
                            android.util.Log.w("OfflineModelManager", "⚠️ Invalid size: ${model.name} - ${formatSize(fileSize)} (expected: ${formatSize(expectedSize)})")
                        }
                    } else {
                        android.util.Log.w("OfflineModelManager", "❌ File not found: $path")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        return downloadedModels
    }
    
    /**
     * اسکن پوشه برای شناسایی مدل‌های دستی
     */
    private fun scanForManuallyDownloadedModels() {
        val modelDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
            android.util.Log.d("OfflineModelManager", "Created models directory: ${modelDir.absolutePath}")
            return
        }
        
        android.util.Log.d("OfflineModelManager", "Scanning: ${modelDir.absolutePath}")
        
        val files = modelDir.listFiles() ?: return
        val prefs = context.getSharedPreferences("offline_models", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        for (file in files) {
            if (!file.name.endsWith(".gguf")) continue
            
            android.util.Log.d("OfflineModelManager", "Found file: ${file.name} - ${formatSize(file.length())}")
            
            // تطبیق با مدل‌ها
            for (model in availableModels) {
                val expectedFileName = "${model.name.replace(" ", "_")}.gguf"
                
                if (file.name.equals(expectedFileName, ignoreCase = true)) {
                    val fileSize = file.length()
                    val expectedSize = (model.size * 1024 * 1024 * 1024).toLong()
                    
                    // چک حجم - باید حداقل 80% باشد
                    if (fileSize >= expectedSize * 0.8) {
                        // ذخیره در SharedPreferences
                        val json = JSONObject().apply {
                            put("name", model.name)
                            put("path", file.absolutePath)
                            put("size", model.size)
                            put("description", model.description)
                            put("downloadDate", System.currentTimeMillis())
                            put("manualDownload", true)
                        }
                        
                        editor.putString(model.name, json.toString())
                        android.util.Log.d("OfflineModelManager", "✅ Registered: ${model.name} - ${file.absolutePath}")
                    } else {
                        android.util.Log.w("OfflineModelManager", "⚠️ Size too small: ${file.name} - ${formatSize(fileSize)} (expected: ${formatSize(expectedSize)})")
                    }
                    break
                }
            }
        }
        
        editor.apply()
    }
    
    /**
     * حذف مدل
     */
    fun deleteModel(modelName: String): Boolean {
        val prefs = context.getSharedPreferences("offline_models", Context.MODE_PRIVATE)
        val modelData = prefs.getString(modelName, null) ?: return false
        
        try {
            val json = JSONObject(modelData)
            val path = json.getString("path")
            val file = File(path)
            
            if (file.exists() && file.delete()) {
                prefs.edit().remove(modelName).apply()
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }
    
    /**
     * فرمت کردن اندازه فایل
     */
    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes B"
        }
    }
    
    /**
     * بررسی فضای خالی
     */
    fun hasEnoughSpace(modelSize: Float): Boolean {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val freeSpace = downloadsDir?.freeSpace ?: 0L
        val requiredSpace = (modelSize * 1024 * 1024 * 1024 * 1.2).toLong() // 20% اضافی
        
        return freeSpace > requiredSpace
    }
}
