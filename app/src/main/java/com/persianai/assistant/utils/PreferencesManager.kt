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

    companion object {
        private const val PREFS_NAME = "ai_assistant_prefs"
        private const val KEY_API_KEYS = "api_keys"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_WORKING_MODE = "working_mode"
        private const val KEY_WELCOME_COMPLETED = "welcome_completed"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_OFFLINE_MODEL_DOWNLOADED = "offline_model_downloaded"
        
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
        val modeName = prefs.getString(KEY_WORKING_MODE, WorkingMode.HYBRID.name)
        return try {
            WorkingMode.valueOf(modeName!!)
        } catch (e: Exception) {
            WorkingMode.HYBRID
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
}
