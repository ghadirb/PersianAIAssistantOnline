package com.persianai.assistant.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.persianai.assistant.activities.AlarmActivity

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("REMINDER_ID")
        val message = intent.getStringExtra("REMINDER_MESSAGE") ?: "شما یک یادآوری دارید"

        // Start the full-screen alarm activity
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("REMINDER_ID", reminderId)
            putExtra("REMINDER_MESSAGE", message)
        }
        context.startActivity(alarmIntent)
    }
}
