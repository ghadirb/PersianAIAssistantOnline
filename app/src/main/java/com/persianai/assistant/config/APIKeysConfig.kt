package com.persianai.assistant.config

import android.content.Context
import android.util.Log
import java.io.File

/**
 * مدیریت کلیدهای API از فایل خارجی محافظ‌شده
 */
class APIKeysConfig(private val context: Context) {
    
    companion object {
        const val KEYS_FILENAME = "api_keys.encrypted"
        const val KEYS_BACKUP = "api_keys.backup"
    }

    /**
     * دریافت مسیر فایل کلیدها
     */
    fun getKeysFilePath(): String {
        val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(filesDir, KEYS_FILENAME).absolutePath
    }

    /**
     * ذخیره کلیدهای رمزشده
     */
    fun saveEncryptedKeys(encryptedData: String): Boolean {
        return try {
            val file = File(getKeysFilePath())
            file.writeText(encryptedData)
            true
        } catch (e: Exception) {
            Log.e("APIKeysConfig", "Error saving keys", e)
            false
        }
    }

    /**
     * دریافت کلیدهای رمزشده
     */
    fun getEncryptedKeys(): String? {
        return try {
            val file = File(getKeysFilePath())
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            Log.e("APIKeysConfig", "Error reading keys", e)
            null
        }
    }

    /**
     * حذف کلیدها (برای logout)
     */
    fun clearKeys(): Boolean {
        return try {
            val file = File(getKeysFilePath())
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            Log.e("APIKeysConfig", "Error clearing keys", e)
            false
        }
    }

    /**
     * بررسی اینکه آیا کلیدها موجود هستند
     */
    fun hasKeys(): Boolean {
        return File(getKeysFilePath()).exists()
    }

    /**
     * ایجاد نسخه پشتیبانی از کلیدها
     */
    fun createBackup(): Boolean {
        return try {
            val original = File(getKeysFilePath())
            val backup = File(original.parent, KEYS_BACKUP)
            original.copyTo(backup, overwrite = true)
            true
        } catch (e: Exception) {
            Log.e("APIKeysConfig", "Error creating backup", e)
            false
        }
    }

    /**
     * بازیابی از نسخه پشتیبانی
     */
    fun restoreFromBackup(): Boolean {
        return try {
            val backup = File(context.getExternalFilesDir(null) ?: context.filesDir, KEYS_BACKUP)
            val original = File(getKeysFilePath())
            
            if (!backup.exists()) return false
            
            backup.copyTo(original, overwrite = true)
            true
        } catch (e: Exception) {
            Log.e("APIKeysConfig", "Error restoring from backup", e)
            false
        }
    }
}