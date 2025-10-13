package com.persianai.assistant.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.models.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مدیریت ذخیره‌سازی چت‌ها (مکالمات)
 */
class ConversationStorage(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * دریافت همه چت‌ها
     */
    suspend fun getAllConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        try {
            val json = prefs.getString("conversations_list", "[]") ?: "[]"
            val type = object : TypeToken<List<Conversation>>() {}.type
            gson.fromJson<List<Conversation>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * ذخیره یک چت
     */
    suspend fun saveConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        try {
            val conversations = getAllConversations().toMutableList()
            
            // حذف چت قدیمی با همین ID (اگر وجود داشت)
            conversations.removeIf { it.id == conversation.id }
            
            // اضافه کردن چت جدید
            conversation.updatedAt = System.currentTimeMillis()
            conversations.add(0, conversation)
            
            // ذخیره
            val json = gson.toJson(conversations)
            prefs.edit().putString("conversations_list", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * دریافت یک چت بر اساس ID
     */
    suspend fun getConversation(id: String): Conversation? = withContext(Dispatchers.IO) {
        getAllConversations().firstOrNull { it.id == id }
    }
    
    /**
     * حذف یک چت
     */
    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        try {
            val conversations = getAllConversations().toMutableList()
            conversations.removeIf { it.id == id }
            
            val json = gson.toJson(conversations)
            prefs.edit().putString("conversations_list", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * تغییر عنوان چت
     */
    suspend fun updateConversationTitle(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        try {
            val conversations = getAllConversations().toMutableList()
            val conversation = conversations.firstOrNull { it.id == id }
            
            if (conversation != null) {
                conversation.title = newTitle
                conversation.updatedAt = System.currentTimeMillis()
                
                val json = gson.toJson(conversations)
                prefs.edit().putString("conversations_list", json).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * دریافت ID آخرین چت
     */
    fun getCurrentConversationId(): String? {
        return prefs.getString("current_conversation_id", null)
    }
    
    /**
     * تنظیم ID چت فعلی
     */
    fun setCurrentConversationId(id: String) {
        prefs.edit().putString("current_conversation_id", id).apply()
    }
    
    /**
     * حذف ID چت فعلی (برای شروع چت جدید)
     */
    fun clearCurrentConversationId() {
        prefs.edit().remove("current_conversation_id").apply()
    }
}
