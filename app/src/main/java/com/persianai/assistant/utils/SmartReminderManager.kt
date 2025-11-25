package com.persianai.assistant.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.models.Reminder
import com.persianai.assistant.utils.ReminderScheduler
import java.util.Calendar

/**
 * مدیریت پیشرفته یادآوری‌های هوشمند
 * شامل: یادآوری‌های تکراری، مبتنی بر مکان، زمینه‌محور، و خانوادگی
 */
class SmartReminderManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("smart_reminders", Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val gson = Gson()
    
    companion object {
        private const val TAG = "SmartReminder"
        private const val KEY_REMINDERS = "reminders"
    }
    
    /**
     * نوع یادآوری
     */
    enum class ReminderType(val displayName: String) {
        SIMPLE("ساده"),
        RECURRING("تکراری"),
        LOCATION_BASED("مبتنی بر مکان"),
        BIRTHDAY("تولد"),
        ANNIVERSARY("سالگرد"),
        BILL_PAYMENT("پرداخت قبض"),
        MEDICINE("دارو"),
        FAMILY("خانوادگی"),
        SHOPPING("خرید"),
        TASK("کار روزانه")
    }
    
    /**
     * اولویت یادآوری
     */
    enum class Priority(val displayName: String, val color: String) {
        LOW("کم", "#4CAF50"),
        MEDIUM("متوسط", "#FF9800"),
        HIGH("زیاد", "#F44336"),
        URGENT("فوری", "#9C27B0")
    }
    
    /**
     * نوع هشدار یادآوری
     */
    enum class AlertType {
        NOTIFICATION,
        FULL_SCREEN
    }
    
    /**
     * الگوی تکرار
     */
    enum class RepeatPattern(val displayName: String) {
        ONCE("یکبار"),
        DAILY("روزانه"),
        WEEKLY("هفتگی"),
        MONTHLY("ماهانه"),
        YEARLY("سالانه"),
        WEEKDAYS("روزهای کاری"),
        WEEKENDS("آخر هفته"),
        CUSTOM("سفارشی")
    }
    
    /**
     * یادآوری هوشمند
     */
    data class SmartReminder(
        val id: String,
        val title: String,
        val description: String = "",
        val type: ReminderType,
        val priority: Priority = Priority.MEDIUM,
        val alertType: AlertType = AlertType.NOTIFICATION,
        val triggerTime: Long,
        val repeatPattern: RepeatPattern = RepeatPattern.ONCE,
        val customRepeatDays: List<Int> = emptyList(), // 1=Sunday, 7=Saturday
        val location: String? = null,
        val relatedPerson: String? = null,
        val notes: String = ""
    ) {
        fun toSimpleReminder(): Reminder {
            return Reminder(
                id = this.id,
                message = this.title,
                timestamp = this.triggerTime,
                isRepeating = this.repeatPattern != RepeatPattern.ONCE
            )
        }
    }
    
    /**
     * افزودن یادآوری
     */
    fun addReminder(reminder: SmartReminder): SmartReminder {
        val reminders = getAllReminders().toMutableList()
        reminders.add(reminder)
        saveReminders(reminders)
        
        // تنظیم آلارم با استفاده از ReminderScheduler
        val simpleReminder = reminder.toSimpleReminder()
        ReminderScheduler.scheduleReminder(context, simpleReminder)
        
        Log.i(TAG, "✅ یادآوری جدید: ${reminder.title} (${reminder.type.displayName})")
        
        return reminder
    }

    fun addReminderWithoutAlarm(reminder: SmartReminder): SmartReminder {
        val reminders = getAllReminders().toMutableList()
        reminders.add(reminder)
        saveReminders(reminders)
        
        Log.i(TAG, "✅ یادآوری جدید (بدون آلارم): ${reminder.title} (${reminder.type.displayName})")
        
        return reminder
    }
    
    /**
     * ایجاد یادآوری ساده
     */
    fun createSimpleReminder(
        title: String,
        description: String = "",
        triggerTime: Long,
        priority: Priority = Priority.MEDIUM,
        alertType: AlertType = AlertType.NOTIFICATION
    ): SmartReminder {
        val reminder = SmartReminder(
            id = System.currentTimeMillis().toString(),
            title = title,
            description = description,
            type = ReminderType.SIMPLE,
            priority = priority,
            alertType = alertType,
            triggerTime = triggerTime
        )
        return addReminder(reminder)
    }
    
    /**
     * ایجاد یادآوری تکراری
     */
    fun createRecurringReminder(
        title: String,
        description: String = "",
        firstTriggerTime: Long,
        repeatPattern: RepeatPattern,
        customDays: List<Int> = emptyList(),
        priority: Priority = Priority.MEDIUM
    ): SmartReminder {
        val reminder = SmartReminder(
            id = System.currentTimeMillis().toString(),
            title = title,
            description = description,
            type = ReminderType.RECURRING,
            priority = priority,
            triggerTime = firstTriggerTime,
            repeatPattern = repeatPattern,
            customRepeatDays = customDays
        )
        return addReminder(reminder)
    }
    
    /**
     * ایجاد یادآوری تولد
     */
    fun createBirthdayReminder(
        personName: String,
        birthdayDate: Long,
        notes: String = ""
    ): SmartReminder {
        val reminder = SmartReminder(
            id = "birthday_${System.currentTimeMillis()}",
            title = "🎂 تولد $personName",
            description = "امروز تولد $personName است!",
            type = ReminderType.BIRTHDAY,
            priority = Priority.HIGH,
            triggerTime = birthdayDate,
            repeatPattern = RepeatPattern.YEARLY,
            relatedPerson = personName,
            notes = notes
        )
        return addReminder(reminder)
    }
    
    /**
     * ایجاد یادآوری پرداخت قبض
     */
    fun createBillReminder(
        billName: String,
        dueDate: Long,
        amount: Long = 0,
        isRecurring: Boolean = false
    ): SmartReminder {
        val reminder = SmartReminder(
            id = "bill_${System.currentTimeMillis()}",
            title = "💰 پرداخت $billName",
            description = if (amount > 0) "مبلغ: ${String.format("%,d", amount)} تومان" else "",
            type = ReminderType.BILL_PAYMENT,
            priority = Priority.HIGH,
            triggerTime = dueDate,
            repeatPattern = if (isRecurring) RepeatPattern.MONTHLY else RepeatPattern.ONCE
        )
        return addReminder(reminder)
    }
    
    /**
     * ایجاد یادآوری دارو
     */
    fun createMedicineReminder(
        medicineName: String,
        times: List<Pair<Int, Int>>, // (hour, minute)
        notes: String = ""
    ): List<SmartReminder> {
        val reminders = mutableListOf<SmartReminder>()
        
        times.forEach { (hour, minute) ->
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            
            // اگر زمان گذشته، برای فردا تنظیم کن
            if (calendar.timeInMillis < System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            val reminder = SmartReminder(
                id = "medicine_${System.currentTimeMillis()}_$hour$minute",
                title = "💊 مصرف دارو: $medicineName",
                description = "ساعت $hour:${String.format("%02d", minute)}",
                type = ReminderType.MEDICINE,
                priority = Priority.URGENT,
                triggerTime = calendar.timeInMillis,
                repeatPattern = RepeatPattern.DAILY,
                notes = notes
            )
            reminders.add(addReminder(reminder))
        }
        
        return reminders
    }
    
    /**
     * ایجاد یادآوری مبتنی بر مکان
     */
    fun createLocationReminder(
        title: String,
        description: String,
        lat: Double,
        lng: Double,
        radius: Int = 100,
        locationName: String
    ): SmartReminder {
        val reminder = SmartReminder(
            id = "location_${System.currentTimeMillis()}",
            title = title,
            description = description,
            type = ReminderType.LOCATION_BASED,
            priority = Priority.MEDIUM,
            triggerTime = System.currentTimeMillis(),
            locationLat = lat,
            locationLng = lng,
            locationRadius = radius,
            locationName = locationName
        )
        return addReminderWithoutAlarm(reminder)
    }
    
    /**
     * دریافت تمام یادآوری‌ها
     */
    fun getAllReminders(): List<SmartReminder> {
        val json = prefs.getString(KEY_REMINDERS, "[]") ?: "[]"
        val type = object : TypeToken<List<SmartReminder>>() {}.type
        return gson.fromJson(json, type)
    }
    
    /**
     * دریافت یادآوری‌های فعال
     */
    fun getActiveReminders(): List<SmartReminder> {
        return getAllReminders().filter { !it.isCompleted }
    }
    
    /**
     * دریافت یادآوری‌های امروز
     */
    fun getTodayReminders(): List<SmartReminder> {
        val now = Calendar.getInstance()
        val startOfDay = now.clone() as Calendar
        startOfDay.set(Calendar.HOUR_OF_DAY, 0)
        startOfDay.set(Calendar.MINUTE, 0)
        startOfDay.set(Calendar.SECOND, 0)
        
        val endOfDay = now.clone() as Calendar
        endOfDay.set(Calendar.HOUR_OF_DAY, 23)
        endOfDay.set(Calendar.MINUTE, 59)
        endOfDay.set(Calendar.SECOND, 59)
        
        return getActiveReminders()
            .filter { it.triggerTime in startOfDay.timeInMillis..endOfDay.timeInMillis }
            .sortedBy { it.triggerTime }
    }
    
    /**
     * دریافت یادآوری‌های سررسید گذشته
     */
    fun getOverdueReminders(): List<SmartReminder> {
        val now = System.currentTimeMillis()
        return getActiveReminders()
            .filter { it.triggerTime < now && it.repeatPattern == RepeatPattern.ONCE }
            .sortedBy { it.triggerTime }
    }
    
    /**
     * دریافت یادآوری‌های آینده
     */
    fun getUpcomingReminders(days: Int = 7): List<SmartReminder> {
        val now = System.currentTimeMillis()
        val future = now + (days * 24 * 60 * 60 * 1000)
        
        return getActiveReminders()
            .filter { it.triggerTime in now..future }
            .sortedBy { it.triggerTime }
    }
    
    /**
     * علامت‌زدن یادآوری به عنوان انجام شده
     */
    fun completeReminder(reminderId: String): Boolean {
        val reminders = getAllReminders().toMutableList()
        val index = reminders.indexOfFirst { it.id == reminderId }
        
        if (index != -1) {
            val reminder = reminders[index]
            
            // اگر تکراری نیست، علامت بزن
            if (reminder.repeatPattern == RepeatPattern.ONCE) {
                reminders[index] = reminder.copy(
                    isCompleted = true,
                    completedAt = System.currentTimeMillis()
                )
            } else {
                // برای تکراری، زمان بعدی را محاسبه کن
                val nextTime = calculateNextTriggerTime(reminder)
                reminders[index] = reminder.copy(triggerTime = nextTime)
                val simpleReminder = reminder.toSimpleReminder()
                ReminderScheduler.cancelReminder(context, simpleReminder)
                simpleReminder.timestamp = nextTime
                ReminderScheduler.scheduleReminder(context, simpleReminder)
            }
            
            saveReminders(reminders)
            Log.i(TAG, "✅ یادآوری انجام شد: ${reminder.title}")
            return true
        }
        
        return false
    }
    
    /**
     * به تعویق انداختن یادآوری (Snooze)
     */
    fun snoozeReminder(reminderId: String, minutes: Int = 10): Boolean {
        val reminders = getAllReminders().toMutableList()
        val index = reminders.indexOfFirst { it.id == reminderId }
        
        if (index != -1) {
            val reminder = reminders[index]
            val newTime = System.currentTimeMillis() + (minutes * 60 * 1000)
            
            reminders[index] = reminder.copy(
                triggerTime = newTime,
                snoozeCount = reminder.snoozeCount + 1,
                lastSnoozed = System.currentTimeMillis()
            )
            
            saveReminders(reminders)
            val simpleReminder = reminder.toSimpleReminder()
            ReminderScheduler.cancelReminder(context, simpleReminder)
            simpleReminder.timestamp = newTime
            ReminderScheduler.scheduleReminder(context, simpleReminder)
            
            Log.i(TAG, "⏰ یادآوری به تعویق افتاد: ${reminder.title} ($minutes دقیقه)")
            return true
        }
        
        return false
    }
    
    /**
     * بروزرسانی یادآوری
     */
    fun updateReminder(updatedReminder: SmartReminder): Boolean {
        val reminders = getAllReminders().toMutableList()
        val index = reminders.indexOfFirst { it.id == updatedReminder.id }

        if (index != -1) {
            val oldReminder = reminders[index]
            reminders[index] = updatedReminder
            saveReminders(reminders)

            // بروزرسانی آلارم
            val oldSimpleReminder = oldReminder.toSimpleReminder()
            ReminderScheduler.cancelReminder(context, oldSimpleReminder)
            val newSimpleReminder = updatedReminder.toSimpleReminder()
            ReminderScheduler.scheduleReminder(context, newSimpleReminder)

            Log.i(TAG, "🔄 یادآوری بروزرسانی شد: ${updatedReminder.title}")
            return true
        }
        return false
    }

    /**
     * حذف یادآوری
     */
    fun deleteReminder(reminderId: String): Boolean {
        val reminders = getAllReminders().toMutableList()
        val reminder = reminders.firstOrNull { it.id == reminderId }
        if (reminder != null) {
            reminders.remove(reminder)
            saveReminders(reminders)

            // لغو آلارم
            val simpleReminder = reminder.toSimpleReminder()
            ReminderScheduler.cancelReminder(context, simpleReminder)

            Log.i(TAG, "🗑️ یادآوری حذف شد: ${reminder.title}")
            return true
        }
        return false
    }
    
    /**
     * محاسبه زمان trigger بعدی برای یادآوری‌های تکراری
     */
    private fun calculateNextTriggerTime(reminder: SmartReminder): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = reminder.triggerTime
        
        when (reminder.repeatPattern) {
            RepeatPattern.DAILY -> calendar.add(Calendar.DAY_OF_MONTH, 1)
            RepeatPattern.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            RepeatPattern.MONTHLY -> calendar.add(Calendar.MONTH, 1)
            RepeatPattern.YEARLY -> calendar.add(Calendar.YEAR, 1)
            RepeatPattern.WEEKDAYS -> {
                do {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                } while (calendar.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.FRIDAY))
            }
            RepeatPattern.WEEKENDS -> {
                do {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                } while (calendar.get(Calendar.DAY_OF_WEEK) !in listOf(Calendar.SATURDAY, Calendar.FRIDAY))
            }
            RepeatPattern.CUSTOM -> {
                if (reminder.customRepeatDays.isNotEmpty()) {
                    do {
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    } while (calendar.get(Calendar.DAY_OF_WEEK) !in reminder.customRepeatDays)
                }
            }
            else -> return reminder.triggerTime
        }
        
        return calendar.timeInMillis
    }
    
    /**
        )
        
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "❌ آلارم لغو شد")
    }
    
    /**
     * ذخیره یادآوری‌ها
     */
    private fun saveReminders(reminders: List<SmartReminder>) {
        val json = gson.toJson(reminders)
        prefs.edit().putString(KEY_REMINDERS, json).apply()
    }
    
    /**
     * دریافت آمار یادآوری‌ها
     */
    fun getReminderStats(): ReminderStats {
        val all = getAllReminders()
        val active = getActiveReminders()
        val completed = all.filter { it.isCompleted }
        val today = getTodayReminders()
        val overdue = getOverdueReminders()
        
        return ReminderStats(
            totalReminders = all.size,
            activeReminders = active.size,
            completedReminders = completed.size,
            todayReminders = today.size,
            overdueReminders = overdue.size,
            completionRate = if (all.isNotEmpty()) (completed.size.toFloat() / all.size * 100).toInt() else 0
        )
    }
    
    data class ReminderStats(
        val totalReminders: Int,
        val activeReminders: Int,
        val completedReminders: Int,
        val todayReminders: Int,
        val overdueReminders: Int,
        val completionRate: Int
    )
}
