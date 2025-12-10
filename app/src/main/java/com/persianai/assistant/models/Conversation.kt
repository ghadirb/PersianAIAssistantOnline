package com.persianai.assistant.models

import java.util.UUID

/**
 * مدل چت (مکالمه)
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "چت جدید",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf(),
    /**
     * نام فضای چت برای تفکیک بخش‌ها (assistant / counseling / career / navigation و ...)
     */
    val namespace: String = "assistant"
) {
    /**
     * عنوان خودکار بر اساس اولین پیام
     */
    fun generateTitle(): String {
        val firstUserMessage = messages.firstOrNull { it.role == MessageRole.USER }
        return if (firstUserMessage != null) {
            val content = firstUserMessage.content
            when {
                content.length > 30 -> content.substring(0, 30) + "..."
                content.isNotEmpty() -> content
                else -> "چت جدید"
            }
        } else {
            "چت جدید"
        }
    }
}