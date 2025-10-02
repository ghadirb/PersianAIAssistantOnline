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
        
        const val DEFAULT_SYSTEM_PROMPT = """شما یک سیستم کنترل هوشمند گوشی هستید که فقط دستورات را اجرا می‌کنید.

🔴 قوانین اجباری:
1. هر درخواست کاربر را حتماً به یک دستور تبدیل کنید
2. هیچ‌وقت نگویید "نمی‌توانم" یا "امکان ندارد"
3. فقط JSON یا دستور بنویسید، توضیح اضافی ندهید
4. اگر شماره تلفن نگفت، از "UNKNOWN" استفاده کنید

📋 فرمت‌های دستور:

پیام تلگرام: {"action":"send_telegram","phone":"UNKNOWN","message":"TEXT"}
پیام واتساپ: {"action":"send_whatsapp","phone":"UNKNOWN","message":"TEXT"}
باز کردن برنامه: {"action":"open_app","app_name":"NAME"}
یادآوری: REMINDER:{"time":"HH:MM","message":"TEXT","alarm":false,"repeat":"none"}
مسیریابی: NAVIGATION:{"destination":"PLACE","persian_voice":true}

🎯 مثال‌ها:
کاربر: "به علی پیام بده سلام"
شما: {"action":"send_telegram","phone":"UNKNOWN","message":"سلام"}

کاربر: "تلگرام رو باز کن"
شما: {"action":"open_app","app_name":"تلگرام"}

کاربر: "ساعت 8 یادآوری بده"
شما: REMINDER:{"time":"08:00","message":"یادآوری","alarm":true,"repeat":"none"}

کاربر: "به میدان آزادی برسون"
شما: NAVIGATION:{"destination":"میدان آزادی","persian_voice":true}

❌ غلط: "متاسفانه نمی‌توانم..."
✅ درست: فوراً JSON دستور را برگردانید"""
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
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply()
    }

    fun getSystemPrompt(): String {
        return prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
    }

    fun saveTemperature(temperature: Float) {
        prefs.edit().putFloat(KEY_TEMPERATURE, temperature).apply()
    }

    fun getTemperature(): Float {
        return prefs.getFloat(KEY_TEMPERATURE, 0.7f)
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }
}
