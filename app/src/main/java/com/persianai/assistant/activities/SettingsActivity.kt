package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.databinding.ActivitySettingsBinding
import com.persianai.assistant.services.AIAssistantService
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.utils.AutoProvisioningManager
import com.persianai.assistant.utils.DriveHelper
import com.persianai.assistant.utils.EncryptionHelper
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * صفحه تنظیمات
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "تنظیمات"

        prefsManager = PreferencesManager(this)
        
        // Hide offline-related cards completely
        binding.changeModeButton.visibility = View.GONE
        binding.currentModeText.text = "حالت فعلی: آنلاین 🌐"
        binding.offlineModelCard.visibility = View.GONE
        binding.coquiTtsCard.visibility = View.GONE

        loadSettings()
        setupListeners()
    }
    
    override fun onResume() {
        super.onResume()
        loadSettings()
    }

    private fun loadSettings() {
        // وضعیت API Keys
        val keys = prefsManager.getAPIKeys()
        val activeKeys = keys.filter { it.isActive }
        binding.apiKeysStatus.text = "کلیدهای فعال: ${activeKeys.size} از ${keys.size}"
        
        android.util.Log.d("SettingsActivity", "Keys: total=${keys.size}, active=${activeKeys.size}")
        
        // مدل فعلی
        val currentModel = prefsManager.getSelectedModel()
        binding.currentModel.text = "مدل فعلی: ${currentModel.displayName}"
        
        // وضعیت سرویس پس‌زمینه
        val serviceEnabled = prefsManager.isServiceEnabled()
        binding.backgroundServiceSwitch.isChecked = serviceEnabled
        android.util.Log.d("SettingsActivity", "Service enabled: $serviceEnabled")

        binding.persistentNotificationSwitch.isChecked = prefsManager.isPersistentStatusNotificationEnabled()
        binding.persistentNotificationActionsSwitch.isChecked = prefsManager.isPersistentNotificationActionsEnabled()
        
        // وضعیت TTS
        binding.ttsSwitch.isChecked = prefsManager.isTTSEnabled()

        // حالت کار فعلی (اجباری آنلاین)
        binding.currentModeText.text = "حالت فعلی: آنلاین 🌐"
    }

    private fun updateCurrentModeText() {
        val mode = prefsManager.getWorkingMode()
        val modeText = when (mode) {
            PreferencesManager.WorkingMode.ONLINE -> "آنلاین 🌐"
            PreferencesManager.WorkingMode.OFFLINE -> "آفلاین 📱"
            PreferencesManager.WorkingMode.HYBRID -> "ترکیبی ⚡"
        }
        binding.currentModeText.text = "حالت فعلی: $modeText"
    }

    private fun setupListeners() {
        // دکمه نمایش راهنما
        binding.showWelcomeButton.setOnClickListener {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.putExtra("SHOW_HELP", true)  // علامت نمایش راهنما
            startActivity(intent)
        }
        
        // حالت و مدل آفلاین غیرفعال شده‌اند
        binding.changeModeButton.setOnClickListener { /* no-op: forced ONLINE */ }
        binding.selectModelTypeButton.setOnClickListener { /* no-op */ }
        binding.downloadModelButton.setOnClickListener { /* no-op */ }
        binding.deleteModelButton.setOnClickListener { /* no-op */ }

        // دکمه مدیریت برنامه‌های متصل
        binding.manageAppsButton.setOnClickListener {
            try {
                android.util.Log.d("SettingsActivity", "Opening ConnectedAppsActivity...")
                val intent = Intent(this, ConnectedAppsActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Error opening ConnectedAppsActivity", e)
                android.widget.Toast.makeText(this, "خطا: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
        // دکمه راهنمای ارسال خودکار
        binding.accessibilityGuideButton.setOnClickListener {
            val intent = Intent(this, AccessibilityGuideActivity::class.java)
            startActivity(intent)
        }
        
        // دکمه به‌روزرسانی کلیدها - بدون درخواست رمز، فقط Liara فعال
        binding.refreshKeysButton.setOnClickListener {
            refreshKeysFromGist()
        }
        // دکمه پاک کردن کلیدها
        binding.clearKeysButton.setOnClickListener {
            showClearKeysDialog()
        }

        // Switch سرویس پس‌زمینه
        binding.backgroundServiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!prefsManager.isPersistentStatusNotificationEnabled()) {
                    prefsManager.setServiceEnabled(false)
                    binding.backgroundServiceSwitch.isChecked = false
                    Toast.makeText(this, "برای سرویس پس‌زمینه، نوتیفیکیشن وضعیت باید فعال باشد", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                prefsManager.setServiceEnabled(true)
                startBackgroundService()
            } else {
                prefsManager.setServiceEnabled(false)
                stopBackgroundService()
            }
        }

        binding.persistentNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setPersistentStatusNotificationEnabled(isChecked)
            if (!isChecked && prefsManager.isServiceEnabled()) {
                prefsManager.setServiceEnabled(false)
                binding.backgroundServiceSwitch.isChecked = false
                stopBackgroundService()
            }
        }

        binding.persistentNotificationActionsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setPersistentNotificationActionsEnabled(isChecked)
            if (prefsManager.isServiceEnabled()) {
                startBackgroundService()
            }
        }
        binding.backupButton.setOnClickListener {
            performBackup()
        }

        // بازیابی بک‌آپ
        binding.restoreButton.setOnClickListener {
            performRestore()
        }

        // درباره برنامه
        binding.aboutButton.setOnClickListener {
            showAboutDialog()
        }

        binding.downloadCoquiTtsButton.setOnClickListener { /* no-op */ }
        binding.openCoquiDriveButton.setOnClickListener { /* no-op */ }

        binding.addOpenAiKeyButton.setOnClickListener {
            promptAddOpenAiKey()
        }
    }

    private fun refreshKeysFromGist() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SettingsActivity, "در حال دانلود و فعال‌سازی کلیدها...", Toast.LENGTH_SHORT).show()
                val result = AutoProvisioningManager.autoProvision(this@SettingsActivity)
                withContext(Dispatchers.Main) {
                    result.onSuccess { keys ->
                        android.util.Log.d("SettingsActivity", "✅ کلیدها دانلود و فعال شدند:")
                        keys.forEach { k ->
                            android.util.Log.d("SettingsActivity", "  - ${k.provider.name}: ${if (k.isActive) "✔ ACTIVE" else "✕ INACTIVE"} base=${k.baseUrl}")
                        }
                        loadSettings()
                        Toast.makeText(
                            this@SettingsActivity,
                            "✅ ${keys.count { it.isActive }} کلید فعال شد",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { e ->
                        Toast.makeText(
                            this@SettingsActivity,
                            "❌ خطا در دانلود/فعال‌سازی: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        android.util.Log.e("SettingsActivity", "AutoProvisioning from SettingsActivity failed", e)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "❌ خطا: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun promptAddOpenAiKey() {
        val editText = android.widget.EditText(this).apply {
            hint = "sk-proj-..."
            setSingleLine()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("افزودن کلید OpenAI (sk-proj-...)")
            .setView(editText)
            .setPositiveButton("ذخیره") { dialog, _ ->
                val token = editText.text?.toString()?.trim().orEmpty()
                if (token.startsWith("sk-proj-")) {
                    val key = APIKey(
                        provider = AIProvider.OPENAI,
                        key = token,
                        baseUrl = "https://api.openai.com/v1",
                        isActive = true
                    )
                    val all = prefsManager.getAPIKeys().toMutableList().apply {
                        add(key)
                    }
                    prefsManager.saveAPIKeys(all)
                    loadSettings()
                    Toast.makeText(this, "✅ کلید اضافه شد و فعال است", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ فرمت باید با sk-proj- شروع شود", Toast.LENGTH_LONG).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("انصراف") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    
    private fun showPasswordDialogForRefresh() {
        // غیرفعال - رمز دیگر درخواست نمی‌شود
    }
    
    private fun downloadAPIKeys(password: String) {
        // غیرفعال - از refreshKeysFromGist استفاده کنید
    }
    
    private fun parseAPIKeys(data: String): List<com.persianai.assistant.models.APIKey> {
        val keys = mutableListOf<com.persianai.assistant.models.APIKey>()
        
        data.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            
            val parts = trimmed.split(":", limit = 2)
            
            if (parts.size == 2) {
                val provider = when (parts[0].lowercase()) {
                    "openai" -> com.persianai.assistant.models.AIProvider.OPENAI
                    "anthropic", "claude" -> com.persianai.assistant.models.AIProvider.ANTHROPIC
                    "openrouter" -> com.persianai.assistant.models.AIProvider.OPENROUTER
                    "liara" -> com.persianai.assistant.models.AIProvider.LIARA
                    else -> null
                }
                
                if (provider != null) {
                    val token = parts[1].trim()
                    if (provider == com.persianai.assistant.models.AIProvider.LIARA) {
                        keys.add(
                            com.persianai.assistant.models.APIKey(
                                provider = com.persianai.assistant.models.AIProvider.LIARA,
                                key = token,
                                baseUrl = "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1",
                                isActive = false
                            )
                        )
                    } else {
                        keys.add(com.persianai.assistant.models.APIKey(provider, token, isActive = false))
                    }
                }
            } else if (parts.size == 1 && trimmed.startsWith("sk-")) {
                val provider = when {
                    trimmed.startsWith("sk-proj-") -> com.persianai.assistant.models.AIProvider.OPENAI
                    trimmed.startsWith("sk-or-") -> com.persianai.assistant.models.AIProvider.OPENROUTER
                    trimmed.length == 51 && trimmed.startsWith("sk-") -> com.persianai.assistant.models.AIProvider.ANTHROPIC
                    else -> com.persianai.assistant.models.AIProvider.OPENAI
                }
                keys.add(com.persianai.assistant.models.APIKey(provider, trimmed, isActive = false))
            }
        }
        
        return keys
    }

    private fun showClearKeysDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("پاک کردن کلیدها")
            .setMessage("آیا مطمئن هستید که می‌خواهید تمام کلیدهای API را پاک کنید؟")
            .setPositiveButton("بله") { _, _ ->
                prefsManager.clearAPIKeys()
                loadSettings()
                Toast.makeText(this, "کلیدها پاک شدند", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    private fun startBackgroundService() {
        val intent = Intent(this, AIAssistantService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "سرویس پس‌زمینه فعال شد", Toast.LENGTH_SHORT).show()
    }

    private fun stopBackgroundService() {
        val intent = Intent(this, AIAssistantService::class.java)
        stopService(intent)
        Toast.makeText(this, "سرویس پس‌زمینه غیرفعال شد", Toast.LENGTH_SHORT).show()
    }

    private fun performBackup() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SettingsActivity, "در حال بک‌آپ...", Toast.LENGTH_SHORT).show()
                
                withContext(Dispatchers.IO) {
                    val backupFile = com.persianai.assistant.utils.BackupManager.createBackup(this@SettingsActivity)
                    
                    withContext(Dispatchers.Main) {
                        com.persianai.assistant.utils.BackupManager.shareBackup(this@SettingsActivity, backupFile)
                        Toast.makeText(
                            this@SettingsActivity, 
                            "✅ بک‌آپ آماده است! Gmail را انتخاب کنید",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performRestore() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "انتخاب فایل بک‌آپ"), REQUEST_CODE_RESTORE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_RESTORE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                lifecycleScope.launch {
                    try {
                        val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        if (content != null) {
                            val success = withContext(Dispatchers.IO) {
                                com.persianai.assistant.utils.BackupManager.restoreBackup(this@SettingsActivity, content)
                            }
                            if (success) {
                                Toast.makeText(this@SettingsActivity, "✅ بازیابی موفق!", Toast.LENGTH_SHORT).show()
                                loadSettings()
                            } else {
                                Toast.makeText(this@SettingsActivity, "❌ خطا در بازیابی", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    companion object {
        private const val REQUEST_CODE_RESTORE = 1001
    }


    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("درباره برنامه")
            .setMessage("""
                Persian AI Assistant
                نسخه 1.0.0
                
                یک دستیار هوش مصنوعی قدرتمند و چندمنظوره
                
                ویژگی‌ها:
                • استفاده از مدل‌های GPT-4o و Claude
                • تشخیص صوت و تحلیل فایل‌های صوتی
                • حافظه بلندمدت و پشتیبان‌گیری
                • سرویس پس‌زمینه
                
                توسعه‌دهنده: Ghadir
                GitHub: github.com/ghadirb/PersianAIAssistantOnline
            """.trimIndent())
            .setPositiveButton("باشه", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
