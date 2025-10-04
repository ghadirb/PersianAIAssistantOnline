package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.databinding.ActivitySettingsBinding
import com.persianai.assistant.services.AIAssistantService
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.launch

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
        
        // حالت کار فعلی
        updateCurrentModeText()
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
        // دکمه تغییر حالت کار
        binding.changeModeButton.setOnClickListener {
            showChangeModeDialog()
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

        // سرویس پس‌زمینه
        binding.backgroundServiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setServiceEnabled(isChecked)
            if (isChecked) {
                startBackgroundService()
            } else {
                stopBackgroundService()
            }
        }

        // بک‌آپ دستی
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
        // Similar to SplashActivity password dialog
        Toast.makeText(this, "قابلیت به‌روزرسانی در نسخه بعدی", Toast.LENGTH_SHORT).show()
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
                // TODO: Implement Google Drive backup
                Toast.makeText(this@SettingsActivity, "بک‌آپ در نسخه بعدی", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performRestore() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SettingsActivity, "در حال بازیابی...", Toast.LENGTH_SHORT).show()
                // TODO: Implement Google Drive restore
                Toast.makeText(this@SettingsActivity, "بازیابی در نسخه بعدی", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
