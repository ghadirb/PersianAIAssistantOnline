package com.persianai.assistant.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.persianai.assistant.utils.SmartReminderManager

/**
 * Helper برای تست FullScreenAlarm
 * استفاده: ReminderTestHelper.testFullScreenAlarm(context)
 */
object ReminderTestHelper {
    
    private const val TAG = "ReminderTestHelper"
    
    /**
     * تست FullScreen Alarm بعد از ۲ ثانیه
     */
    fun testFullScreenAlarm(context: Context, delaySeconds: Int = 2) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                Log.d(TAG, "🧪 Testing Full-Screen Alarm (delay: ${delaySeconds}s)")
                
                val mgr = SmartReminderManager(context)
                val reminder = mgr.createSimpleReminder(
                    title = "🧪 تست هشدار تمام صفحه",
                    description = "اگر این پیام را می‌بینید، کار می‌کند!",
                    triggerTime = System.currentTimeMillis(),
                    alertType = SmartReminderManager.AlertType.FULL_SCREEN,
                    priority = SmartReminderManager.Priority.HIGH
                )
                
                Log.d(TAG, "✅ Reminder created: ${reminder.id}")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error testing", e)
            }
        }, delaySeconds * 1000L)
    }
    
    /**
     * تست Notification Alarm بعد از ۲ ثانیه
     */
    fun testNotificationAlarm(context: Context, delaySeconds: Int = 2) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                Log.d(TAG, "🧪 Testing Notification Alarm (delay: ${delaySeconds}s)")
                
                val mgr = SmartReminderManager(context)
                val reminder = mgr.createSimpleReminder(
                    title = "🧪 تست نوتیفیکیشن",
                    description = "این یک نوتیفیکیشن تست است",
                    triggerTime = System.currentTimeMillis(),
                    alertType = SmartReminderManager.AlertType.NOTIFICATION,
                    priority = SmartReminderManager.Priority.MEDIUM
                )
                
                Log.d(TAG, "✅ Notification reminder created: ${reminder.id}")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error testing", e)
            }
        }, delaySeconds * 1000L)
    }
    
    /**
     * تست هر دو
     */
    fun testBoth(context: Context) {
        testFullScreenAlarm(context, 2)
        Handler(Looper.getMainLooper()).postDelayed({
            testNotificationAlarm(context, 2)
        }, 5000)
    }
}