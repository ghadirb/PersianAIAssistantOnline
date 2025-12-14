package com.persianai.assistant.models

import com.google.gson.annotations.SerializedName

/**
 * مدل‌های هوش مصنوعی پشتیبانی شده
 */
enum class AIModel(
    val modelId: String,
    val displayName: String,
    val provider: AIProvider,
    val description: String,
    val maxTokens: Int
) {
    // اولویت: OpenRouter رایگان/مقرون‌به‌صرفه (سبک در ابتدا)
    QWEN_2_5_1B5(
        "qwen/qwen2.5-1.5b-instruct",
        "Qwen2.5 1.5B (OpenRouter)",
        AIProvider.OPENROUTER,
        "خیلی سبک، مناسب گوشی‌های ضعیف‌تر و سریع",
        4000
    ),
    LLAMA_3_2_1B(
        "meta-llama/Llama-3.2-1B-Instruct",
        "LLaMA 3.2 1B (OpenRouter)",
        AIProvider.OPENROUTER,
        "سبک و کم‌هزینه، پشتیبان سریع",
        4000
    ),
    LLAMA_3_2_3B(
        "meta-llama/Llama-3.2-3B-Instruct",
        "LLaMA 3.2 3B (OpenRouter)",
        AIProvider.OPENROUTER,
        "سبک با دقت بهتر، چندزبانه",
        4000
    ),
    LLAMA_3_3_70B(
        "meta-llama/llama-3.3-70b-instruct",
        "Llama 3.3 70B (OpenRouter)",
        AIProvider.OPENROUTER,
        "مدل قدرتمند و رایگان/مقرون‌به‌صرفه برای مکالمه عمومی",
        8000
    ),
    DEEPSEEK_R1T2(
        "deepseek/deepseek-r1-t2-chimera",
        "DeepSeek R1T2 (OpenRouter)",
        AIProvider.OPENROUTER,
        "تمرکز بر استدلال و هزینه کم برای پاسخ‌های تحلیلی",
        8000
    ),
    MIXTRAL_8X7B(
        "mistralai/mixtral-8x7b-instruct",
        "Mixtral 8x7B (OpenRouter)",
        AIProvider.OPENROUTER,
        "سریع و سبک برای کدنویسی و پاسخ‌های کوتاه",
        8000
    ),
    LLAMA_2_70B(
        "meta-llama/llama-2-70b-chat",
        "Llama 2 70B (OpenRouter)",
        AIProvider.OPENROUTER,
        "مدل پشتیبان متن‌باز و چندزبانه",
        4000
    ),

    // OpenAI
    GPT_4O(
        "gpt-4o",
        "GPT-4o",
        AIProvider.OPENAI,
        "قدرتمندترین مدل OpenAI با قابلیت درک متن و تصویر",
        128000
    ),
    GPT_4O_MINI(
        "gpt-4o-mini",
        "GPT-4o Mini",
        AIProvider.OPENAI,
        "نسخه سریع و کارآمد GPT-4o",
        128000
    ),

    // Anthropic
    CLAUDE_SONNET(
        "claude-3-5-sonnet-20241022",
        "Claude 3.5 Sonnet",
        AIProvider.ANTHROPIC,
        "مدل پیشرفته Anthropic با توانایی استدلال بالا",
        200000
    ),
    CLAUDE_HAIKU(
        "claude-3-5-haiku-20241022",
        "Claude 3.5 Haiku",
        AIProvider.ANTHROPIC,
        "نسخه سریع و مقرون به صرفه Claude",
        200000
    );

    companion object {
        fun fromModelId(modelId: String): AIModel? {
            return values().find { it.modelId == modelId }
        }

        fun getDefaultModel(): AIModel = QWEN_2_5_1B5
    }
}

/**
 * ارائه‌دهندگان سرویس هوش مصنوعی
 */
enum class AIProvider {
    OPENAI,
    ANTHROPIC,
    OPENROUTER
}

/**
 * کلید API
 */
data class APIKey(
    val provider: AIProvider,
    val key: String,
    val isActive: Boolean = true
)

/**
 * پیام چت
 */
data class ChatMessage(
    val id: Long = 0,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val audioPath: String? = null,
    val isError: Boolean = false
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * درخواست چت
 */
data class ChatRequest(
    val model: String,
    val messages: List<Map<String, String>>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val stream: Boolean = false
)

/**
 * پاسخ چت
 */
data class ChatResponse(
    val id: String,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

data class Choice(
    val index: Int,
    val message: Message,
    val finishReason: String? = null
)

data class Message(
    val role: String,
    val content: String
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * پاسخ Claude
 */
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContent>,
    val model: String,
    val usage: ClaudeUsage
)

data class ClaudeContent(
    val type: String,
    val text: String
)

data class ClaudeUsage(
    val inputTokens: Int,
    val outputTokens: Int
)
