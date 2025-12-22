package com.persianai.assistant.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.R
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.utils.DefaultApiKeys
import com.persianai.assistant.utils.DriveHelper
import com.persianai.assistant.utils.EncryptionHelper
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/**
 * صفحه شروع برنامه - نمایش توضیحات و دریافت رمز عبور
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        requestNotificationPermissionIfNeeded()

        lifecycleScope.launch {
            val prefsManager = PreferencesManager(this@SplashActivity)
            try {
                if (prefsManager.hasAPIKeys()) {
                    // کلیدهای موجود را همگام کن
                    syncApiPrefs(prefsManager)
                    android.util.Log.i("SplashActivity", "Keys already present (${prefsManager.getAPIKeys().size})")
                } else {
                    // تلاش خودکار برای دریافت و فعال‌سازی کلیدها (بدون دیالوگ)
                    attemptSilentAutoActivationAndSync(prefsManager)
                }
            } catch (e: Exception) {
                android.util.Log.e("SplashActivity", "Error initializing keys", e)
            } finally {
                // همیشه به داشبورد برو
                navigateToMain()
            }
        }
    }

    /**
     * تلاش خودکار برای دانلود و فعال‌سازی کلیدها با رمز پیش‌فرض ۱۳۴۵
     * بدون نمایش دیالوگ؛ در صورت موفقیت مستقیم به Main می‌رود.
     */
    private suspend fun attemptSilentAutoActivationAndSync(prefsManager: PreferencesManager): Boolean = withContext(Dispatchers.IO) {
        try {
            // رمز صحیح فایل Drive (مطابق فایل نمونه در key/): 12345
            val password = "12345"
            val encryptedData = try {
                DriveHelper.downloadEncryptedKeys()
            } catch (e: Exception) {
                android.util.Log.w("SplashActivity", "Drive download failed, trying local file", e)
                readLocalEncryptedKeys()
            }
            val decryptedData = EncryptionHelper.decrypt(encryptedData, password)
            val apiKeys = parseAPIKeys(decryptedData)
            if (apiKeys.isEmpty()) throw Exception("هیچ کلید معتبری یافت نشد")

            prefsManager.saveAPIKeys(apiKeys)
            // همگام‌سازی با SharedPreferences برای سایر ماژول‌ها
            withContext(Dispatchers.Main) { syncApiPrefs(prefsManager) }
            android.util.Log.i("SplashActivity", "API keys auto-activated (${apiKeys.size})")
            true
        } catch (e: Exception) {
            android.util.Log.w("SplashActivity", "Silent auto-activation failed: ${e.message}")
            false
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
                    Toast.makeText(
                        this@SplashActivity,
                        "دانلود از Drive ناموفق. تلاش از فایل محلی...",
                        Toast.LENGTH_SHORT
                    ).show()
                    readLocalEncryptedKeys()
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
        syncApiPrefs(prefsManager)
        
        Toast.makeText(this, "کلید Provisioning اعمال شد", Toast.LENGTH_SHORT).show()
        navigateToMain()
        return true
    }

    private fun parseAPIKeys(data: String): List<APIKey> {
        val keys = mutableListOf<APIKey>()
        var huggingFaceKey: String? = null
        
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
                    "aiml", "aimlapi", "aimlapi.com" -> AIProvider.AIML
                    "huggingface", "hf" -> {
                        huggingFaceKey = parts[1].trim()
                        null
                    }
                    else -> null
                }

                if (provider != null) {
                    keys.add(APIKey(provider, parts[1].trim(), true))
                }
            } else if (parts.size == 1) {
                // تشخیص خودکار بر اساس الگو
                val token = trimmed
                when {
                    token.startsWith("sk-or-", ignoreCase = true) -> {
                        keys.add(APIKey(AIProvider.OPENROUTER, token, true))
                    }
                    token.startsWith("sk-", ignoreCase = true) -> {
                        keys.add(APIKey(AIProvider.OPENAI, token, true))
                    }
                    token.startsWith("hf_", ignoreCase = true) -> {
                        huggingFaceKey = token
                    }
                    token.contains("aiml", ignoreCase = true) || token.contains("aimlapi", ignoreCase = true) -> {
                        keys.add(APIKey(AIProvider.AIML, token, true))
                    }
                }
            }
        }

        // ذخیره کلید HuggingFace برای STT
        huggingFaceKey?.let {
            getSharedPreferences("api_keys", MODE_PRIVATE)
                .edit()
                .putString("hf_api_key", it)
                .apply()
        }
        
        return keys
    }

    /**
     * همگام‌سازی کلیدهای ذخیره‌شده در PreferencesManager با SharedPreferences عمومی (api_keys)
     * برای استفاده همه Activity ها (از جمله Dashboard/Assistant/Voice Nav)
     */
    private fun syncApiPrefs(prefsManager: PreferencesManager) {
        val apiPrefs = getSharedPreferences("api_keys", MODE_PRIVATE)
        val editor = apiPrefs.edit()

        // کلید فعلی HuggingFace را نگه داریم تا پاک نشود
        val existingHfKey = apiPrefs.getString("hf_api_key", null)

        // پاک‌سازی کلیدهای قبلی برای جلوگیری از تضاد
        editor.remove("openai_api_key")
        editor.remove("openrouter_api_key")
        editor.remove("claude_api_key")
        editor.remove("aiml_api_key")

        prefsManager.getAPIKeys().forEach { key ->
            when (key.provider) {
                AIProvider.OPENAI -> editor.putString("openai_api_key", key.key)
                AIProvider.ANTHROPIC -> editor.putString("claude_api_key", key.key)
                AIProvider.OPENROUTER -> editor.putString("openrouter_api_key", key.key)
                AIProvider.AIML -> editor.putString("aiml_api_key", key.key)
                AIProvider.LOCAL -> {
                    // مدل آفلاین کلید ندارد
                }
            }
        }

        // HuggingFace: اگر در prefs نبود، از DefaultApiKeys پر شود
        val hfToApply = existingHfKey
            ?: apiPrefs.getString("hf_api_key", null)
            ?: DefaultApiKeys.getHuggingFaceKey()
        hfToApply?.takeIf { it.isNotBlank() }?.let { editor.putString("hf_api_key", it) }

        editor.apply()

        // لاگ برای اطمینان از همگام‌سازی کلیدها
        val applied = buildString {
            append("openai=" + apiPrefs.getString("openai_api_key", "")?.take(6))
            append(", openrouter=" + apiPrefs.getString("openrouter_api_key", "")?.take(6))
            append(", claude=" + apiPrefs.getString("claude_api_key", "")?.take(6))
            append(", aiml=" + apiPrefs.getString("aiml_api_key", "")?.take(6))
            append(", hf=" + apiPrefs.getString("hf_api_key", "")?.take(6))
        }
        android.util.Log.i("SplashActivity", "syncApiPrefs applied -> $applied")
    }

    /**
     * درخواست runtime مجوز اعلان برای heads-up/full-screen در Android 13+
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    2001
                )
            }
        }
    }

    /**
     * خواندن فایل رمزشده از مسیرهای محلی (برای مواقع بدون اینترنت یا اندرویدهای قدیمی)
     */
    private fun readLocalEncryptedKeys(): String {
        val candidatePaths = listOf(
            File(getExternalFilesDir(null), "encrypted_keys.b64.txt"),
            File(getExternalFilesDir(null), "key/encrypted_keys.b64.txt"),
            File(Environment.getExternalStorageDirectory(), "Download/encrypted_keys.b64.txt"),
            File(Environment.getExternalStorageDirectory(), "PersianAIAssistantOnline/key/encrypted_keys.b64.txt")
        )

        for (path in candidatePaths) {
            if (path.exists() && path.canRead()) {
                android.util.Log.i("SplashActivity", "Reading local encrypted keys: ${path.absolutePath}")
                return path.readText(Charset.defaultCharset())
            }
        }
        throw Exception("فایل محلی encrypted_keys.b64.txt یافت نشد")
    }

    private fun navigateToMain() {
        val incoming = intent
        if (incoming != null && (Intent.ACTION_SEND == incoming.action || Intent.ACTION_VIEW == incoming.action)) {
            try {
                val forward = Intent(incoming).setClass(this, VoiceNavigationAssistantActivity::class.java)
                startActivity(forward)
                finish()
                return
            } catch (e: Exception) {
                android.util.Log.w("SplashActivity", "Failed to forward share intent", e)
            }
        }

        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}
