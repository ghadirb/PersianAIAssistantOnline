package com.persianai.assistant.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * کلاس کمکی برای درخواست امتیاز از کاربر
 */
object AppRatingHelper {
    
    private const val PREF_NAME = "app_rating"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val KEY_DONT_SHOW_AGAIN = "dont_show_again"
    private const val KEY_RATED = "rated"
    
    private const val LAUNCHES_UNTIL_PROMPT = 5 // بعد از 5 بار اجرا
    private const val DAYS_UNTIL_PROMPT = 3 // بعد از 3 روز
    
    /**
     * بررسی و نمایش دیالوگ امتیازدهی
     */
    fun checkAndShowRatingDialog(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // اگر قبلاً امتیاز داده یا گفته "دیگر نشان نده"
        if (prefs.getBoolean(KEY_RATED, false) || prefs.getBoolean(KEY_DONT_SHOW_AGAIN, false)) {
            return
        }
        
        val editor = prefs.edit()
        
        // اولین اجرا
        val firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH, 0)
        if (firstLaunch == 0L) {
            editor.putLong(KEY_FIRST_LAUNCH, System.currentTimeMillis())
            editor.apply()
            return
        }
        
        // افزایش تعداد اجرا
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1
        editor.putInt(KEY_LAUNCH_COUNT, launchCount)
        editor.apply()
        
        // بررسی شرایط نمایش
        val daysSinceFirstLaunch = (System.currentTimeMillis() - firstLaunch) / (1000 * 60 * 60 * 24)
        
        if (launchCount >= LAUNCHES_UNTIL_PROMPT && daysSinceFirstLaunch >= DAYS_UNTIL_PROMPT) {
            showRatingDialog(context)
        }
    }
    
    /**
     * نمایش دیالوگ امتیازدهی
     */
    private fun showRatingDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle("⭐ آیا از برنامه راضی هستید؟")
            .setMessage("اگر از دستیار هوشمند فارسی راضی هستید، لطفاً با امتیاز دادن ما را حمایت کنید!")
            .setPositiveButton("امتیاز می‌دهم ⭐") { _, _ ->
                rateApp(context)
            }
            .setNegativeButton("بعداً") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("دیگر نشان نده") { _, _ ->
                dontShowAgain(context)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * باز کردن صفحه برنامه در Google Play
     */
    private fun rateApp(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_RATED, true).apply()
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            context.startActivity(intent)
        } catch (e: Exception) {
            // اگر Google Play نصب نبود، از مرورگر باز کن
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
            context.startActivity(intent)
        }
    }
    
    /**
     * علامت‌گذاری "دیگر نشان نده"
     */
    private fun dontShowAgain(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DONT_SHOW_AGAIN, true).apply()
    }
    
    /**
     * ریست کردن وضعیت امتیازدهی (برای تست)
     */
    fun resetRatingStatus(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
