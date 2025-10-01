package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.R
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.utils.DriveHelper
import com.persianai.assistant.utils.EncryptionHelper
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.launch

/**
 * صفحه شروع برنامه - نمایش توضیحات و دریافت رمز عبور
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // بررسی اینکه آیا قبلاً کلیدها بارگذاری شده‌اند یا نه
        val prefsManager = PreferencesManager(this)
        
        if (prefsManager.hasAPIKeys()) {
            // اگر کلیدها موجود هستند، مستقیم به صفحه اصلی برویم
            navigateToMain()
        } else {
            // نمایش دیالوگ توضیحات و دریافت رمز
            showWelcomeDialog()
        }
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
        
        builder.setNegativeButton("بعداً") { _, _ ->
            // اجازه استفاده بدون کلید API (محدود)
            navigateToMain()
        }
        
        builder.setCancelable(false)
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
                val apiKeys = parseAPIKeys(decryptedData)
                
                if (apiKeys.isEmpty()) {
                    throw Exception("هیچ کلید معتبری یافت نشد")
                }
                
                // ذخیره کلیدها
                val prefsManager = PreferencesManager(this@SplashActivity)
                prefsManager.saveAPIKeys(apiKeys)
                
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

    private fun parseAPIKeys(data: String): List<APIKey> {
        val keys = mutableListOf<APIKey>()
        
        data.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            
            // فرمت: provider:key یا فقط key
            val parts = trimmed.split(":", limit = 2)
            
            if (parts.size == 2) {
                val provider = when (parts[0].lowercase()) {
                    "openai" -> AIProvider.OPENAI
                    "anthropic", "claude" -> AIProvider.ANTHROPIC
                    "openrouter" -> AIProvider.OPENROUTER
                    else -> null
                }
                
                if (provider != null) {
                    keys.add(APIKey(provider, parts[1].trim(), true))
                }
            } else if (parts.size == 1 && trimmed.startsWith("sk-")) {
                // احتمالاً کلید OpenAI
                keys.add(APIKey(AIProvider.OPENAI, trimmed, true))
            }
        }
        
        return keys
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
