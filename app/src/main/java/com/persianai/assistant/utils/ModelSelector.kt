package com.persianai.assistant.utils

import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey

/**
 * مدیریت انتخاب هوشمند مدل بر اساس کلیدهای موجود و اولویت‌ها
 */
object ModelSelector {
    
    /**
     * اولویت مدل‌ها برای موبایل‌های ضعیف (از ارزان/سبک به گران/سنگین)
     * Ivira (token-based) در لایه‌ی بالاتر (QueryRouter) قبل از این لیست امتحان می‌شود.
     */
    private val LIGHTWEIGHT_MODELS_PRIORITY = listOf(
        // --- اولویت اصلی: مدل‌های ارزان/کارآمد برای چت روزمره ---
        AIModel.IVIRA_GPT5_NANO,        // ارزان‌ترین Ivira برای چت
        AIModel.GPT_4O_MINI,            // OpenAI GPT‑4o Mini
        AIModel.LIARA_GPT_4O_MINI,      // Liara GPT‑4o Mini
        AIModel.IVIRA_GPT5_MINI,        // Ivira GPT‑5 Mini
        AIModel.GAPGPT_DEEPSEEK_V3,     // DeepSeek V3 از gapgpt.app

        // --- مدل‌های تحلیلی/قوی‌تر (OpenRouter و سایرین) ---
        AIModel.QWEN_2_5_1B5,
        AIModel.LLAMA_3_2_1B,
        AIModel.LLAMA_3_2_3B,
        AIModel.MIXTRAL_8X7B,
        AIModel.LLAMA_3_3_70B,
        AIModel.DEEPSEEK_R1T2,
        AIModel.LLAMA_2_70B,

        // --- سایر مدل‌های عمومی ---
        AIModel.GPT_4O,
        AIModel.CLAUDE_HAIKU,
        AIModel.CLAUDE_SONNET,
        AIModel.AIML_GPT_35
    )
    
    /**
     * انتخاب بهترین مدل بر اساس کلیدهای موجود و اولویت‌ها
     */
    fun selectBestModel(
        apiKeys: List<APIKey>,
        preferLightweight: Boolean = true
    ): AIModel {
        
        // دریافت providerهای فعال
        val activeProviders = apiKeys
            .filter { it.isActive }
            .map { it.provider }
            .toSet()
        
        if (activeProviders.isEmpty()) {
            return AIModel.getDefaultModel()
        }
        
        // انتخاب از لیست اولویت
        val priorityList = if (preferLightweight) {
            LIGHTWEIGHT_MODELS_PRIORITY
        } else {
            LIGHTWEIGHT_MODELS_PRIORITY.reversed()
        }
        
        // پیدا کردن اولین مدل که provider آن فعال است
        return priorityList.firstOrNull { model ->
            activeProviders.contains(model.provider)
        } ?: AIModel.getDefaultModel()
    }
    
    /**
     * دریافت لیست مدل‌های قابل استفاده بر اساس کلیدهای فعال
     */
    fun getAvailableModels(apiKeys: List<APIKey>): List<AIModel> {
        val activeProviders = apiKeys
            .filter { it.isActive }
            .map { it.provider }
            .toSet()
        
        return LIGHTWEIGHT_MODELS_PRIORITY.filter { model ->
            activeProviders.contains(model.provider)
        }
    }
    
    /**
     * چک کردن اینکه آیا یک مدل با کلیدهای موجود قابل استفاده است
     */
    fun isModelAvailable(model: AIModel, apiKeys: List<APIKey>): Boolean {
        return apiKeys.any { it.provider == model.provider && it.isActive }
    }
    
    /**
     * انتخاب fallback model در صورت خطا در مدل فعلی
     */
    fun selectFallbackModel(
        currentModel: AIModel,
        apiKeys: List<APIKey>
    ): AIModel? {
        
        val availableModels = getAvailableModels(apiKeys)
        
        // حذف مدل فعلی از لیست
        val fallbackOptions = availableModels.filter { it != currentModel }
        
        // انتخاب اولین گزینه موجود
        return fallbackOptions.firstOrNull()
    }
    
    /**
     * دریافت اطلاعات مدل برای نمایش به کاربر
     */
    fun getModelInfo(model: AIModel): String {
        return buildString {
            append("🤖 ${model.displayName}\n")
            append("📦 ${model.provider.name}\n")
            append("📝 ${model.description}")
        }
    }
}
