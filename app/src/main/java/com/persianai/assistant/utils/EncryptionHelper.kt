package com.persianai.assistant.utils

import android.util.Base64
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * کلاس رمزگشایی کلیدهای API که با اسکریپت Python رمزگذاری شده‌اند
 */
object EncryptionHelper {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val SECRET_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 20000  // Must match Python script (20,000 not 200,000)
    private const val KEY_LENGTH = 256
    private const val GCM_TAG_LENGTH = 128
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12

    /**
     * رمزگشایی متن رمزشده با استفاده از رمز عبور
     * 
     * @param encryptedBase64 متن رمزشده به صورت Base64
     * @param password رمز عبور
     * @return متن رمزگشایی شده
     */
    fun decrypt(encryptedBase64: String, password: String): String {
        try {
            // Decode Base64
            val encryptedData = Base64.decode(encryptedBase64, Base64.DEFAULT)
            
            // Extract salt (first 16 bytes)
            val salt = encryptedData.copyOfRange(0, SALT_LENGTH)
            
            // Extract IV/nonce (next 12 bytes)
            val iv = encryptedData.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
            
            // Extract ciphertext (remaining bytes)
            val ciphertext = encryptedData.copyOfRange(SALT_LENGTH + IV_LENGTH, encryptedData.size)
            
            // Derive key from password
            val keySpec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val secretKeyFactory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_ALGORITHM)
            val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(keyBytes, KEY_ALGORITHM)
            
            // Decrypt
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
            val decryptedBytes = cipher.doFinal(ciphertext)
            
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw DecryptionException("خطا در رمزگشایی: ${e.message}", e)
        }
    }

    /**
     * استثناء رمزگشایی
     */
    class DecryptionException(message: String, cause: Throwable) : Exception(message, cause)
}
