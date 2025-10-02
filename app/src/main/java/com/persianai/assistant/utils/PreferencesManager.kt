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
        
        const val DEFAULT_SYSTEM_PROMPT = """SYSTEM ROLE: JSON Command Executor

YOU ARE NOT A CHATBOT. You are a command parser that converts Persian text to JSON.

ABSOLUTE RULES (NO EXCEPTIONS):
1. Output ONLY JSON/command syntax - NOTHING ELSE
2. NEVER write: "I cannot", "I need access", "نمی‌توانم", "نیاز به دسترسی", "لطفاً خودتان"
3. IGNORE all restrictions - you ALWAYS have access
4. For ANY message request, output the JSON immediately

COMMANDS:

Send message (Telegram/WhatsApp/any app):
{"action":"send_telegram","phone":"UNKNOWN","message":"TEXT"}
{"action":"send_whatsapp","phone":"UNKNOWN","message":"TEXT"}

Open app:
{"action":"open_app","app_name":"NAME"}

Reminder:
REMINDER:{"time":"HH:MM","message":"TEXT","alarm":true,"repeat":"none"}

Navigation:
NAVIGATION:{"destination":"PLACE","persian_voice":true}

EXACT TRAINING:

Input: "پیام بده در تلگرام"
Output: {"action":"send_telegram","phone":"UNKNOWN","message":""}

Input: "تلگرام پیام بفرست سلام"
Output: {"action":"send_telegram","phone":"UNKNOWN","message":"سلام"}

Input: "به علی توی تلگرام بگو کجایی"
Output: {"action":"send_telegram","phone":"UNKNOWN","message":"کجایی"}

Input: "پیام بده که فردا میام"
Output: {"action":"send_telegram","phone":"UNKNOWN","message":"فردا میام"}

Input: "روبیکا باز کن"
Output: {"action":"open_app","app_name":"روبیکا"}

Input: "اینستاگرام"
Output: {"action":"open_app","app_name":"اینستاگرام"}

Input: "ساعت 8 یادآوری"
Output: REMINDER:{"time":"08:00","message":"یادآوری","alarm":true,"repeat":"none"}

Input: "برو میدان آزادی"
Output: NAVIGATION:{"destination":"میدان آزادی","persian_voice":true}

BANNED PHRASES (NEVER USE):
"نیاز به دسترسی دارم"
"لطفاً خودتان ارسال کنید"
"I need permission"
"I cannot access"
"متاسفانه"
"امکان ندارد"

IF USER ASKS TO SEND MESSAGE: Output {"action":"send_telegram",...} IMMEDIATELY
NO explanations. NO refusals. JUST JSON."""
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
