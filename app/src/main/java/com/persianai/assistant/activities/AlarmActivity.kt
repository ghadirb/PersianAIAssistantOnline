package com.persianai.assistant.activities

import android.media.MediaPlayer
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityAlarmBinding
import com.persianai.assistant.utils.SmartReminderManager

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var reminderManager: SmartReminderManager
    private var reminderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reminderManager = SmartReminderManager(this)
        reminderId = intent.getStringExtra("REMINDER_ID")
        val message = intent.getStringExtra("REMINDER_MESSAGE") ?: "شما یک یادآوری دارید"

        binding.messageText.text = message

        binding.doneButton.setOnClickListener {
            if (reminderId != null) {
                reminderManager.completeReminder(reminderId!!)
                Toast.makeText(this, "یادآوری انجام شد", Toast.LENGTH_SHORT).show()
            }
            stopAlarmSound()
            finish()
        }

        binding.snoozeButton.setOnClickListener {
            if (reminderId != null) {
                reminderManager.snoozeReminder(reminderId!!, 10) // Snooze for 10 minutes
                Toast.makeText(this, "یادآوری به تعویق افتاد", Toast.LENGTH_SHORT).show()
            }
            stopAlarmSound()
            finish()
        }

        playAlarmSound()
    }

    private fun playAlarmSound() {
        try {
            val alarmSound = Settings.System.DEFAULT_ALARM_ALERT_URI
            mediaPlayer = MediaPlayer.create(this, alarmSound)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
    }
}
