package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.adapters.RemindersAdapter
import com.persianai.assistant.databinding.ActivityAdvancedRemindersBinding
import com.persianai.assistant.utils.SmartReminderManager
import kotlinx.coroutines.launch

class AdvancedRemindersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedRemindersBinding
    private lateinit var reminderManager: SmartReminderManager
    private lateinit var adapter: RemindersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reminderManager = SmartReminderManager(this)

        setupRecyclerView()
        loadReminders()
        updateStats()

        binding.fabAddReminder.setOnClickListener {
            // Open a dedicated activity to create/edit reminders
            // startActivity(Intent(this, EditReminderActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter {
            // Handle reminder click for editing
        }
        binding.remindersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.remindersRecyclerView.adapter = adapter
    }

    private fun loadReminders() {
        lifecycleScope.launch {
            val reminders = reminderManager.getAllReminders().map { it.toSimpleReminder() }
            adapter.submitList(reminders)
        }
    }

    private fun updateStats() {
        lifecycleScope.launch {
            val allReminders = reminderManager.getAllReminders()
            val active = allReminders.count { !it.isCompleted }
            val completed = allReminders.count { it.isCompleted }
            val upcoming = reminderManager.getUpcomingReminders(1).size

            binding.statTotal.text = allReminders.size.toString()
            binding.statActive.text = active.toString()
            binding.statCompleted.text = completed.toString()
            binding.statUpcoming.text = upcoming.toString()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh list and stats when returning to the activity
        loadReminders()
        updateStats()
    }
}
