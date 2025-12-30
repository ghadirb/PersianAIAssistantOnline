package com.persianai.assistant.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * کلاس کمکی برای دانلود فایل از Google Drive
 */
object DriveHelper {

    private const val DRIVE_DOWNLOAD_URL = "https://drive.google.com/uc?export=download&id="
    private const val ENCRYPTED_KEYS_FILE_ID = "17iwkjyGcxJeDgwQWEcsOdfbOxOah_0u0"
    private const val GIST_KEYS_URL =
        "https://gist.githubusercontent.com/ghadirb/626a804df3009e49045a2948dad89fe5/raw/5ec50251e01128e0ad8d380350a2002d5c5b585f/keys.txt"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * دانلود فایل رمزگذاری شده کلیدها از Google Drive
     */
    suspend fun downloadEncryptedKeys(): String = withContext(Dispatchers.IO) {
        // تلاش اول: لینک گیت‌هاب (gist)
        runCatching { downloadFromUrl(GIST_KEYS_URL) }.getOrElse {
            // fallback: Google Drive
            val url = DRIVE_DOWNLOAD_URL + ENCRYPTED_KEYS_FILE_ID
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("خطا در دانلود: ${response.code}")
                }
                response.body?.string() ?: throw IOException("پاسخ خالی است")
            }
        }
    }

    /**
     * دانلود فایل از URL مستقیم
     */
    suspend fun downloadFromUrl(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("خطا در دانلود: ${response.code}")
            }
            response.body?.string() ?: throw IOException("پاسخ خالی است")
        }
    }

    /**
     * آپلود فایل به Google Drive (نیاز به احراز هویت دارد)
     * این متد در نسخه‌های بعدی با Google Drive API کامل می‌شود
     */
    suspend fun uploadBackupToDrive(content: String, fileName: String): Boolean {
        // TODO: پیاده‌سازی آپلود با Google Drive API
        return false
    }
}
