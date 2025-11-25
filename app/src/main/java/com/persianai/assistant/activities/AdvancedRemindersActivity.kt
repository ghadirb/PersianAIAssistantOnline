package com.persianai.assistant.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.adapters.RemindersAdapter
import com.persianai.assistant.databinding.ActivityAdvancedRemindersBinding
import com.persianai.assistant.models.Reminder
import com.persianai.assistant.storage.ReminderStorage

class AdvancedRemindersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedRemindersBinding
    private lateinit var reminderStorage: ReminderStorage
    private lateinit var remindersAdapter: RemindersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        reminderStorage = ReminderStorage(this)

        setupRecyclerView()
        loadReminders()

        binding.fabAddReminder.setOnClickListener {
            // Add a sample reminder for testing
            val newReminder = Reminder(
                message = "یادآوری تستی جدید",
                timestamp = System.currentTimeMillis() + 120000 // 2 minutes from now
            )
            reminderStorage.addReminder(newReminder)
            Toast.makeText(this, "یادآوری تستی اضافه شد", Toast.LENGTH_SHORT).show()
            loadReminders()
        }
    }

    private fun setupRecyclerView() {
        remindersAdapter = RemindersAdapter(emptyList()) { reminder, action ->
            when (action) {
                "complete" -> {
                    val updatedReminder = reminder.copy(isCompleted = !reminder.isCompleted)
                    reminderStorage.updateReminder(updatedReminder)
                    loadReminders()
                }
                "view" -> {
                    Toast.makeText(this, "نمایش جزئیات: ${reminder.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.remindersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdvancedRemindersActivity)
            adapter = remindersAdapter
        }
    }

    private fun loadReminders() {
        val reminders = reminderStorage.getAllReminders()
        remindersAdapter.updateData(reminders)
        updateStats(reminders)
    }

    private fun updateStats(reminders: List<Reminder>) {
        binding.totalRemindersText.text = reminders.size.toString()
        binding.activeRemindersText.text = reminders.count { !it.isCompleted }.toString()
        binding.completedRemindersText.text = reminders.count { it.isCompleted }.toString()
        binding.upcomingRemindersText.text = reminders.count { !it.isCompleted && it.timestamp > System.currentTimeMillis() }.toString()
    }
}
