package com.persianai.assistant.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityRemindersBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * صفحه مدیریت یادآوری‌ها
 */
class RemindersActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRemindersBinding
    private lateinit var adapter: RemindersAdapter
    private val reminders = mutableListOf<Reminder>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "یادآوری‌ها"
        
        setupRecyclerView()
        loadReminders()
    }
    
    private fun setupRecyclerView() {
        adapter = RemindersAdapter(reminders) { reminder, position ->
            // کلیک روی چک‌باکس
            reminder.completed = !reminder.completed
            adapter.notifyItemChanged(position)
            saveReminders()
        }
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun loadReminders() {
        val prefs = getSharedPreferences("reminders", MODE_PRIVATE)
        val count = prefs.getInt("count", 0)
        
        reminders.clear()
        for (i in 0 until count) {
            val time = prefs.getString("time_$i", "") ?: ""
            val message = prefs.getString("message_$i", "") ?: ""
            val completed = prefs.getBoolean("completed_$i", false)
            val timestamp = prefs.getLong("timestamp_$i", 0)
            
            if (time.isNotEmpty() && message.isNotEmpty()) {
                reminders.add(Reminder(time, message, completed, timestamp))
            }
        }
        
        // مرتب‌سازی بر اساس زمان
        reminders.sortBy { it.timestamp }
        
        adapter.notifyDataSetChanged()
        
        updateEmptyState()
    }
    
    private fun saveReminders() {
        val prefs = getSharedPreferences("reminders", MODE_PRIVATE)
        val editor = prefs.edit()
        
        editor.putInt("count", reminders.size)
        
        reminders.forEachIndexed { index, reminder ->
            editor.putString("time_$index", reminder.time)
            editor.putString("message_$index", reminder.message)
            editor.putBoolean("completed_$index", reminder.completed)
            editor.putLong("timestamp_$index", reminder.timestamp)
        }
        
        editor.apply()
    }
    
    private fun updateEmptyState() {
        if (reminders.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    companion object {
        fun addReminder(activity: AppCompatActivity, time: String, message: String) {
            val prefs = activity.getSharedPreferences("reminders", MODE_PRIVATE)
            val editor = prefs.edit()
            val count = prefs.getInt("count", 0)
            
            val calendar = Calendar.getInstance()
            val parts = time.split(":")
            calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            calendar.set(Calendar.MINUTE, parts[1].toInt())
            
            editor.putString("time_$count", time)
            editor.putString("message_$count", message)
            editor.putBoolean("completed_$count", false)
            editor.putLong("timestamp_$count", calendar.timeInMillis)
            editor.putInt("count", count + 1)
            editor.apply()
        }
    }
}

/**
 * آداپتور برای نمایش یادآوری‌ها
 */
class RemindersAdapter(
    private val reminders: List<Reminder>,
    private val onCheckedChange: (Reminder, Int) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText: TextView = view.findViewById(R.id.timeText)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reminder = reminders[position]
        
        holder.timeText.text = "⏰ ${reminder.time}"
        holder.messageText.text = reminder.message
        holder.checkBox.isChecked = reminder.completed
        
        // استایل برای تکمیل شده
        if (reminder.completed) {
            holder.messageText.alpha = 0.5f
            holder.timeText.alpha = 0.5f
        } else {
            holder.messageText.alpha = 1.0f
            holder.timeText.alpha = 1.0f
        }
        
        holder.checkBox.setOnClickListener {
            onCheckedChange(reminder, position)
        }
    }
    
    override fun getItemCount() = reminders.size
}

/**
 * مدل یادآوری
 */
data class Reminder(
    val time: String,
    val message: String,
    var completed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
