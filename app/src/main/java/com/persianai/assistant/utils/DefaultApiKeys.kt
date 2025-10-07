package com.persianai.assistant.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

/**
 * کلیدهای پیش‌فرض API به صورت رمزگذاری شده
 */
object DefaultApiKeys {
    
    private const val ENCRYPTION_KEY = "PersianAI2024Key"  // 16 کاراکتر
    private const val ENCRYPTION_IV = "InitVector123456"    // 16 کاراکتر
    
    // کلیدهای رمزگذاری شده - لطفاً کلیدهای خود را جایگزین کنید
    private val ENCRYPTED_KEYS = mapOf(
        "openai_1" to encryptKey("YOUR_OPENAI_API_KEY_HERE"),  // کلید OpenAI خود را اینجا قرار دهید
        "aimlapi_1" to encryptKey("YOUR_AIML_API_KEY_HERE"),   // کلید AIML خود را اینجا قرار دهید
        "openrouter_1" to encryptKey("YOUR_OPENROUTER_KEY_HERE")  // کلید OpenRouter خود را اینجا قرار دهید
    )
    
    /**
     * دریافت کلید OpenAI برای Whisper
     */
    fun getOpenAIKey(): String? {
        return try {
            decryptKey(ENCRYPTED_KEYS["openai_1"] ?: return null)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * دریافت کلید AIML API
     */
    fun getAIMLKey(): String? {
        return try {
            decryptKey(ENCRYPTED_KEYS["aimlapi_1"] ?: return null)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * دریافت کلید OpenRouter
     */
    fun getOpenRouterKey(): String? {
        return try {
            decryptKey(ENCRYPTED_KEYS["openrouter_1"] ?: return null)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * رمزگذاری کلید
     */
    private fun encryptKey(key: String): String {
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(ENCRYPTION_KEY.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(ENCRYPTION_IV.toByteArray())
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encrypted = cipher.doFinal(key.toByteArray())
            
            return Base64.encodeToString(encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            return key
        }
    }
    
    /**
     * رمزگشایی کلید
     */
    private fun decryptKey(encryptedKey: String): String {
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(ENCRYPTION_KEY.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(ENCRYPTION_IV.toByteArray())
            
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decrypted = cipher.doFinal(Base64.decode(encryptedKey, Base64.DEFAULT))
            
            return String(decrypted)
        } catch (e: Exception) {
            return ""
        }
    }
    
    /**
     * بررسی و استفاده از کلید پیش‌فرض اگر کاربر کلید ندارد
     */
    fun initializeDefaultKeys(context: android.content.Context) {
        val prefs = context.getSharedPreferences("api_keys", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // اگر کلید OpenAI ندارد، کلید پیش‌فرض را قرار بده
        if (prefs.getString("openai_api_key", null).isNullOrEmpty()) {
            getOpenAIKey()?.let {
                editor.putString("openai_api_key", it)
                editor.putBoolean("openai_is_default", true)
            }
        }
        
        // اگر کلید AIML ندارد
        if (prefs.getString("aiml_api_key", null).isNullOrEmpty()) {
            getAIMLKey()?.let {
                editor.putString("aiml_api_key", it)
                editor.putBoolean("aiml_is_default", true)
            }
        }
        
        editor.apply()
    }
}
