package com.persianai.assistant.utils

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelDownloadManager(private val context: Context) {

    data class ModelInfo(val type: PreferencesManager.OfflineModelType, val name: String, val url: String, val fileName: String, val sizeHint: String)

    companion object {
        val qwen15 = ModelInfo(
            PreferencesManager.OfflineModelType.FULL,
            "Qwen2.5 1.5B Instruct",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "≈1.0 GB"
        )
        val qwen05 = ModelInfo(
            PreferencesManager.OfflineModelType.LITE,
            "Qwen2.5 0.5B Instruct",
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "≈550 MB"
        )
        val tinyLlama = ModelInfo(
            PreferencesManager.OfflineModelType.BASIC,
            "TinyLlama 1.1B Chat",
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            "≈410 MB"
        )

        fun detectRecommendedModel(context: Context): PreferencesManager.OfflineModelType {
            return try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val info = ActivityManager.MemoryInfo()
                am.getMemoryInfo(info)
                val total = info.totalMem / (1024 * 1024) // MB
                when {
                    total >= 5500 -> PreferencesManager.OfflineModelType.FULL // Qwen 1.5B
                    total >= 3200 -> PreferencesManager.OfflineModelType.LITE // Qwen 0.5B
                    else -> PreferencesManager.OfflineModelType.BASIC // TinyLlama
                }
            } catch (_: Exception) {
                PreferencesManager.OfflineModelType.BASIC
            }
        }
    }

    fun getModelDir(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "models").apply { mkdirs() }

    fun getModelInfo(type: PreferencesManager.OfflineModelType): ModelInfo = when (type) {
        PreferencesManager.OfflineModelType.FULL -> qwen15
        PreferencesManager.OfflineModelType.LITE -> qwen05
        PreferencesManager.OfflineModelType.BASIC -> tinyLlama
    }

    fun getModelFile(info: ModelInfo): File = File(getModelDir(), info.fileName)

    fun isModelDownloaded(info: ModelInfo): Boolean = File(getModelDir(), info.fileName).exists()

    fun findDownloadedModel(preferred: PreferencesManager.OfflineModelType? = null): ModelInfo? {
        val ordered = listOf(qwen15, qwen05, tinyLlama)
        // 1) Preferred if present
        preferred?.let { pref ->
            val prefInfo = getModelInfo(pref)
            if (isModelDownloaded(prefInfo)) return prefInfo
        }
        // 2) Any downloaded (full -> lite -> basic)
        return ordered.firstOrNull { isModelDownloaded(it) }
    }

    /**
     * دانلود مدل به صورت پس‌زمینه با DownloadManager
     */
    fun enqueueDownload(info: ModelInfo): Long {
        val request = DownloadManager.Request(Uri.parse(info.url))
            .setTitle("دانلود ${info.name}")
            .setDescription("حجم ${info.sizeHint}")
            .setDestinationUri(Uri.fromFile(File(getModelDir(), info.fileName)))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    fun deleteModel(info: ModelInfo) {
        File(getModelDir(), info.fileName).delete()
    }
}
