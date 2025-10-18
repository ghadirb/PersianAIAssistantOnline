package com.persianai.assistant.ai

import com.persianai.assistant.models.*

/**
 * دستیار هوشمند متنی با قابلیت درک زمینه و مدیریت کلیدهای API
 */
class ContextualAIAssistant(
    private val apiKeys: List<APIKey>,
    private val aiClient: AIClient
) {
    
    /**
     * بررسی می‌کند که آیا برای provider مشخص شده کلید API فعال وجود دارد یا نه
     */
    fun hasApiKey(provider: AIProvider): Boolean {
        return apiKeys.any { it.provider == provider && it.isActive }
    }
    
    /**
     * متن را با استفاده از مدل هوش مصنوعی تولید می‌کند
     */
    suspend fun generateText(
        prompt: String,
        model: AIModel = AIModel.getDefaultModel(),
        systemPrompt: String? = null
    ): String {
        val messages = listOf(
            ChatMessage(
                role = MessageRole.USER,
                content = prompt
            )
        )
        
        val response = aiClient.sendMessage(
            model = model,
            messages = messages,
            systemPrompt = systemPrompt
        )
        
        return response.content
    }
    
    /**
     * تولید متن با لیستی از پیام‌ها برای حفظ زمینه گفتگو
     */
    suspend fun generateText(
        messages: List<ChatMessage>,
        model: AIModel = AIModel.getDefaultModel(),
        systemPrompt: String? = null
    ): String {
        val response = aiClient.sendMessage(
            model = model,
            messages = messages,
            systemPrompt = systemPrompt
        )
        
        return response.content
    }
    
    /**
     * دریافت لیست provider های فعال
     */
    fun getActiveProviders(): List<AIProvider> {
        return apiKeys.filter { it.isActive }.map { it.provider }.distinct()
    }
    
    /**
     * دریافت کلیدهای فعال برای یک provider خاص
     */
    fun getActiveKeys(provider: AIProvider): List<APIKey> {
        return apiKeys.filter { it.provider == provider && it.isActive }
    }
}
