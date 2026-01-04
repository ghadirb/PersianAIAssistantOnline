package com.persianai.assistant.ai

import android.content.Context
import org.json.JSONObject

/**
 * آفلاین غیرفعال شده؛ فقط پیغام ثابت برمی‌گردد.
 */
class OfflineIntentParser(private val context: Context) {

            "ایتا" to "ir.eitaa.messenger",
            "شاد" to "ir.shad.android",
            
            // برنامه‌های بین‌المللی
            "تلگرام" to "org.telegram.messenger",
            "واتساپ" to "com.whatsapp",
            "اینستاگرام" to "com.instagram.android",
            "توییتر" to "com.twitter.android",
            "یوتیوب" to "com.google.android.youtube",
            "جیمیل" to "com.google.android.gm",
            "کروم" to "com.android.chrome",
            "گوگل مپ" to "com.google.android.apps.maps",
            "گوگل" to "com.google.android.googlequicksearchbox",
            "موزیک" to "com.google.android.music",
            "گالری" to "com.google.android.apps.photos",
            "دوربین" to "com.android.camera2",
            "تنظیمات" to "com.android.settings",
            "تقویم" to "com.android.calendar",
            "ساعت" to "com.android.deskclock",
            "مخاطبین" to "com.android.contacts",
            "پیام" to "com.android.mms",
            "تلفن" to "com.android.dialer"
        )
        
        // کلمات کلیدی برای تشخیص action
        private val OPEN_KEYWORDS = listOf("باز کن", "بازکن", "برو", "اجرا کن", "شروع کن")
        private val REMINDER_KEYWORDS = listOf("یادآور", "یادآوری", "یادم بنداز", "یاداوری")
        private val TIMER_KEYWORDS = listOf("تایمر", "زمان‌سنج", "ساعت شمار")
        private val ALARM_KEYWORDS = listOf("آلارم", "زنگ", "هشدار")
    }

    /**
     * تحلیل متن و استخراج intent
     */
    fun parse(text: String): String {
        val normalizedText = text.trim()
        
        // چک کردن باز کردن برنامه
        val openAppResult = parseOpenApp(normalizedText)
        if (openAppResult != null) return openAppResult
        
        // چک کردن یادآوری
        val reminderResult = parseReminder(normalizedText)
        if (reminderResult != null) return reminderResult
        
        // چک کردن تایمر
        val timerResult = parseTimer(normalizedText)
        if (timerResult != null) return timerResult
        
        // چک کردن آلارم
        val alarmResult = parseAlarm(normalizedText)
        if (alarmResult != null) return alarmResult
        
        // پاسخ پیش‌فرض
        return createSimpleResponse()
    }

    private fun parseOpenApp(text: String): String? {
        val lowerText = text.lowercase()
        
        // چک کردن کلمات کلیدی "باز کن"
        val hasOpenKeyword = OPEN_KEYWORDS.any { lowerText.contains(it) }
        
        if (hasOpenKeyword || lowerText.endsWith("باز کن")) {
            // پیدا کردن نام برنامه
            for ((appName, packageName) in APP_MAPPINGS) {
                if (lowerText.contains(appName.lowercase())) {
                    // چک کردن نصب بودن برنامه
                    if (isAppInstalled(packageName)) {
                        return JSONObject().apply {
                            put("action", "open_app")
                            put("app_name", appName)
                            put("package_name", packageName)
                        }.toString()
                    } else {
                        return JSONObject().apply {
                            put("action", "response")
                            put("message", "برنامه $appName نصب نیست.")
                        }.toString()
                    }
                }
            }
        }
        
        return null
    }

    private fun parseReminder(text: String): String? {
        val lowerText = text.lowercase()
        
        if (REMINDER_KEYWORDS.any { lowerText.contains(it) }) {
            // استخراج متن یادآوری
            val reminderText = extractReminderText(text)
            
            return JSONObject().apply {
                put("action", "reminder")
                put("message", reminderText)
                put("time", "1 ساعت دیگر") // پیش‌فرض
            }.toString()
        }
        
        return null
    }

    private fun parseTimer(text: String): String? {
        val lowerText = text.lowercase()
        
        if (TIMER_KEYWORDS.any { lowerText.contains(it) }) {
            // استخراج زمان
            val duration = extractDuration(text)
            
            return JSONObject().apply {
                put("action", "timer")
                put("duration", duration)
            }.toString()
        }
        
        return null
    }

    private fun parseAlarm(text: String): String? {
        val lowerText = text.lowercase()
        
        if (ALARM_KEYWORDS.any { lowerText.contains(it) }) {
            // استخراج زمان
            val time = extractTime(text)
            
            return JSONObject().apply {
                put("action", "alarm")
                put("time", time)
            }.toString()
        }
        
        return null
    }

    private fun createSimpleResponse(): String {
        return JSONObject().apply {
            put("action", "response")
            put("message", "در حالت آفلاین، فقط دستورات ساده پشتیبانی می‌شود:\n\n" +
                    "✅ باز کردن برنامه‌ها\n" +
                    "✅ یادآوری\n" +
                    "✅ تایمر و آلارم\n\n" +
                    "اگر دقیق‌تر بگی، سعی می‌کنم همینجا راهنمایی‌ات کنم.")
        }.toString()
    }

    private fun extractReminderText(text: String): String {
        // حذف کلمات کلیدی و استخراج متن اصلی
        var cleanText = text
        REMINDER_KEYWORDS.forEach { keyword ->
            cleanText = cleanText.replace(keyword, "", ignoreCase = true)
        }
        return cleanText.trim()
    }

    private fun extractDuration(text: String): String {
        // استخراج اعداد و واحد زمان
        val regex = Regex("""(\d+)\s*(دقیقه|ساعت|ثانیه)""")
        val match = regex.find(text)
        
        return if (match != null) {
            "${match.groupValues[1]} ${match.groupValues[2]}"
        } else {
            "5 دقیقه" // پیش‌فرض
        }
    }

    private fun extractTime(text: String): String {
        // استخراج زمان (مثل "7 صبح" یا "18:30")
        val regex = Regex("""(\d+):?(\d*)\s*(صبح|عصر|شب)?""")
        val match = regex.find(text)
        
        return if (match != null) {
            val hour = match.groupValues[1]
            val minute = match.groupValues[2].ifEmpty { "00" }
            "$hour:$minute"
        } else {
            "08:00" // پیش‌فرض
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * بررسی اینکه آیا این parser میتونه این متن رو مدیریت کنه
     */
    fun canHandle(text: String): Boolean {
        val lowerText = text.lowercase()
        
        return OPEN_KEYWORDS.any { lowerText.contains(it) } ||
               REMINDER_KEYWORDS.any { lowerText.contains(it) } ||
               TIMER_KEYWORDS.any { lowerText.contains(it) } ||
               ALARM_KEYWORDS.any { lowerText.contains(it) } ||
               APP_MAPPINGS.keys.any { lowerText.contains(it.lowercase()) }
    }
}
