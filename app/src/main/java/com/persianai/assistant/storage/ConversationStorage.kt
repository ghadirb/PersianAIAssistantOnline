package com.persianai.assistant.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.models.Conversation
import com.persianai.assistant.models.ChatMessage
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
    suspend fun getAllConversations(): List<Conversation> = withContext(Dispatchers.IO) { loadAll() }

    /**
     * نسخه همگام: همه چت‌ها
     */
    fun getAllConversationsSync(): List<Conversation> = loadAll()

    /**
     * بارگذاری و مرتب‌سازی نزولی بر اساس زمان به‌روزرسانی
     */
    private fun loadAll(): List<Conversation> {
        return try {
            val json = prefs.getString("conversations_list", "[]") ?: "[]"
            val type = object : TypeToken<List<Conversation>>() {}.type
            (gson.fromJson<List<Conversation>>(json, type) ?: emptyList())
                .sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }
    
    /**
     * ذخیره یک چت
     */
    suspend fun saveConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        saveConversationSync(conversation)
    }

    fun saveConversationSync(conversation: Conversation) {
        try {
            val conversations = loadAll().toMutableList()
            conversations.removeIf { it.id == conversation.id }
            conversation.updatedAt = System.currentTimeMillis()
            conversations.add(0, conversation)
            val json = gson.toJson(conversations)
            prefs.edit().putString("conversations_list", json).apply()
        } catch (_: Exception) {
        }
    }
    
    /**
     * دریافت یک چت بر اساس ID
     */
    suspend fun getConversation(id: String): Conversation? = withContext(Dispatchers.IO) {
        loadAll().firstOrNull { it.id == id }
    }

    fun getConversationSync(id: String): Conversation? = loadAll().firstOrNull { it.id == id }
    
    /**
     * حذف یک چت
     */
    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) { deleteConversationSync(id) }

    fun deleteConversationSync(id: String) {
        try {
            val conversations = loadAll().toMutableList()
            conversations.removeIf { it.id == id }
            val json = gson.toJson(conversations)
            prefs.edit().putString("conversations_list", json).apply()
        } catch (_: Exception) {
        }
    }
    
    /**
     * تغییر عنوان چت
     */
    suspend fun updateConversationTitle(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        updateConversationTitleSync(id, newTitle)
    }

    fun updateConversationTitleSync(id: String, newTitle: String) {
        try {
            val conversations = loadAll().toMutableList()
            val conversation = conversations.firstOrNull { it.id == id }
            if (conversation != null) {
                conversation.title = newTitle
                conversation.updatedAt = System.currentTimeMillis()
                val json = gson.toJson(conversations)
                prefs.edit().putString("conversations_list", json).apply()
            }
        } catch (_: Exception) {
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

    /**
     * دریافت پیام‌های چت فعلی یا آخرین چت (نسخه همگام برای سازگاری قدیمی)
     */
    fun getMessages(): List<ChatMessage> {
        return try {
            val conversations = loadAll()
            val currentId = getCurrentConversationId()
            val conv = conversations.firstOrNull { it.id == currentId } ?: conversations.firstOrNull()
            conv?.messages ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * دریافت پیام‌های آخرین مکالمه
     */
    fun getLastConversationMessages(): List<ChatMessage> {
        return try {
            val conversations = loadAll()
            conversations.firstOrNull()?.messages ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * دریافت لیست چت‌ها بر اساس namespace
     */
    fun getConversationsByNamespace(namespace: String): List<Conversation> {
        return loadAll().filter { it.namespace == namespace }
    }

    /**
     * آخرین چت در یک namespace
     */
    fun getLatestConversation(namespace: String): Conversation? {
        return getConversationsByNamespace(namespace).firstOrNull()
    }

    /**
     * ایجاد یک چت جدید در namespace
     */
    fun createConversation(namespace: String, title: String = "چت جدید"): Conversation {
        val conv = Conversation(title = title, namespace = namespace)
        saveConversationSync(conv)
        setCurrentConversationId(conv.id)
        return conv
    }
}
