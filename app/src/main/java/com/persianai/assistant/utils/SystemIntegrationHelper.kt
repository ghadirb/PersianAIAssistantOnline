package com.persianai.assistant.utils

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * کمک به یکپارچه‌سازی با سیستم و برنامه‌های دیگر
 */
object SystemIntegrationHelper {

    /**
     * تنظیم یادآوری/هشدار
     */
    fun setReminder(context: Context, message: String, hour: Int, minute: Int): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * باز کردن مسیریابی در Google Maps
     */
    fun openNavigation(context: Context, destination: String): Boolean {
        return try {
            val gmmIntentUri = Uri.parse("google.navigation:q=$destination")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(mapIntent)
            true
        } catch (e: Exception) {
            // اگر Google Maps نصب نبود، از browser استفاده کن
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destination")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * جستجو در Google
     */
    fun searchWeb(context: Context, query: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * تماس تلفنی
     */
    fun makePhoneCall(context: Context, phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ارسال پیامک
     */
    fun sendSMS(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ارسال ایمیل
     */
    fun sendEmail(context: Context, to: String, subject: String, body: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "ارسال ایمیل"))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * باز کردن یک URL
     */
    fun openUrl(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * اضافه کردن رویداد به تقویم
     */
    fun addCalendarEvent(
        context: Context,
        title: String,
        description: String,
        beginTime: Long,
        endTime: Long
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * پخش موسیقی
     */
    fun playMusic(context: Context, query: String): Boolean {
        return try {
            val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
                putExtra(android.app.SearchManager.QUERY, query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * تشخیص نوع درخواست و اجرای عمل مناسب
     */
    fun handleSmartRequest(context: Context, request: String): String {
        val lowerRequest = request.lowercase()

        return when {
            // یادآوری
            lowerRequest.contains("یادآوری") || lowerRequest.contains("یادآور") -> {
                "برای تنظیم یادآوری، لطفاً از دستور زیر استفاده کنید:\n'یادآوری ساعت [ساعت]:[دقیقه] - [پیام]'\n\nمثال: یادآوری ساعت 8:30 - جلسه کاری"
            }

            // مسیریابی
            lowerRequest.contains("مسیر") || lowerRequest.contains("راهنما") || lowerRequest.contains("navigation") -> {
                extractAndNavigate(context, request)
            }

            // تماس
            lowerRequest.contains("تماس") || lowerRequest.contains("زنگ بزن") -> {
                extractAndCall(context, request)
            }

            // پیامک
            lowerRequest.contains("پیامک") || lowerRequest.contains("sms") -> {
                extractAndSMS(context, request)
            }

            // جستجو
            lowerRequest.contains("جستجو") || lowerRequest.contains("search") -> {
                extractAndSearch(context, request)
            }

            // تقویم
            lowerRequest.contains("تقویم") || lowerRequest.contains("رویداد") -> {
                "برای اضافه کردن رویداد، لطفاً از دستور زیر استفاده کنید:\n'تقویم [عنوان] - [تاریخ] [ساعت]'"
            }

            else -> {
                "می‌توانم به شما در موارد زیر کمک کنم:\n" +
                        "• تنظیم یادآوری\n" +
                        "• مسیریابی و راهنمای مسیر\n" +
                        "• تماس تلفنی\n" +
                        "• ارسال پیامک\n" +
                        "• جستجوی وب\n" +
                        "• اضافه کردن رویداد به تقویم\n\n" +
                        "مثال: 'مسیریابی به تهران' یا 'یادآوری ساعت 8:00 - جلسه کاری'"
            }
        }
    }

    private fun extractAndNavigate(context: Context, request: String): String {
        val destination = request
            .replace(Regex("مسیر(یابی)?\\s*(به)?"), "", RegexOption.IGNORE_CASE)
            .replace(Regex("راهنما(ی)?\\s*مسیر"), "", RegexOption.IGNORE_CASE)
            .trim()

        return if (destination.isNotEmpty() && openNavigation(context, destination)) {
            "در حال باز کردن مسیریابی به $destination در Google Maps..."
        } else {
            "لطفاً مقصد را مشخص کنید. مثال: 'مسیریابی به تهران'"
        }
    }

    private fun extractAndCall(context: Context, request: String): String {
        val phoneRegex = Regex("\\d{10,11}")
        val phoneMatch = phoneRegex.find(request)

        return if (phoneMatch != null && makePhoneCall(context, phoneMatch.value)) {
            "در حال باز کردن شماره‌گیری..."
        } else {
            "لطفاً شماره تلفن را مشخص کنید. مثال: 'تماس با 09121234567'"
        }
    }

    private fun extractAndSMS(context: Context, request: String): String {
        val phoneRegex = Regex("\\d{10,11}")
        val phoneMatch = phoneRegex.find(request)

        return if (phoneMatch != null && sendSMS(context, phoneMatch.value, "")) {
            "در حال باز کردن پیامک..."
        } else {
            "لطفاً شماره تلفن را مشخص کنید. مثال: 'پیامک به 09121234567'"
        }
    }

    private fun extractAndSearch(context: Context, request: String): String {
        val query = request
            .replace(Regex("جستجو(ی)?"), "", RegexOption.IGNORE_CASE)
            .replace("search", "", RegexOption.IGNORE_CASE)
            .trim()

        return if (query.isNotEmpty() && searchWeb(context, query)) {
            "در حال جستجو: $query"
        } else {
            "لطفاً موضوع جستجو را مشخص کنید. مثال: 'جستجوی هوش مصنوعی'"
        }
    }
}
