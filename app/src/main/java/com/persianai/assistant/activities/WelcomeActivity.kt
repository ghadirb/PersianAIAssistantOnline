package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityWelcomeBinding
import com.persianai.assistant.utils.PreferencesManager

/**
 * صفحه خوش‌آمدگویی و انتخاب حالت کار
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsManager = PreferencesManager(this)

        // اگر قبلاً انتخاب شده، بپر به MainActivity
        if (prefsManager.hasCompletedWelcome()) {
            goToMain()
            return
        }

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // دکمه حالت آنلاین
        binding.onlineModeButton.setOnClickListener {
            prefsManager.setWorkingMode(PreferencesManager.WorkingMode.ONLINE)
            prefsManager.setWelcomeCompleted(true)
            goToMain()
        }

        // دکمه حالت آفلاین
        binding.offlineModeButton.setOnClickListener {
            prefsManager.setWorkingMode(PreferencesManager.WorkingMode.OFFLINE)
            prefsManager.setWelcomeCompleted(true)
            
            // نمایش راهنما برای دانلود مدل آفلاین
            showOfflineGuide()
        }

        // دکمه حالت ترکیبی
        binding.hybridModeButton.setOnClickListener {
            prefsManager.setWorkingMode(PreferencesManager.WorkingMode.HYBRID)
            prefsManager.setWelcomeCompleted(true)
            goToMain()
        }
    }

    private fun showOfflineGuide() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("حالت آفلاین")
            .setMessage(
                "در حالت آفلاین:\n\n" +
                "✅ نیازی به اینترنت نیست\n" +
                "✅ حریم خصوصی کامل\n" +
                "⚠️ پاسخ‌ها ساده‌تر هستند\n" +
                "⚠️ نیاز به دانلود مدل (≈100MB)\n\n" +
                "می‌توانید بعداً در تنظیمات مدل آفلاین دانلود کنید."
            )
            .setPositiveButton("ادامه") { _, _ ->
                goToMain()
            }
            .setNegativeButton("انصراف") { _, _ ->
                prefsManager.setWelcomeCompleted(false)
            }
            .show()
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // غیرفعال کردن دکمه بازگشت
    }
}
