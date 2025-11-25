package com.persianai.assistant.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.adapters.RemindersAdapter
import com.persianai.assistant.databinding.ActivityAdvancedRemindersBinding
import com.persianai.assistant.models.Reminder
import com.persianai.assistant.utils.SmartReminderManager

class AdvancedRemindersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedRemindersBinding
    private lateinit var reminderManager: SmartReminderManager
    private lateinit var remindersAdapter: RemindersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reminderManager = SmartReminderManager(this)

        setupRecyclerView()
        loadReminders()

        binding.fab.setOnClickListener {
            // For simplicity, we'll just add a sample reminder.
            // The full dialog implementation can be added back later.
            val newReminder = reminderManager.createSimpleReminder(
                title = "یادآوری تستی",
                triggerTime = System.currentTimeMillis() + 60000 // 1 minute from now
            )
            Toast.makeText(this, "یادآوری تستی اضافه شد", Toast.LENGTH_SHORT).show()
            loadReminders()
        }
    }

    private fun setupRecyclerView() {
        remindersAdapter = RemindersAdapter(emptyList()) { reminder, action ->
            // Handle reminder actions (view, edit, etc.)
        }
        binding.remindersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdvancedRemindersActivity)
            adapter = remindersAdapter
        }
    }

    private fun loadReminders() {
        val reminders = reminderManager.getAllReminders()
        remindersAdapter.updateData(reminders)
    }
}
