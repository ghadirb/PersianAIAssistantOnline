package com.persianai.assistant.core

import android.content.Context
import android.util.Log
import com.persianai.assistant.storage.ConversationStorage
import com.persianai.assistant.models.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * ConversationStateManager
 * 
 * Manages conversation context to prevent repetitive responses
 * ✓ Fixed: Tracks conversation history properly
 * ✓ Fixed: Detects duplicate intents
 * ✓ Fixed: Maintains context between messages
 */
class ConversationStateManager(private val context: Context) {
    
    private val TAG = "ConversationState"
    private val storage = ConversationStorage(context)
    
    private var currentConversation: Conversation? = null
    private var lastProcessedIntent: String? = null
    private var lastProcessedText: String? = null
    
    suspend fun initializeOrLoad(): Conversation? = withContext(Dispatchers.IO) {
        return@withContext try {
            val existing = storage.getActiveConversation()
            if (existing != null) {
                Log.d(TAG, "Loaded existing conversation: ${existing.id}")
                currentConversation = existing
                existing
            } else {
                val new = Conversation(
                    id = UUID.randomUUID().toString(),
                    title = "نام‌نشخص",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    messages = mutableListOf()
                )
                Log.d(TAG, "Created new conversation: ${new.id}")
                currentConversation = new
                try {
                    storage.setCurrentConversationId(new.id)
                    storage.saveConversation(new)
                } catch (_: Exception) {
                }
                new
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing conversation", e)
            null
        }
    }
    
    /**
     * Check if this is a duplicate request
     */
    fun isDuplicateRequest(
        intentName: String,
        userText: String
    ): Boolean {
        val isDuplicate = (intentName == lastProcessedIntent && 
                          userText.trim() == lastProcessedText?.trim())
        
        if (isDuplicate) {
            Log.w(TAG, "⚠️ Duplicate detected: $intentName | $userText")
        }
        
        return isDuplicate
    }
    
    /**
     * Update last processed request
     */
    fun updateLastRequest(intentName: String, userText: String) {
        lastProcessedIntent = intentName
        lastProcessedText = userText
        Log.d(TAG, "✅ Tracking: $intentName")
    }
    
    /**
     * Get current conversation ID
     */
    fun getCurrentConversationId(): String? = currentConversation?.id
    
    /**
     * Save message to conversation
     */
    suspend fun saveMessage(
        role: String,
        content: String
    ) = withContext(Dispatchers.IO) {
        try {
            val conv = currentConversation ?: return@withContext false
            
            // Don't save empty messages
            if (content.isBlank()) {
                Log.w(TAG, "Skipping empty message")
                return@withContext false
            }
            
            val message = com.persianai.assistant.models.ChatMessage(
                role = if (role.equals("user", ignoreCase = true)) 
                    com.persianai.assistant.models.MessageRole.USER 
                else 
                    com.persianai.assistant.models.MessageRole.ASSISTANT,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            
            conv.messages.add(message)
            storage.saveConversation(conv)
            
            Log.d(TAG, "✅ Saved message: ${role.take(10)} | ${content.take(30)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving message", e)
            false
        }
    }
    
    /**
     * Get conversation history for context
     */
    fun getConversationHistory(maxMessages: Int = 5): List<com.persianai.assistant.models.ChatMessage> {
        return currentConversation?.messages
            ?.takeLast(maxMessages)
            ?.toList() ?: emptyList()
    }
    
    /**
     * Clear conversation history
     */
    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        try {
            currentConversation?.let { conv ->
                conv.messages.clear()
                storage.saveConversation(conv)
                Log.d(TAG, "✅ Conversation cleared")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing conversation", e)
        }
    }
    
    /**
     * Get context summary for AI
     */
    fun getContextSummary(): String {
        val history = getConversationHistory(3)
        if (history.isEmpty()) return "بدون تاریخچه"
        
        return history.map { "${it.role.name}: ${it.content.take(50)}" }
            .joinToString("\n")
    }
}