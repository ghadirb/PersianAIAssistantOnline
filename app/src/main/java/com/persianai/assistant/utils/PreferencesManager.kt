package com.persianai.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.APIKey

/**
 * مدیریت تنظیمات برنامه
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val base64Flags = android.util.Base64.NO_WRAP

    companion object {
        private const val PREFS_NAME = "ai_assistant_prefs"
        private const val KEY_API_KEYS = "api_keys"
        private const val KEY_PROVISIONING_KEY = "provisioning_key_enc"
        private const val KEY_AUTO_PROVISIONING = "auto_provisioning"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_WORKING_MODE = "working_mode"
        private const val KEY_PROVIDER_PREF = "provider_pref"
        private const val KEY_WELCOME_COMPLETED = "welcome_completed"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_OFFLINE_MODEL_DOWNLOADED = "offline_model_downloaded"
        private const val KEY_PARENTAL_ENABLED = "parental_enabled"
        private const val KEY_PARENTAL_KEYWORDS = "parental_keywords"
        
        const val DEFAULT_SYSTEM_PROMPT = """OUTPUT ONLY JSON. NO TEXT.

ACTIONS:
{"action":"open_app","app_name":"NAME"}
{"action":"send_telegram","phone":"NUMBER","message":"TEXT"}
{"action":"send_whatsapp","phone":"NUMBER","message":"TEXT"}
{"action":"send_sms","phone":"NUMBER","message":"TEXT"}

EXAMPLES:
"تلگرام باز کن" → {"action":"open_app","app_name":"تلگرام"}
"پیام بده سلام" → {"action":"send_telegram","phone":"UNKNOWN","message":"سلام"}
"به احمد بگو برگشتم" → {"action":"send_telegram","phone":"UNKNOWN","message":"برگشتم"}
"پیامک بفرست در راهم" → {"action":"send_sms","phone":"UNKNOWN","message":"در راهم"}
"واتساپ بفرست رسیدم" → {"action":"send_whatsapp","phone":"UNKNOWN","message":"رسیدم"}

If no phone number, use "UNKNOWN".
NEVER: "متاسفانه", "نمیتوانم"
JSON ONLY."""
    }

    fun saveAPIKeys(keys: List<APIKey>) {
        val json = gson.toJson(keys)
        prefs.edit().putString(KEY_API_KEYS, json).apply()
    }

    fun getAPIKeys(): List<APIKey> {
        val json = prefs.getString(KEY_API_KEYS, null) ?: return emptyList()
        val type = object : TypeToken<List<APIKey>>() {}.type
        return gson.fromJson(json, type)
    }

    fun hasAPIKeys(): Boolean {
        return prefs.contains(KEY_API_KEYS)
    }

    /**
     * Provisioning key (ذخیره به صورت Base64 برای جلوگیری از لاگ ساده)
     */
    fun saveProvisioningKey(key: String) {
        if (key.isBlank()) {
            prefs.edit().remove(KEY_PROVISIONING_KEY).apply()
        } else {
            val enc = android.util.Base64.encodeToString(key.trim().toByteArray(Charsets.UTF_8), base64Flags)
            prefs.edit().putString(KEY_PROVISIONING_KEY, enc).apply()
        }
    }

    fun getProvisioningKey(): String? {
        val enc = prefs.getString(KEY_PROVISIONING_KEY, null) ?: return null
        return try {
            String(android.util.Base64.decode(enc, base64Flags), Charsets.UTF_8).trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    fun setAutoProvisioning(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PROVISIONING, enabled).apply()
    }

    fun isAutoProvisioningEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_PROVISIONING, true)
    }

    fun clearAPIKeys() {
        prefs.edit().remove(KEY_API_KEYS).apply()
    }

    fun saveSelectedModel(model: AIModel) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model.modelId).apply()
    }

    fun getSelectedModel(): AIModel {
        val modelId = prefs.getString(KEY_SELECTED_MODEL, null)
        return if (modelId != null) {
            AIModel.fromModelId(modelId) ?: AIModel.getDefaultModel()
        } else {
            AIModel.getDefaultModel()
        }
    }

    fun saveSystemPrompt(prompt: String) {
        // غیرفعال - system prompt hardcoded است
    }

    fun getSystemPrompt(): String {
        // همیشه از DEFAULT_SYSTEM_PROMPT استفاده می‌کند
        return DEFAULT_SYSTEM_PROMPT
    }

    fun saveTemperature(temperature: Float) {
        prefs.edit().putFloat(KEY_TEMPERATURE, temperature).apply()
    }

    fun getTemperature(): Float {
        return prefs.getFloat(KEY_TEMPERATURE, 0.3f)  // کم برای دقت بالا در اجرای دستورات
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    // Working Mode
    enum class WorkingMode {
        ONLINE,    // فقط آنلاین با API
        OFFLINE,   // فقط آفلاین با مدل محلی
        HYBRID     // ترکیبی (پیشنهادی)
    }

    fun setWorkingMode(mode: WorkingMode) {
        prefs.edit().putString(KEY_WORKING_MODE, mode.name).apply()
    }

    fun getWorkingMode(): WorkingMode {
        // پیش‌فرض را کاملاً آفلاین می‌گیریم تا همه ضبط‌ها و مکالمات بدون اینترنت کار کنند
        val modeName = prefs.getString(KEY_WORKING_MODE, WorkingMode.OFFLINE.name)
        return try {
            WorkingMode.valueOf(modeName!!)
        } catch (e: Exception) {
            WorkingMode.OFFLINE
        }
    }

    enum class ProviderPreference {
        AUTO,
        SMART_ROUTE, // OpenRouter بدون نام بردن
        OPENAI_ONLY
    }

    fun setProviderPreference(pref: ProviderPreference) {
        prefs.edit().putString(KEY_PROVIDER_PREF, pref.name).apply()
    }

    fun getProviderPreference(): ProviderPreference {
        val name = prefs.getString(KEY_PROVIDER_PREF, ProviderPreference.AUTO.name)
        return try {
            ProviderPreference.valueOf(name!!)
        } catch (_: Exception) {
            ProviderPreference.AUTO
        }
    }

    fun setWelcomeCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_WELCOME_COMPLETED, completed).apply()
    }

    fun hasCompletedWelcome(): Boolean {
        return prefs.getBoolean(KEY_WELCOME_COMPLETED, false)
    }

    // TTS
    fun setTTSEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
    }

    fun isTTSEnabled(): Boolean {
        return prefs.getBoolean(KEY_TTS_ENABLED, true) // پیش‌فرض فعال
    }

    // Offline Model
    fun setOfflineModelDownloaded(downloaded: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_MODEL_DOWNLOADED, downloaded).apply()
    }

    fun isOfflineModelDownloaded(): Boolean {
        return prefs.getBoolean(KEY_OFFLINE_MODEL_DOWNLOADED, false)
    }
    
    fun isParentalControlEnabled(): Boolean {
        return prefs.getBoolean(KEY_PARENTAL_ENABLED, false)
    }

    fun setParentalControlEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PARENTAL_ENABLED, enabled).apply()
    }

    fun getBlockedKeywords(): List<String> {
        val json = prefs.getString(KEY_PARENTAL_KEYWORDS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setBlockedKeywords(keywords: List<String>) {
        val clean = keywords.map { it.trim() }.filter { it.isNotEmpty() }
        val json = gson.toJson(clean)
        prefs.edit().putString(KEY_PARENTAL_KEYWORDS, json).apply()
    }

    fun addBlockedKeyword(keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return
        val current = getBlockedKeywords().toMutableList()
        if (current.any { it.equals(clean, ignoreCase = true) }) return
        current.add(clean)
        setBlockedKeywords(current)
    }

    fun removeBlockedKeyword(keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return
        val current = getBlockedKeywords().toMutableList()
        val updated = current.filterNot { it.equals(clean, ignoreCase = true) }
        setBlockedKeywords(updated)
    }
    
    // Offline Model Type
    enum class OfflineModelType(val displayName: String, val size: String, val description: String) {
        BASIC("ساده (پارسر)", "5 MB", "فقط دستورات ساده - بدون هوش مصنوعی"),
        LITE("سبک (Gemini Nano)", "200 MB", "پاسخ به سوالات ساده و محاسبات"),
        FULL("کامل (Llama 3.1)", "2 GB", "قدرتمند مثل ChatGPT - نیاز به موبایل قوی")
    }
    
    fun setOfflineModelType(type: OfflineModelType) {
        prefs.edit().putString("offline_model_type", type.name).apply()
    }
    
    fun getOfflineModelType(): OfflineModelType {
        val typeName = prefs.getString("offline_model_type", OfflineModelType.BASIC.name)
        return try {
            OfflineModelType.valueOf(typeName!!)
        } catch (e: Exception) {
            OfflineModelType.BASIC
        }
    }
    
    /**
     * دریافت مقدار Integer
     */
    fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }
    
    /**
     * دریافت مقدار Double
     */
    fun getDouble(key: String, defaultValue: Double): Double {
        return prefs.getFloat(key, defaultValue.toFloat()).toDouble()
    }
}
