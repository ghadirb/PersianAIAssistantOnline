package com.persianai.assistant.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.models.ChatMessage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ذخیره‌سازی امن پیام‌های چت با AES/GCM و کلید در Keystore
 */
class SecureMessageStorage(context: Context) {

    private val prefs = context.getSharedPreferences("secure_chat_storage", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "chat_memory_key"
        private const val PREF_MESSAGES = "chat_messages_enc"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    /**
     * ذخیره کامل پیام‌ها به‌صورت رمزگذاری‌شده
     */
    fun saveMessages(messages: List<ChatMessage>) {
        try {
            val json = gson.toJson(messages)
            val encrypted = encrypt(json.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(PREF_MESSAGES, encrypted).apply()
        } catch (_: Exception) {
            // در صورت خطا، داده ذخیره نمی‌شود تا از خراب شدن جلوگیری شود
        }
    }

    /**
     * بارگذاری پیام‌ها (در صورت خطا لیست خالی برمی‌گردد)
     */
    fun loadMessages(): List<ChatMessage> {
        val enc = prefs.getString(PREF_MESSAGES, null) ?: return emptyList()
        return try {
            val plain = decrypt(enc)
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson<List<ChatMessage>>(plain, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(data: ByteArray): String {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String {
        val combined = Base64.decode(enc, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        val plain = cipher.doFinal(ciphertext)
        return String(plain, Charsets.UTF_8)
    }
}
