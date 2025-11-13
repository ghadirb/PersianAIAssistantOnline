package com.persianai.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log
import java.util.*

/**
 * مدیر هوشمند یادآورهای فارسی روزانه
 */
class SmartReminderManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("smart_reminders", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val REMINDERS_KEY = "reminders"
        private const val NOTIFICATION_HELPER = "NotificationHelper"
    }
    
    @Serializable
    data class SmartReminder(
        val id: String,
        val title: String,
        val message: String,
        val time: String, // HH:mm format
        val days: List<String>, // روزهای هفته
        val category: ReminderCategory,
        val priority: ReminderPriority,
        val isActive: Boolean = true,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    @Serializable
    enum class ReminderCategory {
        HEALTH, // سلامتی
        WORK, // کاری
        PERSONAL, // شخصی
        FAMILY, // خانوادگی
        FINANCIAL, // مالی
        EDUCATION, // آموزشی
        SPIRITUAL // معنوی
    }
    
    @Serializable
    enum class ReminderPriority {
        LOW, // پایین
        MEDIUM, // متوسط
        HIGH, // بالا
        URGENT // فوری
    }
    
    /**
     * افزودن یادآور جدید
     */
    fun addReminder(reminder: SmartReminder) {
        try {
            val reminders = getReminders().toMutableList()
            reminders.add(reminder)
            saveReminders(reminders)
            
            Log.i("SmartReminderManager", "✅ یادآور جدید اضافه شد: ${reminder.title}")
            
            // شروع بررسی دوره‌ای
            startPeriodicCheck()
            
        } catch (e: Exception) {
            Log.e("SmartReminderManager", "❌ خطا در افزودن یادآور: ${e.message}")
        }
    }
    
    /**
     * دریافت تمام یادآورها
     */
    fun getReminders(): List<SmartReminder> {
        return try {
            val remindersJson = prefs.getString(REMINDERS_KEY, null)
            if (remindersJson != null) {
                json.decodeFromString<List<SmartReminder>>(remindersJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SmartReminderManager", "❌ خطا در دریافت یادآورها: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * دریافت یادآورهای فعال
     */
    fun getActiveReminders(): List<SmartReminder> {
        return getReminders().filter { it.isActive }
    }
    
    /**
     * دریافت یادآورهای بر اساس دسته‌بندی
     */
    fun getRemindersByCategory(category: ReminderCategory): List<SmartReminder> {
        return getReminders().filter { it.category == category }
    }
    
    /**
     * ویرایش یادآور
     */
    fun updateReminder(reminder: SmartReminder) {
        try {
            val reminders = getReminders().toMutableList()
            val index = reminders.indexOfFirst { it.id == reminder.id }
            if (index != -1) {
                reminders[index] = reminder
                saveReminders(reminders)
                Log.i("SmartReminderManager", "✅ یادآور ویرایش شد: ${reminder.title}")
            }
        } catch (e: Exception) {
            Log.e("SmartReminderManager", "❌ خطا در ویرایش یادآور: ${e.message}")
        }
    }
    
    /**
     * حذف یادآور
     */
    fun deleteReminder(reminderId: String) {
        try {
            val reminders = getReminders().toMutableList()
            reminders.removeAll { it.id == reminderId }
            saveReminders(reminders)
            Log.i("SmartReminderManager", "✅ یادآور حذف شد: $reminderId")
        } catch (e: Exception) {
            Log.e("SmartReminderManager", "❌ خطا در حذف یادآور: ${e.message}")
        }
    }
    
    /**
     * فعال/غیرفعال کردن یادآور
     */
    fun toggleReminder(reminderId: String, isActive: Boolean) {
        try {
            val reminders = getReminders().toMutableList()
            val index = reminders.indexOfFirst { it.id == reminderId }
            if (index != -1) {
                reminders[index] = reminders[index].copy(isActive = isActive)
                saveReminders(reminders)
                Log.i("SmartReminderManager", "✅ وضعیت یادآور تغییر کرد: $reminderId -> $isActive")
            }
        } catch (e: Exception) {
            Log.e("SmartReminderManager", "❌ خطا در تغییر وضعیت یادآور: ${e.message}")
        }
    }
    
    /**
     * بررسی یادآورهای فعلی
     */
    private fun checkReminders() {
        try {
            val now = Calendar.getInstance()
            val currentTime = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            val currentDay = getDayOfWeek(now.get(Calendar.DAY_OF_WEEK))
            
            val activeReminders = getActiveReminders()
            
            activeReminders.forEach { reminder ->
                if (reminder.time == currentTime && reminder.days.contains(currentDay)) {
                    sendNotification(reminder)
                }
            }
            
        } catch (e: Exception) {
            Log.e("SmartReminderManager", "❌ خطا در بررسی یادآورها: ${e.message}")
        }
    }
    
    /**
     * ارسال نوتیفیکیشن یادآور
     */
    private fun sendNotification(reminder: SmartReminder) {
        try {
            // استفاده از NotificationHelper برای ارسال نوتیفیکیشن
            val notificationHelper = NotificationHelper(context)
            scope.launch {
                notificationHelper.showNotification(
                    title = "🔔 یادآور هوشمند: ${reminder.title}",
                    message = reminder.message,
                    channelId = "smart_reminders"
                )
            }
            
            Log.i("SmartReminderManager", "✅ نوتیفیکیشن یادآور ارسال شد: ${reminder.title}")
            
        } catch (e: Exception) {
            Log.e("SmartReminderManager", "❌ خطا در ارسال نوتیفیکیشن: ${e.message}")
        }
    }
    
    /**
     * شروع بررسی دوره‌ای
     */
    private fun startPeriodicCheck() {
        scope.launch {
            while (isActive) {
                checkReminders()
                delay(60000) // بررسی هر دقیقه
            }
        }
    }
    
    /**
     * دریافت نام روز هفته
     */
    private fun getDayOfWeek(day: Int): String {
        return when (day) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یکشنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنجشنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> "نامشخص"
        }
    }
    
    /**
     * ذخیره یادآورها
     */
    private fun saveReminders(reminders: List<SmartReminder>) {
        try {
            val remindersJson = json.encodeToString(reminders)
            prefs.edit()
                .putString(REMINDERS_KEY, remindersJson)
                .apply()
        } catch (e: Exception) {
            Log.e("SmartReminderManager", "❌ خطا در ذخیره یادآورها: ${e.message}")
        }
    }
    
    /**
     * ایجاد یادآورهای پیش‌فرض
     */
    fun createDefaultReminders() {
        val defaultReminders = listOf(
            SmartReminder(
                id = "morning_prayer",
                title = "اذکار صبحگاهی",
                message = "وقت اذکار صبحگاهی فرا رسیده است",
                time = "06:00",
                days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"),
                category = ReminderCategory.SPIRITUAL,
                priority = ReminderPriority.HIGH
            ),
            SmartReminder(
                id = "morning_exercise",
                title = "ورزش صبحگاهی",
                message = "ورزش صبحگاهی برای شروع یک روز پرانرژی",
                time = "07:00",
                days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه"),
                category = ReminderCategory.HEALTH,
                priority = ReminderPriority.MEDIUM
            ),
            SmartReminder(
                id = "work_start",
                title = "شروع کار",
                message = "زمان شروع فعالیت‌های کاری",
                time = "09:00",
                days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه"),
                category = ReminderCategory.WORK,
                priority = ReminderPriority.HIGH
            ),
            SmartReminder(
                id = "lunch_break",
                title = "استراحت ناهار",
                message = "زمان استراحت و ناهار",
                time = "13:00",
                days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه"),
                category = ReminderCategory.HEALTH,
                priority = ReminderPriority.MEDIUM
            ),
            SmartReminder(
                id = "evening_prayer",
                title = "اذکار شامگاهی",
                message = "وقت اذکار شامگاهی فرا رسیده است",
                time = "19:00",
                days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"),
                category = ReminderCategory.SPIRITUAL,
                priority = ReminderPriority.HIGH
            ),
            SmartReminder(
                id = "family_time",
                title = "زمان خانواده",
                message = "وقت گذراندن با خانواده",
                time = "20:00",
                days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه"),
                category = ReminderCategory.FAMILY,
                priority = ReminderPriority.HIGH
            ),
            SmartReminder(
                id = "sleep_time",
                title = "زمان خواب",
                message = "زمان استراحت و خواب برای فردایی پرانرژی",
                time = "23:00",
                days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه"),
                category = ReminderCategory.HEALTH,
                priority = ReminderPriority.MEDIUM
            )
        )
        
        defaultReminders.forEach { addReminder(it) }
        Log.i("SmartReminderManager", "✅ یادآورهای پیش‌فرض ایجاد شد")
    }
    
    /**
     * پاک‌سازی منابع
     */
    fun cleanup() {
        scope.cancel()
        Log.i("SmartReminderManager", "🧹 منابع SmartReminderManager پاک‌سازی شد")
    }
}
