package com.persianai.assistant.utils

import android.content.Context
import android.util.Log
import com.persianai.assistant.models.AIProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * تست و نمایش وضعیت سیستم Auto-Provisioning
 */
object ProvisioningTest {
    
    private const val TAG = "ProvisioningTest"
    
    /**
     * اجرای تست کامل و نمایش نتایج در لاگ
     */
    fun runTest(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "🧪 شروع تست Auto-Provisioning")
            Log.d(TAG, "═══════════════════════════════════════")
            
            val prefsManager = PreferencesManager(context)
            
            // مرحله 1: چک کردن کلیدهای موجود
            val existingKeys = prefsManager.getAPIKeys()
            Log.d(TAG, "\n📋 کلیدهای موجود: ${existingKeys.size}")
            
            if (existingKeys.isNotEmpty()) {
                existingKeys.groupBy { it.provider }.forEach { (provider, keys) ->
                    Log.d(TAG, "  - ${provider.name}: ${keys.size} کلید (${keys.count { it.isActive }} فعال)")
                }
            } else {
                Log.d(TAG, "  ⚠️ هیچ کلیدی یافت نشد")
            }
            
            // مرحله 2: بارگذاری خودکار
            Log.d(TAG, "\n🔄 در حال بارگذاری خودکار کلیدها...")
            
            val result = AutoProvisioningManager.autoProvision(context)
            
            if (result.isSuccess) {
                val keys = result.getOrNull() ?: emptyList()
                Log.d(TAG, "✅ موفق: ${keys.size} کلید بارگذاری شد")
                
                keys.groupBy { it.provider }.forEach { (provider, providerKeys) ->
                    Log.d(TAG, "  - ${provider.name}: ${providerKeys.size} کلید")
                    providerKeys.forEachIndexed { index, key ->
                        val status = if (key.isActive) "✅ فعال" else "❌ غیرفعال"
                        val keyPreview = key.key.take(15) + "..."
                        Log.d(TAG, "    ${index + 1}. $keyPreview - $status")
                    }
                }
                
                // مرحله 3: انتخاب مدل
                Log.d(TAG, "\n🤖 انتخاب بهترین مدل...")
                
                val selectedModel = ModelSelector.selectBestModel(keys, preferLightweight = true)
                Log.d(TAG, "  مدل انتخابی: ${selectedModel.displayName}")
                Log.d(TAG, "  Provider: ${selectedModel.provider.name}")
                Log.d(TAG, "  توضیحات: ${selectedModel.description}")
                
                // مرحله 4: لیست مدل‌های قابل استفاده
                val availableModels = ModelSelector.getAvailableModels(keys)
                Log.d(TAG, "\n📦 مدل‌های قابل استفاده (${availableModels.size}):")
                
                availableModels.forEachIndexed { index, model ->
                    Log.d(TAG, "  ${index + 1}. ${model.displayName} (${model.provider.name})")
                }
                
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "❌ خطا: ${error?.message}")
            }
            
            Log.d(TAG, "\n═══════════════════════════════════════")
            Log.d(TAG, "✅ تست کامل شد")
            Log.d(TAG, "═══════════════════════════════════════")
        }
    }
}
