package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityMainBinding
import com.persianai.assistant.utils.SmartReminderManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var reminderManager: SmartReminderManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reminderManager = SmartReminderManager(this)

        // Example of how to use the new manager
        binding.remindersCard.setOnClickListener {
            startActivity(Intent(this, AdvancedRemindersActivity::class.java))
        }

        binding.financeCard.setOnClickListener {
            startActivity(Intent(this, FinanceAdvancedActivity::class.java))
        }

        // Load initial data or update UI
        updateDashboard()
    }

    private fun updateDashboard() {
        val activeReminders = reminderManager.getActiveReminders()
        // Update your UI elements here, for example:
        // binding.activeRemindersCount.text = activeReminders.size.toString()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }
}
