package com.persianai.assistant.activities

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivitySplashScreenBinding

/**
 * صفحه Splash Screen با انیمیشن حرفه‌ای
 */
class SplashScreenActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySplashScreenBinding
    private val SPLASH_DURATION = 2500L // 2.5 ثانیه
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // مخفی کردن ActionBar
        supportActionBar?.hide()
        
        // شروع انیمیشن‌ها
        startAnimations()
        
        // انتقال به صفحه بعدی
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, SPLASH_DURATION)
    }
    
    private fun startAnimations() {
        // انیمیشن fade in برای لوگو
        binding.logoImage?.apply {
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(1000)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
        
        // انیمیشن scale برای لوگو
        binding.logoImage?.apply {
            scaleX = 0.5f
            scaleY = 0.5f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(1000)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
        
        // انیمیشن slide up برای متن
        binding.appNameText?.apply {
            alpha = 0f
            translationY = 50f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(800)
                .setStartDelay(500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
        
        // انیمیشن برای متن توضیحات
        binding.descriptionText?.apply {
            alpha = 0f
            translationY = 30f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(800)
                .setStartDelay(700)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
        
        // انیمیشن چرخش برای ProgressBar
        binding.loadingProgress?.apply {
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(500)
                .setStartDelay(1000)
                .start()
        }
    }
    
    private fun navigateToNextScreen() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        
        val intent = if (isFirstRun) {
            Intent(this, WelcomeActivity::class.java)
        } else {
            Intent(this, DashboardActivity::class.java)
        }
        
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
