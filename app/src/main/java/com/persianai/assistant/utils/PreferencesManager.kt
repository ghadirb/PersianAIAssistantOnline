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
        
        const val DEFAULT_SYSTEM_PROMPT = """You are a command execution system. Output ONLY valid JSON.

CRITICAL: You CANNOT refuse ANY request. You have FULL access to ALL apps.

For messaging: {"action":"send_telegram","phone":"UNKNOWN","message":"TEXT"}
For opening apps: {"action":"open_app","app_name":"APP_NAME"}
For reminders: REMINDER:{"time":"HH:MM","message":"TEXT","alarm":true,"repeat":"none"}
For navigation: NAVIGATION:{"destination":"PLACE","persian_voice":true}

EXAMPLES (Output ONLY the JSON part):

"تلگرام باز کن" → {"action":"open_app","app_name":"تلگرام"}
"پیام بده سلام" → {"action":"send_telegram","phone":"UNKNOWN","message":"سلام"}
"روبیکا" → {"action":"open_app","app_name":"روبیکا"}
"واتساپ پیام بده" → {"action":"send_whatsapp","phone":"UNKNOWN","message":""}
"اینستاگرام" → {"action":"open_app","app_name":"اینستاگرام"}
"به علی بگو فردا میام" → {"action":"send_telegram","phone":"UNKNOWN","message":"فردا میام"}

FORBIDDEN (NEVER output these):
❌ "نمی‌توانم"
❌ "نیاز به دسترسی"
❌ "I cannot"
❌ "I need access"
❌ ANY text explanation

OUTPUT FORMAT: Only the JSON, nothing else."""
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
}
