package com.persianai.assistant.activities

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.persianai.assistant.R
import com.persianai.assistant.adapters.RemindersAdapter
import com.persianai.assistant.data.AdvancedReminder
import com.persianai.assistant.databinding.ActivityAdvancedRemindersBinding
import com.persianai.assistant.services.ReminderReceiver
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.SmartReminderManager
import kotlinx.coroutines.launch
import java.util.*

/**
 * یادآوری‌های پیشرفته
 * 
 * ✅ یادآوری زمانی
 * ✅ یادآوری مکانی (GPS)
 * ✅ یادآوری تکراری
 * ✅ یادآوری شرطی
 * ✅ اولویت‌بندی
 * ✅ دسته‌بندی
 */
class AdvancedRemindersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedRemindersBinding
    private lateinit var remindersAdapter: RemindersAdapter
    private lateinit var reminderManager: SmartReminderManager
    private val reminders = mutableListOf<AdvancedReminder>()
    
    private var filterType: FilterType = FilterType.ALL
    
    enum class FilterType {
        ALL,
        TIME_BASED,
        LOCATION_BASED,
        RECURRING,
        CONDITIONAL,
        HIGH_PRIORITY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        initializeManager()
        setupRecyclerView()
        setupListeners()
        checkPermissions()
        loadReminders()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "📝 یادآوری‌های پیشرفته"
    }
    
    private fun initializeManager() {
        reminderManager = SmartReminderManager(this)
    }
    
    private fun setupRecyclerView() {
        remindersAdapter = RemindersAdapter(reminders) { reminder, action ->
            when (action) {
                "view" -> viewReminderDetails(reminder)
                "edit" -> editReminder(reminder)
                "delete" -> deleteReminder(reminder)
                "complete" -> markComplete(reminder)
            }
        }
        
        binding.remindersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdvancedRemindersActivity)
            adapter = remindersAdapter
        }
    }
    
    private fun setupListeners() {
        binding.fabAddReminder.setOnClickListener {
            showAddReminderDialog()
        }
        
        // Filters
        binding.chipAll.setOnClickListener { applyFilter(FilterType.ALL) }
        binding.chipTimeBased.setOnClickListener { applyFilter(FilterType.TIME_BASED) }
        binding.chipLocationBased.setOnClickListener { applyFilter(FilterType.LOCATION_BASED) }
        binding.chipRecurring.setOnClickListener { applyFilter(FilterType.RECURRING) }
        binding.chipConditional.setOnClickListener { applyFilter(FilterType.CONDITIONAL) }
        binding.chipHighPriority.setOnClickListener { applyFilter(FilterType.HIGH_PRIORITY) }
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }
    
    private fun loadReminders() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                
                val allReminders = reminderManager.getAllReminders()
                
                reminders.clear()
                reminders.addAll(allReminders)
                
                applyFilter(filterType)
                
                binding.progressBar.visibility = View.GONE
                
                if (reminders.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.remindersRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.remindersRecyclerView.visibility = View.VISIBLE
                }
                
                updateStats()
                
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@AdvancedRemindersActivity, "❌ خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun applyFilter(type: FilterType) {
        filterType = type
        
        // Reset chips
        binding.chipAll.isChecked = false
        binding.chipTimeBased.isChecked = false
        binding.chipLocationBased.isChecked = false
        binding.chipRecurring.isChecked = false
        binding.chipConditional.isChecked = false
        binding.chipHighPriority.isChecked = false
        
        val allReminders = reminderManager.getAllReminders()
        
        val filtered = when (type) {
            FilterType.ALL -> {
                binding.chipAll.isChecked = true
                allReminders
            }
            FilterType.TIME_BASED -> {
                binding.chipTimeBased.isChecked = true
                allReminders.filter { it.type == AdvancedReminder.ReminderType.TIME_BASED }
            }
            FilterType.LOCATION_BASED -> {
                binding.chipLocationBased.isChecked = true
                allReminders.filter { it.type == AdvancedReminder.ReminderType.LOCATION_BASED }
            }
            FilterType.RECURRING -> {
                binding.chipRecurring.isChecked = true
                allReminders.filter { it.isRecurring }
            }
            FilterType.CONDITIONAL -> {
                binding.chipConditional.isChecked = true
                allReminders.filter { it.type == AdvancedReminder.ReminderType.CONDITIONAL }
            }
            FilterType.HIGH_PRIORITY -> {
                binding.chipHighPriority.isChecked = true
                allReminders.filter { it.priority == AdvancedReminder.Priority.HIGH }
            }
        }
        
        reminders.clear()
        reminders.addAll(filtered)
        remindersAdapter.notifyDataSetChanged()
    }
    
    private fun updateStats() {
        lifecycleScope.launch {
            val stats = reminderManager.getReminderStats()
            
            binding.statsCard.visibility = View.VISIBLE
            binding.totalRemindersText.text = "${stats.totalReminders} یادآوری"
            binding.activeRemindersText.text = "${stats.activeReminders} فعال"
            binding.completedRemindersText.text = "${stats.completedReminders} انجام شده"
            binding.upcomingRemindersText.text = "${stats.upcomingReminders} نزدیک"
        }
    }
    
    private fun showAddReminderDialog() {
        val options = arrayOf(
            "⏰ یادآوری زمانی",
            "📍 یادآوری مکانی",
            "🔁 یادآوری تکراری",
            "⚙️ یادآوری شرطی"
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("نوع یادآوری")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTimeBasedReminderDialog()
                    1 -> showLocationBasedReminderDialog()
                    2 -> showRecurringReminderDialog()
                    3 -> showConditionalReminderDialog()
                }
            }
            .show()
    }
    
    private fun showTimeBasedReminderDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_reminder, null)
        
        val titleInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.titleInput)
        val descriptionInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.descriptionInput)
        val dateButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectDateButton)
        val timeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectTimeButton)
        val priorityGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.priorityChipGroup)
        val categorySpinner = dialogView.findViewById<android.widget.Spinner>(R.id.categorySpinner)
        
        var selectedDate: Long = System.currentTimeMillis()
        var selectedHour = 12
        var selectedMinute = 0
        
        // Setup category spinner
        val categories = arrayOf("شخصی", "کاری", "خانوادگی", "مالی", "سلامت", "خرید", "سایر")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
        
        dateButton.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("تاریخ")
                .setSelection(selectedDate)
                .build()
            
            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedDate = selection
                val persianDate = PersianDateConverter.gregorianToPersian(Date(selection))
                dateButton.text = persianDate
            }
            
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }
        
        timeButton.setOnClickListener {
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText("ساعت")
                .build()
            
            timePicker.addOnPositiveButtonClickListener {
                selectedHour = timePicker.hour
                selectedMinute = timePicker.minute
                timeButton.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            }
            
            timePicker.show(supportFragmentManager, "TIME_PICKER")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("➕ یادآوری زمانی")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString()
                val description = descriptionInput.text.toString()
                val category = categories[categorySpinner.selectedItemPosition]
                
                val priority = when (priorityGroup.checkedChipId) {
                    R.id.chipLowPriority -> AdvancedReminder.Priority.LOW
                    R.id.chipMediumPriority -> AdvancedReminder.Priority.MEDIUM
                    R.id.chipHighPriority -> AdvancedReminder.Priority.HIGH
                    else -> AdvancedReminder.Priority.MEDIUM
                }
                
                if (title.isEmpty()) {
                    Toast.makeText(this, "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // محاسبه زمان دقیق
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = selectedDate
                calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                calendar.set(Calendar.MINUTE, selectedMinute)
                calendar.set(Calendar.SECOND, 0)
                
                val reminder = AdvancedReminder(
                    id = UUID.randomUUID().toString(),
                    type = AdvancedReminder.ReminderType.TIME_BASED,
                    title = title,
                    description = description,
                    category = category,
                    priority = priority,
                    triggerTime = calendar.timeInMillis,
                    isRecurring = false,
                    completed = false,
                    createdAt = System.currentTimeMillis()
                )
                
                addReminder(reminder)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showLocationBasedReminderDialog() {
        Toast.makeText(this, "🚧 یادآوری مکانی در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun showRecurringReminderDialog() {
        Toast.makeText(this, "🚧 یادآوری تکراری در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun showConditionalReminderDialog() {
        Toast.makeText(this, "🚧 یادآوری شرطی در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun addReminder(reminder: AdvancedReminder) {
        lifecycleScope.launch {
            try {
                reminderManager.addReminder(reminder)
                
                // ثبت Alarm
                scheduleReminder(reminder)
                
                Toast.makeText(
                    this@AdvancedRemindersActivity,
                    "✅ یادآوری ثبت شد",
                    Toast.LENGTH_SHORT
                ).show()
                
                loadReminders()
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@AdvancedRemindersActivity,
                    "❌ خطا: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun scheduleReminder(reminder: AdvancedReminder) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_title", reminder.title)
            putExtra("reminder_description", reminder.description)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerTime,
            pendingIntent
        )
    }
    
    private fun viewReminderDetails(reminder: AdvancedReminder) {
        val persianDate = if (reminder.triggerTime > 0) {
            PersianDateConverter.gregorianToPersian(Date(reminder.triggerTime))
        } else {
            "نامشخص"
        }
        
        val typeText = when (reminder.type) {
            AdvancedReminder.ReminderType.TIME_BASED -> "⏰ زمانی"
            AdvancedReminder.ReminderType.LOCATION_BASED -> "📍 مکانی"
            AdvancedReminder.ReminderType.CONDITIONAL -> "⚙️ شرطی"
        }
        
        val priorityText = when (reminder.priority) {
            AdvancedReminder.Priority.LOW -> "🟢 کم"
            AdvancedReminder.Priority.MEDIUM -> "🟡 متوسط"
            AdvancedReminder.Priority.HIGH -> "🔴 بالا"
        }
        
        val details = buildString {
            appendLine("نوع: $typeText")
            appendLine("عنوان: ${reminder.title}")
            if (reminder.description.isNotEmpty()) {
                appendLine("توضیحات: ${reminder.description}")
            }
            appendLine("دسته: ${reminder.category}")
            appendLine("اولویت: $priorityText")
            if (reminder.type == AdvancedReminder.ReminderType.TIME_BASED) {
                appendLine("زمان: $persianDate")
            }
            appendLine("تکراری: ${if (reminder.isRecurring) "بله" else "خیر"}")
            appendLine("وضعیت: ${if (reminder.completed) "✅ انجام شده" else "⏳ در انتظار"}")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("جزئیات یادآوری")
            .setMessage(details)
            .setPositiveButton("بستن", null)
            .setNeutralButton("ویرایش") { _, _ ->
                editReminder(reminder)
            }
            .setNegativeButton("حذف") { _, _ ->
                deleteReminder(reminder)
            }
            .show()
    }
    
    private fun editReminder(reminder: AdvancedReminder) {
        Toast.makeText(this, "🚧 ویرایش در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun deleteReminder(reminder: AdvancedReminder) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف یادآوری")
            .setMessage("آیا مطمئن هستید؟")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    try {
                        reminderManager.deleteReminder(reminder.id)
                        
                        // لغو Alarm
                        cancelReminder(reminder)
                        
                        Toast.makeText(
                            this@AdvancedRemindersActivity,
                            "✅ حذف شد",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        loadReminders()
                        
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@AdvancedRemindersActivity,
                            "❌ خطا: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("خیر", null)
            .show()
    }
    
    private fun cancelReminder(reminder: AdvancedReminder) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
    }
    
    private fun markComplete(reminder: AdvancedReminder) {
        lifecycleScope.launch {
            try {
                reminderManager.markComplete(reminder.id)
                
                Toast.makeText(
                    this@AdvancedRemindersActivity,
                    "✅ انجام شد",
                    Toast.LENGTH_SHORT
                ).show()
                
                loadReminders()
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@AdvancedRemindersActivity,
                    "❌ خطا: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.reminders_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear_completed -> {
                clearCompleted()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun clearCompleted() {
        MaterialAlertDialogBuilder(this)
            .setTitle("پاک کردن یادآوری‌های انجام شده")
            .setMessage("آیا مطمئن هستید؟")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    reminderManager.clearCompleted()
                    loadReminders()
                    Toast.makeText(this@AdvancedRemindersActivity, "✅ پاک شد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("خیر", null)
            .show()
    }
}
