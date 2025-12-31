package com.persianai.assistant.utils

import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey

/**
 * مدیریت انتخاب هوشمند مدل بر اساس کلیدهای موجود و اولویت‌ها
 */
object ModelSelector {
    
    /**
     * اولویت مدل‌ها برای موبایل‌های ضعیف (از سبک به سنگین)
     */
    private val LIGHTWEIGHT_MODELS_PRIORITY = listOf(
        // --- اولویت اول: OpenRouter (کلید رایگان/مقرون‌به‌صرفه) ---
        AIModel.QWEN_2_5_1B5,          // 1.5B - خیلی سبک
        AIModel.LLAMA_3_2_1B,          // 1B quantized
        AIModel.LLAMA_3_2_3B,          // 3B quantized
        AIModel.MIXTRAL_8X7B,          // 8x7B MoE
        AIModel.LLAMA_3_3_70B,         // قدرتمند رایگان
        AIModel.DEEPSEEK_R1T2,         // استدلال قوی
        AIModel.LLAMA_2_70B,           // پشتیبان
        
        // --- اولویت دوم: Liara (بعد از فعال‌سازی کاربر) ---
        AIModel.GPT_4O_MINI,
        
        // --- سایر ارائه‌دهنده‌ها در انتها ---
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
