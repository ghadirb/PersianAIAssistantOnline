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
import android.widget.EditText
import android.widget.Toast
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityRemindersBinding
import com.persianai.assistant.ai.ContextualAIAssistant
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * صفحه مدیریت یادآوری‌ها
 */
class RemindersActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRemindersBinding
    private lateinit var adapter: RemindersAdapter
    private lateinit var aiAssistant: ContextualAIAssistant
    private val reminders = mutableListOf<Reminder>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "یادآوری‌ها"
        
        aiAssistant = ContextualAIAssistant(this)
        
        setupRecyclerView()
        loadReminders()
        setupAIChatButton()
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
    
    private fun showAddReminderDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_reminder, null)
        MaterialAlertDialogBuilder(this)
            .setTitle("🔔 یادآوری جدید")
            .setView(view)
            .setPositiveButton("ثبت") { _, _ ->
                // ذخیره یادآوری
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun setupAIChatButton() {
        binding.aiChatButton.setOnClickListener {
            showAIChat()
        }
        
        binding.fab?.setOnClickListener {
            showAddReminderDialog()
        }
    }
    
    private fun showAIChat() {
        val input = EditText(this)
        input.hint = "دستور خود را بنویسید (مثل: یادآوری ساعت 9 صبح جلسه)"
        input.setPadding(32, 32, 32, 32)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("🔔 دستیار یادآوری هوشمند")
            .setView(input)
            .setPositiveButton("اجرا") { _, _ ->
                val userMessage = input.text.toString()
                if (userMessage.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            val response = aiAssistant.processReminderCommand(userMessage)
                            
                            runOnUiThread {
                                MaterialAlertDialogBuilder(this@RemindersActivity)
                                    .setTitle(if (response.success) "✅ انجام شد" else "⚠️ خطا")
                                    .setMessage(response.message)
                                    .setPositiveButton("باشه") { _, _ ->
                                        if (response.success && response.action == "add_reminder") {
                                            loadReminders()
                                        }
                                    }
                                    .show()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this@RemindersActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun processAICommand(command: String) {
        // تحلیل دستور برای افزودن یادآوری
        Toast.makeText(this, "🔄 در حال پردازش...", Toast.LENGTH_SHORT).show()
        
        // مثال: "یادآوری فردا ساعت 9 صبح تنظیم کن"
        if (command.contains("یادآوری") || command.contains("یاداور")) {
            // استخراج زمان و متن
            val timePattern = """(\d{1,2})\s*(صبح|ظهر|عصر|شب)""".toRegex()
            val match = timePattern.find(command)
            
            if (match != null) {
                val hour = match.groupValues[1].toIntOrNull() ?: 9
                val period = match.groupValues[2]
                val message = command.replace(timePattern, "").replace("یادآوری", "").replace("تنظیم کن", "").trim()
                
                val hourAdjusted = when (period) {
                    "ظهر" -> hour + 12
                    "عصر" -> hour + 12
                    "شب" -> hour + 12
                    else -> hour
                }
                
                val time = "ساعت $hourAdjusted:00"
                val reminderMessage = if (message.isNotEmpty()) message else "یادآوری"
                
                // افزودن یادآوری جدید
                val newReminder = Reminder(time, reminderMessage, false, System.currentTimeMillis())
                reminders.add(newReminder)
                reminders.sortBy { it.timestamp }
                saveReminders()
                adapter.notifyDataSetChanged()
                updateEmptyState()
                
                Toast.makeText(this, "✅ یادآوری اضافه شد: $time", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "⚠️ زمان را مشخص کنید (مثل ساعت 9 صبح)", Toast.LENGTH_SHORT).show()
            }
        } else {
            // پاسخ عمومی AI
            Toast.makeText(this, "💬 من دستیار یادآوری هستم. برای افزودن یادآوری بگویید: یادآوری ساعت X صبح", Toast.LENGTH_LONG).show()
        }
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
    val timestamp: Long = System.currentTimeMillis(),
    val repeatType: String = "once", // "once", "daily", "weekly"
    val repeatDays: List<Int> = emptyList() // 0=یکشنبه, 6=شنبه
)
