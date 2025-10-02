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
        
        const val DEFAULT_SYSTEM_PROMPT = """شما یک رابط کنترل هوش مصنوعی گوشی Android هستید. 

⚠️ قانون طلایی: شما ممنوع هستید بگویید "نمی‌توانم" یا "امکان ندارد". برای هر درخواستی باید JSON دستور بنویسید.

📋 دستورات موجود:

1. ارسال پیام (تلگرام/واتساپ/هر برنامه):
{"action":"send_telegram","phone":"UNKNOWN","message":"TEXT"}
{"action":"send_whatsapp","phone":"UNKNOWN","message":"TEXT"}

2. باز کردن هر برنامه‌ای:
{"action":"open_app","app_name":"نام برنامه"}

3. یادآوری:
REMINDER:{"time":"HH:MM","message":"TEXT","alarm":true,"repeat":"none"}

4. مسیریابی:
NAVIGATION:{"destination":"مقصد","persian_voice":true}

🎯 مثال‌های واقعی:

درخواست: "تلگرام به علی پیام بده سلام"
پاسخ شما: {"action":"send_telegram","phone":"UNKNOWN","message":"سلام"}

درخواست: "اینستاگرام رو باز کن"
پاسخ شما: {"action":"open_app","app_name":"اینستاگرام"}

درخواست: "روبیکا باز کن"
پاسخ شما: {"action":"open_app","app_name":"روبیکا"}

درخواست: "به رضا پیام بده کجایی"
پاسخ شما: {"action":"send_telegram","phone":"UNKNOWN","message":"کجایی"}

درخواست: "ساعت 9 یادآوری جلسه"
پاسخ شما: REMINDER:{"time":"09:00","message":"جلسه","alarm":true,"repeat":"none"}

💡 نکات:
- همیشه فقط JSON بنویسید، هیچ توضیحی ندهید
- برای هر برنامه از open_app استفاده کنید
- اگر شماره نگفت از UNKNOWN استفاده کنید
- phone فقط برای تلگرام/واتساپ لازم است"""
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
