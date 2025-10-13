package com.persianai.assistant.activities

import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityAlarmBinding
import java.util.*

/**
 * صفحه نمایش آلارم تمام صفحه
 */
class AlarmActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAlarmBinding
    private var ringtone: Ringtone? = null
    private var message: String = ""
    private var reminderId: Int = 0
    
    companion object {
        private const val REQUEST_VOICE_RECOGNITION = 2001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // نمایش روی صفحه قفل
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        message = intent.getStringExtra("message") ?: "یادآوری"
        reminderId = intent.getIntExtra("reminder_id", 0)
        
        binding.messageText.text = message
        
        // پخش صدای آلارم
        playAlarmSound()
        
        setupListeners()
    }
    
    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            ringtone = RingtoneManager.getRingtone(this, alarmUri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupListeners() {
        // دکمه "انجام شد"
        binding.doneButton.setOnClickListener {
            markAsDone()
            finish()
        }
        
        // دکمه "بعداً یادآوری کن" (5 دقیقه بعد)
        binding.snoozeButton.setOnClickListener {
            snoozeReminder(5)
            finish()
        }
        
        // دکمه "با صدا انجام دهید"
        binding.voiceButton.setOnClickListener {
            startVoiceRecognition()
        }
        
        // دکمه بستن
        binding.dismissButton.setOnClickListener {
            finish()
        }
    }
    
    private fun markAsDone() {
        stopAlarmSound()
        
        // علامت‌گذاری در لیست یادآوری‌ها
        val prefs = getSharedPreferences("reminders", MODE_PRIVATE)
        val count = prefs.getInt("count", 0)
        
        for (i in 0 until count) {
            val savedMessage = prefs.getString("message_$i", "")
            if (savedMessage == message) {
                prefs.edit().putBoolean("completed_$i", true).apply()
                break
            }
        }
        
        Toast.makeText(this, "✅ انجام شد", Toast.LENGTH_SHORT).show()
    }
    
    private fun snoozeReminder(minutes: Int) {
        stopAlarmSound()
        
        // تنظیم یادآوری جدید برای چند دقیقه بعد
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, minutes)
        
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        com.persianai.assistant.utils.SystemIntegrationHelper.setReminder(
            this, 
            message, 
            hour, 
            minute
        )
        
        Toast.makeText(this, "⏰ $minutes دقیقه بعد یادآوری می‌شود", Toast.LENGTH_SHORT).show()
    }
    
    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "بگویید: انجام شد، بعداً، یا هر چیز دیگر...")
        }
        
        try {
            startActivityForResult(intent, REQUEST_VOICE_RECOGNITION)
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در تشخیص صوت", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_VOICE_RECOGNITION && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)?.lowercase() ?: ""
            
            when {
                spokenText.contains("انجام") || spokenText.contains("خورد") || 
                spokenText.contains("شد") || spokenText.contains("تمام") -> {
                    markAsDone()
                    finish()
                }
                spokenText.contains("بعد") || spokenText.contains("یادآوری") -> {
                    snoozeReminder(5)
                    finish()
                }
                else -> {
                    Toast.makeText(this, "متوجه نشدم. لطفاً دوباره تلاش کنید.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun stopAlarmSound() {
        ringtone?.stop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
    }
}
