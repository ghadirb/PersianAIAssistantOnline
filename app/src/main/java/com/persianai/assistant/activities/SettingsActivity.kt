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
import com.persianai.assistant.utils.DriveHelper
import com.persianai.assistant.utils.EncryptionHelper
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
        
        // حالت کار فعلی
        updateCurrentModeText()
        
        // وضعیت مدل آفلاین
        updateOfflineModelStatus()
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
    
    private fun updateOfflineModelStatus() {
        val modelType = prefsManager.getOfflineModelType()

        // نمایش نوع مدل انتخاب‌شده در تنظیمات
        binding.offlineModelType.text = "نوع: ${modelType.displayName} (${modelType.size})"

        // خواندن وضعیت واقعی از OfflineModelManager
        val modelManager = com.persianai.assistant.models.OfflineModelManager(this)
        val downloadedModels = modelManager.getDownloadedModels()

        if (downloadedModels.isNotEmpty()) {
            val names = downloadedModels.joinToString("، ") { it.first.name }
            binding.offlineModelStatus.text = "✅ مدل(ها) آماده است: $names"
            binding.offlineModelStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            // دکمه را برای مدیریت دوباره باز می‌گذاریم
            binding.downloadModelButton.visibility = android.view.View.VISIBLE
            binding.downloadModelButton.text = "مدیریت مدل‌های آفلاین"
            binding.deleteModelButton.visibility = android.view.View.GONE
        } else {
            binding.offlineModelStatus.text = "❌ مدل دانلود نشده"
            binding.offlineModelStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            binding.downloadModelButton.visibility = android.view.View.VISIBLE
            binding.downloadModelButton.text = "دانلود / مدیریت مدل‌های آفلاین"
            binding.deleteModelButton.visibility = android.view.View.GONE
        }
    }

    private fun setupListeners() {
        // دکمه نمایش راهنما
        binding.showWelcomeButton.setOnClickListener {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.putExtra("SHOW_HELP", true)  // علامت نمایش راهنما
            startActivity(intent)
        }
        
        // دکمه تغییر حالت کار
        binding.changeModeButton.setOnClickListener {
            showChangeModeDialog()
        }
        
        // دکمه انتخاب نوع مدل
        binding.selectModelTypeButton.setOnClickListener {
            showSelectModelTypeDialog()
        }
        
        // دکمه دانلود / مدیریت مدل آفلاین
        binding.downloadModelButton.setOnClickListener {
            try {
                val intent = Intent(this, OfflineModelsActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Error opening OfflineModelsActivity", e)
                Toast.makeText(this, "خطا در باز کردن مدل‌های آفلاین: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        // دکمه حذف مدل
        binding.deleteModelButton.setOnClickListener {
            showDeleteModelDialog()
        }
        
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
        
        // دکمه به‌روزرسانی کلیدها
        binding.refreshKeysButton.setOnClickListener {
            showPasswordDialogForRefresh()
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
    }

    private fun showPasswordDialogForRefresh() {
        val input = android.widget.EditText(this).apply {
            hint = "رمز عبور کلیدها"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("🔑 بروزرسانی کلیدهای API")
            .setMessage("لطفاً رمز عبور کلیدهای API را وارد کنید:")
            .setView(input)
            .setPositiveButton("دانلود") { _, _ ->
                val password = input.text.toString()
                if (password.isNotBlank()) {
                    downloadAPIKeys(password)
                } else {
                    Toast.makeText(this, "⚠️ رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
    
    private fun downloadAPIKeys(password: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SettingsActivity, "در حال دانلود کلیدها...", Toast.LENGTH_SHORT).show()
                
                val encryptedData = DriveHelper.downloadEncryptedKeys()
                val decryptedData = EncryptionHelper.decrypt(encryptedData, password)
                val keys = parseAPIKeys(decryptedData)
                
                withContext(Dispatchers.Main) {
                    if (keys.isNotEmpty()) {
                        prefsManager.saveAPIKeys(keys)
                        loadSettings()
                        Toast.makeText(
                            this@SettingsActivity,
                            "✅ ${keys.size} کلید دانلود شد",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            "❌ کلیدی پیدا نشد",
                            Toast.LENGTH_SHORT
                        ).show()
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
                    else -> null
                }
                
                if (provider != null) {
                    keys.add(com.persianai.assistant.models.APIKey(provider, parts[1].trim(), true))
                }
            } else if (parts.size == 1 && trimmed.startsWith("sk-")) {
                val provider = when {
                    trimmed.startsWith("sk-proj-") -> com.persianai.assistant.models.AIProvider.OPENAI
                    trimmed.startsWith("sk-or-") -> com.persianai.assistant.models.AIProvider.OPENROUTER
                    trimmed.length == 51 && trimmed.startsWith("sk-") -> com.persianai.assistant.models.AIProvider.ANTHROPIC
                    else -> com.persianai.assistant.models.AIProvider.OPENAI
                }
                keys.add(com.persianai.assistant.models.APIKey(provider, trimmed, true))
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

    private fun showSelectModelTypeDialog() {
        val modelType = prefsManager.getOfflineModelType()
        val types = PreferencesManager.OfflineModelType.values()
        
        val options = types.map { 
            "${it.displayName}\n${it.size} - ${it.description}"
        }.toTypedArray()
        
        val selectedIndex = types.indexOf(modelType)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب نوع مدل آفلاین")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val selectedType = types[which]
                prefsManager.setOfflineModelType(selectedType)
                
                // اگر مدل دانلود شده بود، باید دوباره دانلود کنه
                if (prefsManager.isOfflineModelDownloaded()) {
                    prefsManager.setOfflineModelDownloaded(false)
                    Toast.makeText(
                        this,
                        "✅ نوع مدل تغییر کرد. لطفاً دوباره دانلود کنید.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                updateOfflineModelStatus()
                dialog.dismiss()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showDownloadModelDialog() {
        val modelType = prefsManager.getOfflineModelType()
        
        val capabilities = when (modelType) {
            PreferencesManager.OfflineModelType.BASIC -> """
                ✅ باز کردن برنامه‌ها
                ✅ یادآوری و تایمر
                ✅ دستورات ساده سیستمی
                ❌ پاسخ به سوالات
                ❌ محاسبات پیچیده
            """.trimIndent()
            
            PreferencesManager.OfflineModelType.LITE -> """
                ✅ همه قابلیت‌های نوع ساده
                ✅ پاسخ به سوالات ساده
                ✅ محاسبات و ترجمه
                ✅ خلاصه‌سازی متن
                ⚠️ پاسخ‌ها کوتاه‌تر از آنلاین
            """.trimIndent()
            
            PreferencesManager.OfflineModelType.FULL -> """
                ✅ تمام قابلیت‌های نوع سبک
                ✅ پاسخ‌های دقیق و کامل
                ✅ مکالمه طبیعی
                ✅ تولید متن خلاقانه
                ⚠️ نیاز به موبایل قوی (4GB+ RAM)
                ⚠️ مصرف باتری بالاتر
            """.trimIndent()
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("دانلود ${modelType.displayName}")
            .setMessage(
                "قابلیت‌ها:\n\n$capabilities\n\n" +
                "📦 حجم: ${modelType.size}\n" +
                "🔒 حریم خصوصی: کامل (بدون اینترنت)\n\n" +
                "آیا میخواهید دانلود شود؟"
            )
            .setPositiveButton("دانلود") { _, _ ->
                startModelDownload()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
    
    private fun startModelDownload() {
        val downloadManager = com.persianai.assistant.utils.ModelDownloadManager(this)
        val progressDialog = android.app.ProgressDialog(this).apply {
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            show()
        }
        
        lifecycleScope.launch {
            downloadManager.downloadModel(prefsManager.getOfflineModelType()) { progress ->
                progressDialog.progress = progress
            }
            progressDialog.dismiss()
            prefsManager.setOfflineModelDownloaded(true)
            updateOfflineModelStatus()
            Toast.makeText(this@SettingsActivity, "✅ دانلود شد", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDeleteModelDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف مدل آفلاین")
            .setMessage("مطمئنید؟")
            .setPositiveButton("حذف") { _, _ ->
                com.persianai.assistant.utils.ModelDownloadManager(this).deleteModel()
                prefsManager.setOfflineModelDownloaded(false)
                updateOfflineModelStatus()
                Toast.makeText(this, "✅ حذف شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    private fun showChangeModeDialog() {
        val modes = arrayOf(
            "🌐 آنلاین - پاسخ‌های دقیق با AI پیشرفته",
            "📱 آفلاین - بدون نیاز به اینترنت",
            "⚡ ترکیبی - بهترین تعادل (پیشنهادی)"
        )
        
        val currentMode = prefsManager.getWorkingMode()
        val selectedIndex = when (currentMode) {
            PreferencesManager.WorkingMode.ONLINE -> 0
            PreferencesManager.WorkingMode.OFFLINE -> 1
            PreferencesManager.WorkingMode.HYBRID -> 2
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب حالت کار")
            .setSingleChoiceItems(modes, selectedIndex) { dialog, which ->
                val newMode = when (which) {
                    0 -> PreferencesManager.WorkingMode.ONLINE
                    1 -> PreferencesManager.WorkingMode.OFFLINE
                    else -> PreferencesManager.WorkingMode.HYBRID
                }
                
                prefsManager.setWorkingMode(newMode)
                updateCurrentModeText()
                
                val modeText = when (newMode) {
                    PreferencesManager.WorkingMode.ONLINE -> "آنلاین"
                    PreferencesManager.WorkingMode.OFFLINE -> "آفلاین"
                    PreferencesManager.WorkingMode.HYBRID -> "ترکیبی"
                }
                
                Toast.makeText(this, "✅ حالت $modeText فعال شد", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("انصراف", null)
            .show()
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
