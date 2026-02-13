package com.persianai.assistant.utils

import android.content.Context
import com.persianai.assistant.config.RemoteAIConfigManager
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey

/**
 * مدیریت انتخاب هوشمند مدل بر اساس کلیدهای موجود و اولویت‌ها
 */
object ModelSelector {
    
    /**
     * انتخاب بهترین مدل بر اساس کلیدهای موجود و اولویت‌ها (از remote config یا fallback)
     */
    fun selectBestModel(
        context: Context?,
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
        
        // دریافت لیست اولویت از RemoteAIConfigManager (با fallback به لیست پیش‌فرض)
        val priorityList = try {
            context?.let { RemoteAIConfigManager.getInstance(it).getModelPriority() }
                ?: listOf(AIModel.LIARA_GPT_5_NANO, AIModel.GAPGPT_DEEPSEEK_V3, AIModel.LIARA_GPT_4O_MINI)
        } catch (e: Exception) {
            listOf(AIModel.LIARA_GPT_5_NANO, AIModel.GAPGPT_DEEPSEEK_V3, AIModel.LIARA_GPT_4O_MINI)
        }
        
        // پیدا کردن اولین مدل که provider آن فعال است
        return priorityList.firstOrNull { model ->
            activeProviders.contains(model.provider)
        } ?: AIModel.getDefaultModel()
    }
    
    /**
     * دریافت لیست مدل‌های قابل استفاده بر اساس کلیدهای فعال (از remote config)
     */
    fun getAvailableModels(context: Context?, apiKeys: List<APIKey>): List<AIModel> {
        val activeProviders = apiKeys
            .filter { it.isActive }
            .map { it.provider }
            .toSet()
        
        val priorityList = try {
            context?.let { RemoteAIConfigManager.getInstance(it).getModelPriority() }
                ?: listOf(AIModel.LIARA_GPT_5_NANO, AIModel.GAPGPT_DEEPSEEK_V3, AIModel.LIARA_GPT_4O_MINI)
        } catch (e: Exception) {
            listOf(AIModel.LIARA_GPT_5_NANO, AIModel.GAPGPT_DEEPSEEK_V3, AIModel.LIARA_GPT_4O_MINI)
        }
        
        return priorityList.filter { model ->
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
        context: Context?,
        currentModel: AIModel,
        apiKeys: List<APIKey>
    ): AIModel? {
        
        val availableModels = getAvailableModels(context, apiKeys)
        
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
