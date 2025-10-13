package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityAccessibilityGuideBinding
import com.persianai.assistant.services.MessageAutomationService

class AccessibilityGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccessibilityGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccessibilityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupUI()
        checkServiceStatus()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "ارسال خودکار پیام"
        }
    }

    private fun setupUI() {
        binding.openSettingsButton.setOnClickListener {
            showConfirmationDialog()
        }

        binding.testButton.setOnClickListener {
            if (MessageAutomationService.isServiceEnabled) {
                android.widget.Toast.makeText(
                    this,
                    "✅ سرویس فعال است! حالا می‌توانید پیام ارسال کنید",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                android.widget.Toast.makeText(
                    this,
                    "⚠️ لطفاً ابتدا سرویس را فعال کنید",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkServiceStatus() {
        if (MessageAutomationService.isServiceEnabled) {
            binding.statusText.text = "✅ سرویس فعال است"
            binding.statusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            binding.statusText.text = "⚠️ سرویس غیرفعال است"
            binding.statusText.setTextColor(getColor(android.R.color.holo_orange_dark))
        }
    }

    private fun showConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("فعال‌سازی ارسال خودکار")
            .setMessage(
                "برای فعال‌سازی ارسال خودکار پیام، باید دسترسی Accessibility را فعال کنید:\n\n" +
                "۱. روی «باز کردن تنظیمات» کلیک کنید\n" +
                "۲. «دستیار هوش مصنوعی» را پیدا کنید\n" +
                "۳. سوئیچ را روشن کنید\n" +
                "۴. روی «اجازه دادن» کلیک کنید\n\n" +
                "⚠️ توجه: این دسترسی فقط برای ارسال پیام‌های شما استفاده می‌شود"
            )
            .setPositiveButton("باز کردن تنظیمات") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                this,
                "خطا در باز کردن تنظیمات",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        checkServiceStatus()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
