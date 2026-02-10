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
import com.persianai.assistant.utils.ModelDownloadManager
import com.persianai.assistant.integration.IviraIntegrationManager
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
        // If app was launched via a share or VIEW intent, forward immediately
        try {
            val incoming = intent
            if (incoming != null && (Intent.ACTION_SEND == incoming.action || Intent.ACTION_VIEW == incoming.action)) {
                val forward = Intent(incoming).setClass(this, VoiceNavigationAssistantActivity::class.java)
                startActivity(forward)
                finish()
                return
            }
        } catch (e: Exception) {
            android.util.Log.w("SplashActivity", "Failed to forward incoming intent", e)
        }
        requestNotificationPermissionIfNeeded()

        lifecycleScope.launch {
            val prefsManager = PreferencesManager(this@SplashActivity)
            try {
                // ✅ مرحله 1: بارگذاری توکن‌های Ivira (اولویت اول)
                android.util.Log.d("SplashActivity", "🔄 Initializing Ivira tokens...")
                val iviraManager = IviraIntegrationManager(this@SplashActivity)
                val iviraLoaded = iviraManager.initializeIviraTokens()
                
                if (iviraLoaded) {
                    android.util.Log.i("SplashActivity", "✅ Ivira tokens loaded successfully")
                    val status = iviraManager.getTokensStatus()
                    android.util.Log.d("SplashActivity", "📊 Ivira status: $status")
                } else {
                    android.util.Log.w("SplashActivity", "⚠️ Ivira tokens failed, using fallback")
                }
                
                // ✅ مرحله 2: بارگذاری خودکار کلیدهای API (fallback)
                android.util.Log.d("SplashActivity", "🔄 Auto-provisioning API keys as fallback...")
                try {
                    val res = com.persianai.assistant.utils.AutoProvisioningManager.autoProvision(this@SplashActivity)
                    if (res.isSuccess) {
                        val keys = res.getOrNull().orEmpty()
                        android.util.Log.i("SplashActivity", "AutoProvision success: ${keys.size} keys (fallback); active=${keys.count { it.isActive }}")
                    } else {
                        android.util.Log.w("SplashActivity", "AutoProvision failed: ${res.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SplashActivity", "AutoProvision exception: ${e.message}", e)
                }
                
                // همگام‌سازی با SharedPreferences
                syncApiPrefs(prefsManager)
                android.util.Log.i("SplashActivity", "Keys applied (${prefsManager.getAPIKeys().size})")
            } catch (e: Exception) {
                android.util.Log.e("SplashActivity", "Error initializing keys", e)
            } finally {
                // مسیر شروع بر اساس ترجیح کاربر (داشبورد یا دستیار) + پیشنهاد دانلود مدل آفلاین
                showOfflineModelPromptIfNeededThenNavigate()
            }
        }
    }

    /**
     * سازگاری با فراخوانی‌های قدیمی؛ اکنون به مقصد شروع ترجیحی هدایت می‌شود
     */
    private fun navigateToMain() {
        navigateToStartDestination()
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
        currentKeys.add(APIKey(provider = AIProvider.OPENROUTER, key = provisioningKey, isActive = true))
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

            val parts = trimmed.split(":", limit = 2)
            if (parts.size == 2) {
                when (parts[0].lowercase()) {
                    "openai" -> keys.add(APIKey(AIProvider.OPENAI, parts[1].trim(), isActive = true))
                    "anthropic", "claude" -> keys.add(APIKey(AIProvider.ANTHROPIC, parts[1].trim(), isActive = true))
                    "openrouter" -> keys.add(APIKey(AIProvider.OPENROUTER, parts[1].trim(), isActive = true))
                    "aiml", "aimlapi", "aimlapi.com" -> keys.add(APIKey(AIProvider.AIML, parts[1].trim(), isActive = true))
                    "avalai" -> keys.add(
                        APIKey(
                            provider = AIProvider.AVALAI,
                            key = parts[1].trim(),
                            baseUrl = "https://avalai.ir/api/v1",
                            isActive = true
                        )
                    )
                    "liara" -> keys.add(
                        APIKey(
                            provider = AIProvider.LIARA,
                            key = parts[1].trim(),
                            baseUrl = "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1",
                            isActive = true
                        )
                    )
                    "huggingface", "hf" -> huggingFaceKey = parts[1].trim()
                }
            } else if (parts.size == 1) {
                val token = trimmed
                when {
                    token.startsWith("sk-or-", ignoreCase = true) -> keys.add(APIKey(AIProvider.OPENROUTER, token, isActive = true))
                    token.startsWith("sk-", ignoreCase = true) -> keys.add(APIKey(AIProvider.OPENAI, token, isActive = true))
                    token.startsWith("hf_", ignoreCase = true) -> huggingFaceKey = token
                }
            }
        }
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
        editor.remove("liara_api_key")
        editor.remove("openrouter_api_key")
        editor.remove("claude_api_key")
        editor.remove("aiml_api_key")
        editor.remove("gladia_api_key")

        prefsManager.getAPIKeys().forEach { key ->
            when (key.provider) {
                AIProvider.OPENAI -> editor.putString("openai_api_key", key.key)
                AIProvider.LIARA -> editor.putString("liara_api_key", key.key)
                AIProvider.ANTHROPIC -> editor.putString("claude_api_key", key.key)
                AIProvider.OPENROUTER -> editor.putString("openrouter_api_key", key.key)
                AIProvider.AIML -> editor.putString("aiml_api_key", key.key)
                AIProvider.GLADIA -> editor.putString("gladia_api_key", key.key)
                AIProvider.AVALAI -> editor.putString("avalai_api_key", key.key)
                AIProvider.LOCAL -> {
                    // مدل آفلاین کلید ندارد
                }
                AIProvider.IVIRA -> {
                    // Ivira uses token manager, not string keys
                }
                AIProvider.GAPGPT -> {
                    // فعلاً نیازی به سینک مستقیم در SharedPreferences قدیمی نیست
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
            append(", liara=" + apiPrefs.getString("liara_api_key", "")?.take(6))
            append(", openrouter=" + apiPrefs.getString("openrouter_api_key", "")?.take(6))
            append(", claude=" + apiPrefs.getString("claude_api_key", "")?.take(6))
            append(", aiml=" + apiPrefs.getString("aiml_api_key", "")?.take(6))
            append(", hf=" + apiPrefs.getString("hf_api_key", "")?.take(6))
        }
        android.util.Log.i("SplashActivity", "syncApiPrefs applied -> $applied")

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

    private fun navigateToStartDestination() {
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

        val prefs = PreferencesManager(this)
        val dest = prefs.getStartDestination()
        val target = when (dest) {
            PreferencesManager.StartDestination.DASHBOARD -> HomeActivity::class.java
            PreferencesManager.StartDestination.ASSISTANT -> AIChatActivity::class.java
        }
        val i = Intent(this, target)
        startActivity(i)
        finish()
    }

    /**
     * پیشنهاد دانلود مدل آفلاین پیشنهادی بر اساس RAM دستگاه
     * فقط یک‌بار نشان داده می‌شود (offline_prompt_shown)
     */
    private fun showOfflineModelPromptIfNeededThenNavigate() {
        val prefs = PreferencesManager(this)
        val mdm = ModelDownloadManager(this)

        val alreadyDownloaded = mdm.findDownloadedModel(prefs.getOfflineModelType()) != null
        if (prefs.isOfflineModelDownloaded() || alreadyDownloaded) {
            prefs.setOfflineModelDownloaded(alreadyDownloaded)
            navigateToStartDestination()
            return
        }

        if (prefs.isOfflinePromptShown()) {
            navigateToStartDestination()
            return
        }

        val recommendedType = ModelDownloadManager.detectRecommendedModel(this)
        val info = mdm.getModelInfo(recommendedType)
        val title = "دانلود مدل آفلاین پیشنهادی"
        val message = "بر اساس سخت‌افزار گوشی، مدل پیشنهادی: ${info.name} (${info.sizeHint}).\nمی‌خواهید الان دانلود شود؟"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("دانلود ${info.name}") { _, _ ->
                prefs.setOfflinePromptShown(true)
                prefs.setOfflineModelType(recommendedType)
                prefs.setOfflineModelDownloaded(false)
                val id = mdm.enqueueDownload(info)
                Toast.makeText(this, "دانلود '${info.name}' شروع شد (آی‌دی: $id)", Toast.LENGTH_LONG).show()
                navigateToStartDestination()
            }
            .setNegativeButton("بعداً") { _, _ ->
                prefs.setOfflinePromptShown(true)
                navigateToStartDestination()
            }
            .setOnCancelListener {
                prefs.setOfflinePromptShown(true)
                navigateToStartDestination()
            }
            .show()
    }
}
