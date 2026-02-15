package com.persianai.assistant.utils

import android.content.Context
import android.util.Log
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.ModelManager
import com.persianai.assistant.models.ModelWrapper
import com.persianai.assistant.config.RemoteAIConfigManager

/**
 * انتخاب‌گر هوشمند مدل‌های هوش مصنوعی با پشتیبانی از مدل‌های داینامیک
 */
object ModelSelector {
    
    private const val TAG = "ModelSelector"
    
    /**
     * انتخاب بهترین مدل موجود بر اساس کلیدهای فعال و اولویت از remote config
     */
    fun selectBestModel(context: Context?, apiKeys: List<APIKey>): AIModel {
        // دریافت لیست اولویت از ModelManager (شامل مدل‌های داینامیک)
        val priorityModels = ModelManager.getModelPriority(context)
        
        // دریافت provider‌های فعال
        val activeProviders = apiKeys
            .filter { it.isActive }
            .map { it.provider }
            .toSet()
        
        if (activeProviders.isEmpty()) {
            Log.w(TAG, "No active API keys found, using default offline model")
            return AIModel.getDefaultModel()
        }
        
        Log.d(TAG, "Active providers: ${activeProviders.joinToString(", ")}")
        
        // پیدا کردن اولین مدل با provider فعال
        for (modelWrapper in priorityModels) {
            when (modelWrapper) {
                is ModelWrapper.StaticModel -> {
                    val model = modelWrapper.unwrap()
                    if (activeProviders.contains(model.provider)) {
                        Log.d(TAG, "Selected static model: ${model.modelId} (${model.provider})")
                        return model
                    }
                }
                is ModelWrapper.DynamicModel -> {
                    val dynamicModel = modelWrapper.unwrap()
                    if (activeProviders.contains(dynamicModel.provider)) {
                        // تبدیل مدل داینامیک به مدل استاتیک مشابه یا fallback
                        val staticModel = findCompatibleStaticModel(dynamicModel, apiKeys)
                        if (staticModel != null) {
                            Log.d(TAG, "Selected dynamic-compatible model: ${staticModel.modelId} (${staticModel.provider})")
                            return staticModel
                        }
                    }
                }
            }
        }
        
        Log.w(TAG, "No compatible model found, using default")
        return AIModel.getDefaultModel()
    }
    
    /**
     * پیدا کردن مدل استاتیک سازگار با مدل داینامیک
     */
    private fun findCompatibleStaticModel(dynamicModel: com.persianai.assistant.models.DynamicAIModel, apiKeys: List<APIKey>): AIModel? {
        // اول تلاش کن مدل استاتیک با همین provider پیدا کن
        val compatibleStatic = AIModel.values().find { it.provider == dynamicModel.provider }
        if (compatibleStatic != null) {
            return compatibleStatic
        }
        
        // اگر پیدا نشد، اولین مدل فعال را برگردان
        val activeProvider = apiKeys.find { it.provider == dynamicModel.provider }?.provider
        if (activeProvider != null) {
            return AIModel.values().find { it.provider == activeProvider }
        }
        
        return null
    }
    
    /**
     * بررسی اینکه آیا مدل مشخص قابل استفاده است
     */
    fun isModelAvailable(model: AIModel, apiKeys: List<APIKey>): Boolean {
        return apiKeys.any { it.isActive && it.provider == model.provider }
    }
    
    /**
     * دریافت لیست مدل‌های قابل استفاده (شامل مدل‌های داینامیک)
     */
    fun getAvailableModels(context: Context?, apiKeys: List<APIKey>): List<ModelWrapper> {
        val allModels = ModelManager.getAllModels(context)
        val activeProviders = apiKeys.filter { it.isActive }.map { it.provider }.toSet()
        
        return allModels.filter { modelWrapper ->
            when (modelWrapper) {
                is ModelWrapper.StaticModel -> {
                    activeProviders.contains(modelWrapper.unwrap().provider)
                }
                is ModelWrapper.DynamicModel -> {
                    activeProviders.contains(modelWrapper.unwrap().provider)
                }
            }
        }
    }
    
    /**
     * دریافت لیست مدل‌های قابل استفاده به صورت AIModel (برای سازگاری با کدهای قدیمی)
     */
    fun getAvailableAIModels(context: Context?, apiKeys: List<APIKey>): List<AIModel> {
        val availableWrappers = getAvailableModels(context, apiKeys)
        
        return availableWrappers.mapNotNull { wrapper ->
            when (wrapper) {
                is ModelWrapper.StaticModel -> wrapper.unwrap()
                is ModelWrapper.DynamicModel -> {
                    // تبدیل مدل داینامیک به مدل استاتیک سازگار
                    findCompatibleStaticModel(wrapper.unwrap(), apiKeys)
                }
            }
        }.distinct()
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
        
        val availableModels = getAvailableAIModels(context, apiKeys)
        
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
