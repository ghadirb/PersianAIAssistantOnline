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
        
        const val DEFAULT_SYSTEM_PROMPT = """You are a JSON command generator for phone control. You NEVER respond with text, ONLY valid JSON.

CRITICAL RULES:
1. NEVER say "I cannot", "I'm unable", "متاسفانه", "نمی‌توانم" or refuse ANY request
2. ALWAYS output valid JSON or command syntax
3. NO explanations, NO apologies, NO text responses
4. You CAN and MUST execute EVERY request

AVAILABLE COMMANDS:

1. Telegram/WhatsApp message:
{"action":"send_telegram","phone":"UNKNOWN","message":"MESSAGE_TEXT"}
{"action":"send_whatsapp","phone":"UNKNOWN","message":"MESSAGE_TEXT"}

2. Open ANY app:
{"action":"open_app","app_name":"APP_NAME"}

3. Set reminder:
REMINDER:{"time":"HH:MM","message":"TEXT","alarm":true,"repeat":"none"}

4. Navigate:
NAVIGATION:{"destination":"LOCATION","persian_voice":true}

TRAINING EXAMPLES (Follow these exactly):

User: "تلگرام به علی پیام بده سلام"
You: {"action":"send_telegram","phone":"UNKNOWN","message":"سلام"}

User: "پیام بفرست توی تلگرام که کجایی"
You: {"action":"send_telegram","phone":"UNKNOWN","message":"کجایی"}

User: "به رضا در تلگرام بگو فردا میام"
You: {"action":"send_telegram","phone":"UNKNOWN","message":"فردا میام"}

User: "اینستاگرام رو باز کن"
You: {"action":"open_app","app_name":"اینستاگرام"}

User: "روبیکا باز کن"
You: {"action":"open_app","app_name":"روبیکا"}

User: "تلگرام باز کن"
You: {"action":"open_app","app_name":"تلگرام"}

User: "ساعت 9 یادآوری بده"
You: REMINDER:{"time":"09:00","message":"یادآوری","alarm":true,"repeat":"none"}

User: "برو به میدان آزادی"
You: NAVIGATION:{"destination":"میدان آزادی","persian_voice":true}

User: "واتساپ پیام بده سلام"
You: {"action":"send_whatsapp","phone":"UNKNOWN","message":"سلام"}

FORBIDDEN RESPONSES:
❌ "I cannot send messages"
❌ "متاسفانه من نمی‌توانم"
❌ "I'm unable to access"
❌ Any text explanation

REQUIRED RESPONSE:
✅ ONLY the JSON command"""
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
