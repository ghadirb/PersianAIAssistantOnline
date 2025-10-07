package com.persianai.assistant.models

import android.content.Context
import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * مدیریت دانلود و استفاده از مدل‌های آفلاین
 */
class OfflineModelManager(private val context: Context) {
    
    companion object {
        // لینک‌های واقعی مدل‌ها از Hugging Face
        const val MODEL_SMALL_URL = "https://huggingface.co/TheBloke/Llama-2-7B-Chat-GGUF/resolve/main/llama-2-7b-chat.Q4_K_M.gguf"
        const val MODEL_MEDIUM_URL = "https://huggingface.co/TheBloke/Llama-2-13B-chat-GGUF/resolve/main/llama-2-13b-chat.Q4_K_M.gguf"
        const val MODEL_LARGE_URL = "https://huggingface.co/TheBloke/CodeLlama-13B-Instruct-GGUF/resolve/main/codellama-13b-instruct.Q4_K_M.gguf"
        
        // اندازه تقریبی مدل‌ها
        const val MODEL_SMALL_SIZE = 3.9f // GB
        const val MODEL_MEDIUM_SIZE = 7.4f // GB
        const val MODEL_LARGE_SIZE = 7.9f // GB
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
            name = "Llama 2 7B Chat",
            url = MODEL_SMALL_URL,
            size = MODEL_SMALL_SIZE,
            description = "مدل سبک و سریع برای چت‌های روزمره",
            features = listOf(
                "پاسخ‌های سریع",
                "مصرف RAM کم (4GB)",
                "مناسب برای گوشی‌های متوسط",
                "پشتیبانی از زبان فارسی (محدود)"
            )
        ),
        ModelInfo(
            name = "Llama 2 13B Chat",
            url = MODEL_MEDIUM_URL,
            size = MODEL_MEDIUM_SIZE,
            description = "مدل متوسط با قابلیت‌های پیشرفته",
            features = listOf(
                "دقت بالا در پاسخ‌ها",
                "مصرف RAM متوسط (8GB)",
                "پشتیبانی بهتر از زبان فارسی",
                "قابلیت درک متون طولانی"
            )
        ),
        ModelInfo(
            name = "CodeLlama 13B",
            url = MODEL_LARGE_URL,
            size = MODEL_LARGE_SIZE,
            description = "مدل قدرتمند برای برنامه‌نویسی",
            features = listOf(
                "تخصص در کدنویسی",
                "پشتیبانی از 20+ زبان برنامه‌نویسی",
                "تولید و Debug کد",
                "توضیحات فنی دقیق"
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
                
                // اگر فایل وجود داره، چک کن کامل دانلود شده یا نه
                if (modelFile.exists() && modelFile.length() > 1000000) {
                    withContext(Dispatchers.Main) {
                        _downloadStatus.value = "✅ مدل قبلاً دانلود شده!"
                        _isDownloading.value = false
                        onComplete(true)
                    }
                    return@launch
                }
                
                val url = URL(modelInfo.url)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()
                
                val fileLength = connection.contentLength
                val input = BufferedInputStream(connection.inputStream)
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
        val prefs = context.getSharedPreferences("offline_models", Context.MODE_PRIVATE)
        val downloadedModels = mutableListOf<Pair<ModelInfo, String>>()
        
        for (model in availableModels) {
            val modelData = prefs.getString(model.name, null)
            if (modelData != null) {
                try {
                    val json = JSONObject(modelData)
                    val path = json.getString("path")
                    if (File(path).exists()) {
                        downloadedModels.add(Pair(model, path))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        return downloadedModels
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
