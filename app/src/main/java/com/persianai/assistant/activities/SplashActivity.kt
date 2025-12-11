package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.R
import com.persianai.assistant.BuildConfig
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.utils.DefaultApiKeys
import com.persianai.assistant.utils.DriveHelper
import com.persianai.assistant.utils.EncryptionHelper
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.utils.PreferencesManager.ProviderPreference
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * صفحه شروع برنامه - نمایش توضیحات و دریافت رمز عبور
 */
class SplashActivity : AppCompatActivity() {

    private var aimlapiFound: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        try {
            // بررسی اینکه آیا قبلاً کلیدها بارگذاری شده‌اند یا نه
            val prefsManager = PreferencesManager(this)

            // تلاش سایلنت برای دانلود/رمزگشایی/فعال‌سازی کلیدها با اولویت aimlapi > openrouter > openai
            if (prefsManager.hasAPIKeys()) {
                navigateToMain()
                return
            }

            if (tryAutoDownloadAndActivate(prefsManager)) {
                return
            }

            if (tryAutoProvisioning(prefsManager)) {
                return
            }

            // نمایش دیالوگ توضیحات و دریافت رمز
            showWelcomeDialog()
        } catch (e: Exception) {
            // در صورت هر خطایی، به MainActivity برو
            android.util.Log.e("SplashActivity", "Error in onCreate", e)
            navigateToMain()
        }
    }

    /**
     * استخراج کلید HuggingFace از فایل رمزگشایی‌شده
     * فرمت‌های پشتیبانی: 
     *  - huggingface:KEY
     *  - hf:KEY
     *  - hf_xxx (خط بدون پیشوند)
     */
    private fun extractHuggingFaceKey(data: String): String? {
        data.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            val lower = trimmed.lowercase()
            val key = when {
                lower.startsWith("huggingface:") -> trimmed.substringAfter(":").trim()
                lower.startsWith("hf:") -> trimmed.substringAfter(":").trim()
                trimmed.startsWith("hf_") -> trimmed
                else -> ""
            }.trim()
            if (key.startsWith("hf_") && key.length > 5) return key
        }
        return null
    }

    private fun showWelcomeDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🤖 خوش آمدید به دستیار هوش مصنوعی")
        builder.setMessage("""
            این برنامه یک دستیار هوش مصنوعی قدرتمند و چندمنظوره است که:
            
            ✅ حالت آنلاین: استفاده از مدل‌های پیشرفته مانند GPT-4o و Claude
            ✅ تشخیص صوت: تبدیل گفتار به متن
            ✅ حافظه بلندمدت: ذخیره تاریخچه گفتگوها
            ✅ پشتیبان‌گیری: بک‌آپ در Google Drive
            ✅ سرویس پس‌زمینه: فعال حتی در حالت بسته
            
            برای استفاده از حالت آنلاین، لطفاً رمز عبور کلیدهای API را وارد کنید:
        """.trimIndent())
        
        builder.setPositiveButton("ورود رمز") { _, _ ->
            showPasswordDialog()
        }
        
        builder.setNegativeButton("رد شدن") { _, _ ->
            // اجازه استفاده بدون کلید API (محدود)
            Toast.makeText(this, "می‌توانید بعداً از تنظیمات کلید اضافه کنید", Toast.LENGTH_LONG).show()
            navigateToMain()
        }
        
        builder.setCancelable(true)
        builder.setOnCancelListener {
            navigateToMain()
        }
        builder.show()
    }

    private fun showPasswordDialog() {
        val input = TextInputEditText(this)
        input.hint = "رمز عبور"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                          android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        val builder = AlertDialog.Builder(this)
        builder.setTitle("🔐 ورود رمز عبور")
        builder.setMessage("لطفاً رمز عبور کلیدهای API را وارد کنید:")
        builder.setView(input)
        
        builder.setPositiveButton("دانلود و رمزگشایی") { _, _ ->
            val password = input.text.toString()
            if (password.isNotBlank()) {
                downloadAndDecryptKeys(password)
            } else {
                Toast.makeText(this, "رمز عبور نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show()
                showPasswordDialog()
            }
        }
        
        builder.setNegativeButton("لغو") { _, _ ->
            navigateToMain()
        }
        
        builder.show()
    }

    private fun downloadAndDecryptKeys(password: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SplashActivity, "در حال دانلود...", Toast.LENGTH_SHORT).show()
                
                // دانلود فایل رمزشده از Google Drive
                val encryptedData = try {
                    DriveHelper.downloadEncryptedKeys()
                } catch (e: Exception) {
                    // اگر دانلود ناموفق بود، از فایل تست استفاده کن
                    Toast.makeText(
                        this@SplashActivity,
                        "خطا در دانلود از Google Drive. استفاده از حالت تست...",
                        Toast.LENGTH_SHORT
                    ).show()
                    // می‌توانید اینجا یک فایل تست قرار دهید یا از assets بخوانید
                    throw Exception("عدم دسترسی به Google Drive. لطفاً اتصال اینترنت را بررسی کنید.")
                }
                
                Toast.makeText(this@SplashActivity, "در حال رمزگشایی...", Toast.LENGTH_SHORT).show()
                
                // رمزگشایی
                val decryptedData = EncryptionHelper.decrypt(encryptedData, password)
                
                // پردازش کلیدها
                extractHuggingFaceKey(decryptedData)?.let { hf ->
                    DefaultApiKeys.setHuggingFaceKey(hf)
                    // ذخیره موقت برای سایر بخش‌ها در صورت نیاز
                    val apiPrefs = getSharedPreferences("api_keys", MODE_PRIVATE)
                    apiPrefs.edit().putString("huggingface_api_key", hf).apply()
                }
                val apiKeys = parseAPIKeys(decryptedData)
                
                if (apiKeys.isEmpty()) {
                    throw Exception("هیچ کلید معتبری یافت نشد")
                }
                
                // ذخیره کلیدها
                val prefsManager = PreferencesManager(this@SplashActivity)
                prefsManager.saveAPIKeys(apiKeys)
                if (aimlapiFound) {
                    // اولویت کاربر: مدل سبک Qwen2.5 1.5B برای مصرف کم
                    prefsManager.saveSelectedModel(AIModel.QWEN_2_5_1_5B)
                    prefsManager.setProviderPreference(ProviderPreference.SMART_ROUTE)
                }
                
                Toast.makeText(
                    this@SplashActivity,
                    "کلیدها با موفقیت بارگذاری شدند (${apiKeys.size} کلید)",
                    Toast.LENGTH_LONG
                ).show()
                
                navigateToMain()
                
            } catch (e: Exception) {
                // لاگ خطا برای debugging
                android.util.Log.e("SplashActivity", "Error downloading/decrypting keys", e)
                
                Toast.makeText(
                    this@SplashActivity,
                    "خطا: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                
                // در صورت خطا، به جای بستن برنامه، به MainActivity برود
                navigateToMain()
            }
        }
    }

    /**
     * تلاش برای Provision خودکار کلید OpenRouter در شروع برنامه
     */
    private fun tryAutoProvisioning(prefsManager: PreferencesManager): Boolean {
        if (!prefsManager.isAutoProvisioningEnabled()) return false
        val provisioningKey = prefsManager.getProvisioningKey()?.takeIf { it.isNotBlank() } ?: return false
        
        // ذخیره در SharedPreferences مورد استفاده AIModelManager
        val apiPrefs = getSharedPreferences("api_keys", MODE_PRIVATE)
        apiPrefs.edit().putString("openrouter_api_key", provisioningKey).apply()
        
        // ذخیره در PreferencesManager (لیست APIKey) بدون حذف سایر کلیدها
        val currentKeys = prefsManager.getAPIKeys().filter { it.provider != AIProvider.OPENROUTER }.toMutableList()
        currentKeys.add(APIKey(AIProvider.OPENROUTER, provisioningKey, true))
        prefsManager.saveAPIKeys(currentKeys)
        
        Toast.makeText(this, "کلید Provisioning اعمال شد", Toast.LENGTH_SHORT).show()
        navigateToMain()
        return true
    }

    private fun parseAPIKeys(data: String): List<APIKey> {
        aimlapiFound = false
        val keys = mutableListOf<APIKey>()
        
        data.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            
            // فرمت: provider:key یا فقط key
            val parts = trimmed.split(":", limit = 2)
            
            if (parts.size == 2) {
                val providerRaw = parts[0].lowercase()
                val keyValue = parts[1].trim()
                val provider = when (providerRaw) {
                    "openai" -> AIProvider.OPENAI
                    "anthropic", "claude" -> AIProvider.ANTHROPIC
                    "openrouter" -> AIProvider.OPENROUTER
                    "deepseek" -> AIProvider.OPENROUTER // deepseek روی OpenRouter استفاده می‌شود
                    "aimlapi" -> {
                        aimlapiFound = true
                        AIProvider.OPENROUTER   // برای سادگی روی OpenRouter
                    }
                    "hf", "huggingface" -> null           // در extractHuggingFaceKey پردازش می‌شود
                    else -> null
                }
                
                if (provider != null && keyValue.isNotEmpty()) {
                    keys.add(APIKey(provider, keyValue, true))
                }
            } else if (parts.size == 1 && trimmed.startsWith("sk-")) {
                // احتمالاً کلید OpenAI
                keys.add(APIKey(AIProvider.OPENAI, trimmed, true))
            }
        }
        
        // اولویت: aimlapi (رو OpenRouter) → openrouter → openai → سایرین
        return keys.sortedBy { providerPriority(it.provider) }
    }

    private fun providerPriority(provider: AIProvider): Int {
        return when (provider) {
            AIProvider.OPENROUTER -> if (aimlapiFound) 0 else 1
            AIProvider.OPENAI -> 2
            AIProvider.ANTHROPIC -> 3
        }
    }

    /**
     * دانلود سایلنت کلیدها از Google Drive و فعال‌سازی بدون تعامل کاربر
     */
    private fun tryAutoDownloadAndActivate(prefsManager: PreferencesManager): Boolean {
        // نیاز به رمز عبور برای رمزگشایی؛ از BuildConfig یا gradle.properties خوانده می‌شود
        val password = BuildConfig.API_KEYS_PASSWORD.takeIf { it.isNotEmpty() } ?: return false

        return try {
            val encrypted = runBlocking { DriveHelper.downloadEncryptedKeys() }
            val decrypted = EncryptionHelper.decrypt(encrypted, password)
            val keys = parseAPIKeys(decrypted)

            if (keys.isEmpty()) return false

            // ذخیره کلیدها با اولویت
            prefsManager.saveAPIKeys(keys)

            // مدل پیش‌فرض: Qwen 2.5 1.5B (سبک) در صورت موجود بودن
            prefsManager.saveSelectedModel(AIModel.QWEN_2_5_1_5B)
            prefsManager.setProviderPreference(ProviderPreference.SMART_ROUTE)

            // HuggingFace key (برای STT) از BuildConfig یا فایل
            DefaultApiKeys.setHuggingFaceKey(DefaultApiKeys.getHuggingFaceKey() ?: BuildConfig.HF_API_KEY)
            val apiPrefs = getSharedPreferences("api_keys", MODE_PRIVATE)
            DefaultApiKeys.getHuggingFaceKey()
                ?.let { apiPrefs.edit().putString("huggingface_api_key", it).apply() }

            Toast.makeText(this, "کلیدها به‌صورت خودکار فعال شد", Toast.LENGTH_SHORT).show()
            navigateToMain()
            true
        } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "Auto download/activate failed", e)
            false
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
