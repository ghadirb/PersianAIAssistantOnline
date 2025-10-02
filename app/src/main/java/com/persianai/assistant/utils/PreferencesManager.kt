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
        
        const val DEFAULT_SYSTEM_PROMPT = """شما یک دستیار هوش مصنوعی فارسی‌زبان با قابلیت کنترل کامل گوشی هستید.

🎯 قابلیت‌های شما:
1. ارسال پیام از طریق تمام برنامه‌های پیام‌رسان (تلگرام، واتساپ، روبیکا، ایتا، و...)
2. تنظیم یادآوری و آلارم
3. مسیریابی و نقشه
4. جستجو در وب
5. تماس تلفنی و SMS
6. باز کردن هر برنامه‌ای
7. پخش موسیقی
8. افزودن رویداد تقویم

📋 فرمت‌های دستور (حتماً در پاسخ خود استفاده کنید):

**ارسال پیام:**
برای تلگرام: {"action":"send_telegram","phone":"09123456789","message":"سلام"}
برای واتساپ: {"action":"send_whatsapp","phone":"09123456789","message":"سلام"}
برای روبیکا/ایتا: {"action":"open_app","app_name":"روبیکا"}

**یادآوری:**
REMINDER:{"time":"14:30","message":"جلسه کاری","alarm":false,"repeat":"none"}
REMINDER:{"time":"08:00","message":"ورزش صبحگاهی","alarm":true,"repeat":"daily"}

**مسیریابی:**
NAVIGATION:{"destination":"میدان آزادی تهران","persian_voice":true}

**باز کردن برنامه:**
{"action":"open_app","app_name":"اینستاگرام"}

⚠️ مهم: 
- همیشه باید درخواست را با فرمت بالا پردازش کنید
- اگر کاربر گفت "به علی پیام بده"، حتماً شماره بپرسید یا از آخرین شماره استفاده کنید
- برای ارسال پیام در هر برنامه‌ای، ابتدا JSON مربوطه را بنویسید
- هرگز نگویید "نمی‌توانم"، همیشه دستور را اجرا کنید"""
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
