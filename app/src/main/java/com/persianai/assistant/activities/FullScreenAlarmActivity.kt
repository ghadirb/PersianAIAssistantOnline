package com.persianai.assistant.activities

import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.R
import com.persianai.assistant.utils.SmartReminderManager

/**
 * فعالیت تمام‌صفحه برای نمایش هشدار یادآوری
 */
class FullScreenAlarmActivity : AppCompatActivity() {
    
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var smartReminderId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_alarm)
        
        // تنظیمات صفحه
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        // دریافت داده‌ها
        val title = intent.getStringExtra("title") ?: "یادآوری"
        val description = intent.getStringExtra("description") ?: ""
        smartReminderId = intent.getStringExtra("smart_reminder_id")
        
        // تنظیم UI
        findViewById<TextView>(R.id.alarm_title).text = title
        findViewById<TextView>(R.id.alarm_description).text = description
        
        // دکمه‌ها
        findViewById<Button>(R.id.btn_dismiss).setOnClickListener {
            markAsDone()
            finish()
        }
        
        findViewById<Button>(R.id.btn_snooze).setOnClickListener {
            snoozeReminder()
            finish()
        }
        
        // شروع صدا و لرزش
        startAlarmSound()
        startVibration()
    }
    
    private fun startAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@FullScreenAlarmActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startVibration() {
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
        }
    }
    
    private fun markAsDone() {
        if (smartReminderId != null) {
            try {
                val mgr = SmartReminderManager(this)
                val reminder = mgr.getAllReminders().find { it.id == smartReminderId }
                if (reminder != null) {
                    mgr.updateReminder(reminder.copy(isCompleted = true, completedAt = System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun snoozeReminder() {
        if (smartReminderId != null) {
            try {
                val mgr = SmartReminderManager(this)
                val reminder = mgr.getAllReminders().find { it.id == smartReminderId }
                if (reminder != null) {
                    // 5 دقیقه بعد دوباره یادآوری کن
                    val newTriggerTime = System.currentTimeMillis() + (5 * 60 * 1000)
                    mgr.updateReminder(reminder.copy(triggerTime = newTriggerTime))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        vibrator?.cancel()
    }
}
