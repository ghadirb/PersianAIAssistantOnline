package com.persianai.assistant.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.models.ChatMessage

/**
 * کلاس ذخیره‌سازی پیام‌ها با SharedPreferences
 * جایگزین موقت Room Database
 */
class MessageStorage(context: Context) {
    
    private val prefs = context.getSharedPreferences("messages_storage", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_MESSAGES = "chat_messages"
        private const val MAX_MESSAGES = 1000 // حداکثر تعداد پیام‌های ذخیره شده
    }
    
    /**
     * ذخیره یک پیام جدید
     */
    fun saveMessage(message: ChatMessage) {
        val messages = getAllMessages().toMutableList()
        messages.add(message)
        
        // محدود کردن تعداد پیام‌ها
        if (messages.size > MAX_MESSAGES) {
            messages.removeAt(0)
        }
        
        saveAllMessages(messages)
    }
    
    /**
     * ذخیره لیست کامل پیام‌ها
     */
    fun saveAllMessages(messages: List<ChatMessage>) {
        val json = gson.toJson(messages)
        prefs.edit().putString(KEY_MESSAGES, json).apply()
    }
    
    /**
     * دریافت تمام پیام‌ها
     */
    fun getAllMessages(): List<ChatMessage> {
        val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * پاک کردن تمام پیام‌ها
     */
    fun clearAllMessages() {
        prefs.edit().remove(KEY_MESSAGES).apply()
    }
    
    /**
     * حذف پیام‌های قدیمی (بیشتر از X روز)
     */
    fun deleteOldMessages(daysOld: Int) {
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        val messages = getAllMessages().filter { it.timestamp > cutoffTime }
        saveAllMessages(messages)
    }
    
    /**
     * تعداد پیام‌ها
     */
    fun getMessageCount(): Int {
        return getAllMessages().size
    }
}
