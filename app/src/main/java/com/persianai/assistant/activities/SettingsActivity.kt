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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.persianai.assistant.utils.ModelDownloadManager

/**
 * صفحه تنظیمات
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var modelDownloadManager: ModelDownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "تنظیمات"

        prefsManager = PreferencesManager(this)
        modelDownloadManager = ModelDownloadManager(this)
        
        // نمایش کارت مدل آفلاین و انتخاب مقصد شروع
        binding.offlineModelCard.visibility = View.VISIBLE
        binding.coquiTtsCard.visibility = View.GONE
        binding.changeModeButton.visibility = View.VISIBLE
        binding.currentModeText.text = "صفحه شروع: ${prefsManager.getStartDestination().name}"
        setupRecordingModeUI()

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

        // مقصد شروع
        binding.currentModeText.text = "صفحه شروع: ${if (prefsManager.getStartDestination() == PreferencesManager.StartDestination.DASHBOARD) "داشبورد" else "دستیار"}"
        refreshRecordingModeUI()
        updateOfflineModelSection()
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
        
        // انتخاب صفحه شروع (داشبورد/دستیار)
        binding.changeModeButton.setOnClickListener {
            showStartDestinationDialog()
        }
        binding.selectModelTypeButton.setOnClickListener {
            showModelChoiceDialog()
        }
        binding.downloadModelButton.setOnClickListener {
            startSelectedModelDownload()
        }
        binding.deleteModelButton.setOnClickListener {
            deleteCurrentModel()
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

    private fun setupRecordingModeUI() {
        try {
            refreshRecordingModeUI()
            binding.recordingModeToggle.addOnButtonCheckedListener { group: MaterialButtonToggleGroup, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                when (checkedId) {
                    binding.btnModeFast.id -> {
                        prefsManager.setRecordingMode(PreferencesManager.RecordingMode.FAST)
                        binding.recordingModeDesc.text = "حالت فعلی: سریع (ارسال خودکار)"
                        Toast.makeText(this, "حالت ضبط: سریع (ارسال خودکار)", Toast.LENGTH_SHORT).show()
                    }
                    binding.btnModePrecise.id -> {
                        prefsManager.setRecordingMode(PreferencesManager.RecordingMode.PRECISE)
                        binding.recordingModeDesc.text = "حالت فعلی: دقیق (تأیید دستی)"
                        Toast.makeText(this, "حالت ضبط: دقیق (تأیید دستی)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private fun refreshRecordingModeUI() {
        val mode = prefsManager.getRecordingMode()
        when (mode) {
            PreferencesManager.RecordingMode.FAST -> {
                binding.recordingModeDesc.text = "حالت فعلی: سریع (ارسال خودکار)"
                binding.recordingModeToggle.check(binding.btnModeFast.id)
            }
            PreferencesManager.RecordingMode.PRECISE -> {
                binding.recordingModeDesc.text = "حالت فعلی: دقیق (تأیید دستی)"
                binding.recordingModeToggle.check(binding.btnModePrecise.id)
            }
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

    private fun showStartDestinationDialog() {
        val options = arrayOf("داشبورد", "دستیار")
        val current = prefsManager.getStartDestination()
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب صفحه شروع")
            .setSingleChoiceItems(options, if (current == PreferencesManager.StartDestination.DASHBOARD) 0 else 1) { dialog, which ->
                val dest = if (which == 0) PreferencesManager.StartDestination.DASHBOARD else PreferencesManager.StartDestination.ASSISTANT
                prefsManager.setStartDestination(dest)
                binding.currentModeText.text = "صفحه شروع: ${options[which]}"
                dialog.dismiss()
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun updateOfflineModelSection() {
        val recommended = ModelDownloadManager.detectRecommendedModel(this)
        val selected = prefsManager.getOfflineModelType()
        val info = modelDownloadManager.getModelInfo(selected)
        val downloadedInfo = modelDownloadManager.findDownloadedModel(selected)
        binding.offlineModelType.text = "${info.name} (${info.sizeHint})\nپیشنهاد دستگاه: ${modelDownloadManager.getModelInfo(recommended).name}"
        val downloaded = downloadedInfo != null && modelDownloadManager.isModelDownloaded(downloadedInfo)
        if (downloaded) {
            prefsManager.setOfflineModelDownloaded(true)
            binding.offlineModelStatus.text = "✅ مدل دانلود شده (${downloadedInfo!!.name})"
            binding.deleteModelButton.visibility = View.VISIBLE
        } else {
            prefsManager.setOfflineModelDownloaded(false)
            binding.offlineModelStatus.text = "❌ مدل دانلود نشده"
            binding.deleteModelButton.visibility = View.GONE
        }
    }

    private fun startRecommendedModelDownload() {
        val recommended = ModelDownloadManager.detectRecommendedModel(this)
        prefsManager.setOfflineModelType(recommended)
        val info = modelDownloadManager.getModelInfo(recommended)
        val id = modelDownloadManager.enqueueDownload(info)
        Toast.makeText(this, "دانلود '${info.name}' شروع شد (آی‌دی: $id)", Toast.LENGTH_LONG).show()
        binding.offlineModelStatus.text = "⬇️ در حال دانلود..."
    }

    /**
     * دانلود بر اساس مدل انتخاب‌شده کاربر (نه فقط پیشنهادی)
     */
    private fun startSelectedModelDownload() {
        val selected = prefsManager.getOfflineModelType()
        val info = modelDownloadManager.getModelInfo(selected)
        val id = modelDownloadManager.enqueueDownload(info)
        Toast.makeText(this, "دانلود '${info.name}' شروع شد (آی‌دی: $id)", Toast.LENGTH_LONG).show()
        binding.offlineModelStatus.text = "⬇️ در حال دانلود..."
    }

    private fun deleteCurrentModel() {
        val info = modelDownloadManager.findDownloadedModel(prefsManager.getOfflineModelType())
            ?: modelDownloadManager.getModelInfo(prefsManager.getOfflineModelType())
        modelDownloadManager.deleteModel(info)
        prefsManager.setOfflineModelDownloaded(false)
        updateOfflineModelSection()
        Toast.makeText(this, "مدل حذف شد", Toast.LENGTH_SHORT).show()
    }

    private fun showModelChoiceDialog() {
        val items = arrayOf("TinyLlama (سبک)", "Qwen 0.5B (متوسط)", "Qwen 1.5B (قوی)")
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب مدل آفلاین")
            .setItems(items) { dialog, which ->
                val type = when (which) {
                    0 -> PreferencesManager.OfflineModelType.BASIC
                    1 -> PreferencesManager.OfflineModelType.LITE
                    else -> PreferencesManager.OfflineModelType.FULL
                }
                prefsManager.setOfflineModelType(type)
                val info = modelDownloadManager.getModelInfo(type)
                binding.offlineModelType.text = "${info.name} (${info.sizeHint})"
                updateOfflineModelSection()
                dialog.dismiss()
            }
            .setNegativeButton("بستن", null)
            .show()
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
